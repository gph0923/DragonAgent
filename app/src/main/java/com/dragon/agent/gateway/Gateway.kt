package com.dragon.agent.gateway

import com.dragon.agent.channels.Channel
import com.dragon.agent.channels.ChannelManager
import com.dragon.agent.channels.ChannelMessage
import com.dragon.agent.core.AgentEngine
import com.dragon.agent.data.local.MemoryManager
import com.dragon.agent.llm.ChatMessage
import com.dragon.agent.llm.MessageRole
import com.dragon.agent.router.AgentRouter
import com.dragon.agent.router.PromptManager
import com.dragon.agent.router.ToolSelector
import com.dragon.agent.skills.SkillManager
import com.dragon.agent.tools.ToolManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Gateway 配置
 */
@Serializable
data class GatewayConfig(
    val enabled: Boolean = true,
    val port: Int = 8080,
    val authToken: String = "",
    val allowExternalAccess: Boolean = false
)

/**
 * Gateway 状态
 */
enum class GatewayState {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

/**
 * Gateway 事件
 */
sealed class GatewayEvent {
    data class Message(val message: ChannelMessage) : GatewayEvent()
    data class Error(val error: String) : GatewayEvent()
    data class Health(val status: String) : GatewayEvent()
}

/**
 * Gateway - 消息路由核心
 * 类似于 OpenClaw Gateway 的简化版
 * 负责：消息接收 → 意图识别 → Agent 执行 → 消息发送
 */
@Singleton
class Gateway @Inject constructor(
    private val channelManager: ChannelManager,
    private val agentEngine: AgentEngine,
    private val memoryManager: MemoryManager,
    private val toolManager: ToolManager,
    private val skillManager: SkillManager,
    private val agentRouter: AgentRouter,
    private val promptManager: PromptManager,
    private val toolSelector: ToolSelector
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    private val _state = MutableStateFlow(GatewayState.STOPPED)
    val state: StateFlow<GatewayState> = _state
    
    private val _events = MutableSharedFlow<GatewayEvent>()
    val events = _events.asSharedFlow()
    
    private var gatewayJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    private val messageHandlers = mutableMapOf<String, suspend (ChannelMessage) -> Unit>()

    /**
     * 启动 Gateway
     */
    fun start(config: GatewayConfig) {
        if (_state.value == GatewayState.RUNNING) return
        
        _state.value = GatewayState.STARTING
        gatewayJob = scope.launch {
            try {
                // 1. 初始化渠道
                channelManager.initializeAll()
                
                // 2. 初始化技能
                skillManager.initialize()
                
                // 3. 注册技能工具
                val skillTools = skillManager.getSkillTools()
                skillTools.forEach { skillTool ->
                    toolManager.registerTool(
                        name = skillTool.name,
                        description = skillTool.description,
                        parameters = emptyMap()
                    )
                }
                
                // 4. 启动消息监听
                startMessageListening()
                
                // 5. 注册默认消息处理器
                registerDefaultHandlers()
                
                _state.value = GatewayState.RUNNING
                _events.emit(GatewayEvent.Health("Gateway started"))
                
                println("[Gateway] Started successfully")
            } catch (e: Exception) {
                _state.value = GatewayState.ERROR
                _events.emit(GatewayEvent.Error("Failed to start: ${e.message}"))
            }
        }
    }

    /**
     * 停止 Gateway
     */
    fun stop() {
        gatewayJob?.cancel()
        scope.launch {
            channelManager.shutdownAll()
            _state.value = GatewayState.STOPPED
            _events.emit(GatewayEvent.Health("Gateway stopped"))
        }
    }

    /**
     * 启动消息监听
     */
    private fun startMessageListening() {
        scope.launch {
            channelManager.channels.value?.values?.forEach { channel ->
                launch {
                    channel.messages().collect { message ->
                        handleIncomingMessage(message)
                    }
                }
            }
        }
    }

    /**
     * 处理接收到的消息
     */
    private suspend fun handleIncomingMessage(message: ChannelMessage) {
        println("[Gateway] Received: ${message.content}")
        
        // 1. 触发技能匹配
        val routeDecision = agentRouter.route(message.content)
        
        // 2. 获取对话历史
        val history = memoryManager.getRecentMessages(message.chatId, 20).map {
            ChatMessage(
                role = it.role,
                content = it.content
            )
        }
        
        // 3. 获取可用工具
        val tools = toolManager.getToolDefinitions()
        
        // 4. 构建提示词
        val systemPrompt = promptManager.buildPromptForIntent(routeDecision, history.map { it.content })
        
        // 5. 执行对话
        val result = if (tools.isNotEmpty()) {
            agentEngine.executeWithTools(
                systemPrompt = systemPrompt,
                userInput = message.content,
                history = history,
                tools = tools,
                onToolCall = { toolCall ->
                    toolManager.executeTool(toolCall.name, toolCall.arguments ?: emptyMap()).content
                }
            )
        } else {
            agentEngine.execute(
                systemPrompt = systemPrompt,
                userInput = message.content,
                history = history
            )
        }
        
        // 6. 发送回复
        result.onSuccess { response ->
            val reply = ChannelMessage(
                id = System.currentTimeMillis().toString(),
                channel = message.channel,
                sender = "bot",
                content = response.content,
                chatId = message.chatId,
                chatType = message.chatType,
                messageType = message.messageType
            )
            
            channelManager.activeChannel.value?.sendMessage(reply)
            
            // 保存到记忆
            memoryManager.saveMessage(conversationId = message.chatId, messageRole = MessageRole.USER, content = message.content)
            memoryManager.saveMessage(conversationId = message.chatId, messageRole = MessageRole.ASSISTANT, content = response.content)
            
            _events.emit(GatewayEvent.Message(reply))
        }.onFailure { error ->
            _events.emit(GatewayEvent.Error(error.message ?: "Unknown error"))
        }
    }

    /**
     * 注册消息处理器
     */
    fun registerMessageHandler(channel: String, handler: suspend (ChannelMessage) -> Unit) {
        messageHandlers[channel] = handler
    }

    /**
     * 注册默认处理器
     */
    private fun registerDefaultHandlers() {
        channelManager.channels.value?.keys?.forEach { channel ->
            registerMessageHandler(channel) { message ->
                handleIncomingMessage(message)
            }
        }
    }

    /**
     * 发送消息到指定渠道
     */
    suspend fun sendMessage(channel: String, chatId: String, content: String): Result<Unit> {
        val ch = channelManager.getChannel(channel)
            ?: return Result.failure(IllegalArgumentException("Channel not found: $channel"))
        
        return ch.sendMessage(ChannelMessage(
            id = System.currentTimeMillis().toString(),
            channel = channel,
            sender = "bot",
            content = content,
            chatId = chatId
        ))
    }

    /**
     * 获取 Gateway 状态信息
     */
    fun getStatus(): GatewayStatus {
        return GatewayStatus(
            state = _state.value.name,
            channels = channelManager.getAvailableChannels(),
            skillsLoaded = skillManager.skills.value.size,
            toolsCount = toolManager.getTools().size,
            activeChannel = channelManager.activeChannel.value?.name ?: "none"
        )
    }
}

@Serializable
data class GatewayStatus(
    val state: String,
    val channels: List<String>,
    val skillsLoaded: Int,
    val toolsCount: Int,
    val activeChannel: String
)
