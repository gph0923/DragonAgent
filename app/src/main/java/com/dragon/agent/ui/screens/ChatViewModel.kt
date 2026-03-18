package com.dragon.agent.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.dragon.agent.core.AgentEngine
import com.dragon.agent.data.local.MemoryManager
import com.dragon.agent.data.local.SettingsManager
import com.dragon.agent.llm.ChatMessage
import com.dragon.agent.llm.MessageRole
import com.dragon.agent.router.AgentRouter
import com.dragon.agent.router.Intent
import com.dragon.agent.router.PromptManager
import com.dragon.agent.router.ToolSelector
import com.dragon.agent.skills.SkillManager
import com.dragon.agent.tools.ToolManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * UI 状态
 */
data class ChatUiState(
    val messages: List<ChatUiMessage> = emptyList(),
    val inputText: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val systemPrompt: String = DEFAULT_SYSTEM_PROMPT,
    val hasApiKey: Boolean = false,
    val skillsLoaded: Boolean = false,
    val toolsCount: Int = 0,
    val currentIntent: String = "",      // 当前意图
    val routeDecision: String = ""       // 路由决策信息
)

data class ChatUiMessage(
    val id: String,
    val role: MessageRole,
    val content: String,
    val isLoading: Boolean = false
)

/**
 * 默认 System Prompt
 */
private const val DEFAULT_SYSTEM_PROMPT = """你是一个有帮助的 AI 助手。请用简洁清晰的方式回答用户的问题。"""

/**
 * Chat ViewModel - 集成路由、意图识别、工具选择
 */
@HiltViewModel
class ChatViewModel @Inject constructor(
    private val agentEngine: AgentEngine,
    private val settingsManager: SettingsManager,
    private val memoryManager: MemoryManager,
    private val toolManager: ToolManager,
    private val skillManager: SkillManager,
    private val agentRouter: AgentRouter,
    private val promptManager: PromptManager,
    private val toolSelector: ToolSelector
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    // 当前会话 ID
    private var currentConversationId: String = "default"

    init {
        // 加载设置
        viewModelScope.launch {
            settingsManager.settings.collect { settings ->
                _uiState.update { state ->
                    state.copy(
                        systemPrompt = settings.systemPrompt.ifBlank { DEFAULT_SYSTEM_PROMPT },
                        hasApiKey = settings.apiKey.isNotBlank()
                    )
                }
            }
        }
        
        // 初始化技能和工具
        initializeSkillsAndTools()
    }

    /**
     * 初始化技能系统
     */
    private fun initializeSkillsAndTools() {
        viewModelScope.launch {
            try {
                // 初始化技能
                skillManager.initialize()
                
                // 注册技能工具到 ToolManager
                val skillTools = skillManager.getSkillTools()
                skillTools.forEach { skillTool ->
                    toolManager.registerTool(
                        name = skillTool.name,
                        description = skillTool.description,
                        parameters = emptyMap()
                    )
                }
                
                // 更新 UI 状态
                val totalTools = toolManager.getTools().size
                _uiState.update { state ->
                    state.copy(
                        skillsLoaded = true,
                        toolsCount = totalTools
                    )
                }
                
                println("[ChatViewModel] Skills: ${skillManager.skills.value.size}, Tools: $totalTools")
            } catch (e: Exception) {
                println("[ChatViewModel] Init error: ${e.message}")
            }
        }
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    /**
     * 发送消息 - 带路由和意图识别
     */
    fun sendMessage() {
        val input = _uiState.value.inputText.trim()
        if (input.isBlank() || _uiState.value.isLoading) return

        // 检查 API Key
        if (!_uiState.value.hasApiKey) {
            _uiState.update { state ->
                state.copy(error = "请先在设置中配置 API Key")
            }
            return
        }

        // 添加用户消息
        val userMessage = ChatUiMessage(
            id = System.currentTimeMillis().toString(),
            role = MessageRole.USER,
            content = input
        )

        // 添加助手消息（等待中）
        val assistantMessage = ChatUiMessage(
            id = (System.currentTimeMillis() + 1).toString(),
            role = MessageRole.ASSISTANT,
            content = "",
            isLoading = true
        )

        _uiState.update { state ->
            state.copy(
                messages = state.messages + userMessage + assistantMessage,
                inputText = "",
                isLoading = true,
                error = null,
                currentIntent = "",  // 清空之前意图
                routeDecision = ""
            )
        }

        // 执行对话（带路由）
        viewModelScope.launch {
            executeWithRouting(input, assistantMessage.id)
        }
    }

    /**
     * 执行带路由的对话
     */
    private suspend fun executeWithRouting(userInput: String, assistantMessageId: String) {
        try {
            // 1. 意图识别和路由决策
            val routeDecision = agentRouter.route(userInput)
            
            // 更新 UI 显示当前意图
            _uiState.update { state ->
                state.copy(
                    currentIntent = routeDecision.intent.name,
                    routeDecision = "触发技能: ${routeDecision.triggeredSkills.joinToString(", ").ifEmpty { "无" }} | 工具: ${routeDecision.selectedTools.joinToString(", ").ifEmpty { "无" }}"
                )
            }
            
            // 2. 获取对话历史
            val historyMessages = memoryManager.getRecentMessages(currentConversationId, 20)
            val history = historyMessages.map { msg ->
                ChatMessage(
                    role = msg.role,
                    content = msg.content
                )
            }

            // 3. 保存用户消息到记忆
            memoryManager.saveMessage(
                conversationId = currentConversationId,
                messageRole = MessageRole.USER,
                content = userInput
            )

            // 4. 获取可用工具（基于路由决策）
            val selectedTools = if (routeDecision.selectedTools.isNotEmpty()) {
                toolManager.getToolDefinitions().filter { it.name in routeDecision.selectedTools }
            } else {
                toolManager.getToolDefinitions()
            }

            // 5. 构建动态系统提示词
            val systemPrompt = promptManager.buildPromptForIntent(routeDecision, history.map { it.content })

            // 6. 执行对话
            val result = if (selectedTools.isNotEmpty()) {
                agentEngine.executeWithTools(
                    systemPrompt = systemPrompt,
                    userInput = userInput,
                    history = history,
                    tools = selectedTools,
                    onToolCall = { toolCall ->
                        handleToolCall(toolCall)
                    }
                )
            } else {
                agentEngine.execute(
                    systemPrompt = systemPrompt,
                    userInput = userInput,
                    history = history
                )
            }

            result.fold(
                onSuccess = { response ->
                    // 保存助手回复到记忆
                    memoryManager.saveMessage(
                        conversationId = currentConversationId,
                        messageRole = MessageRole.ASSISTANT,
                        content = response.content
                    )

                    // 更新 UI
                    _uiState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) {
                                msg.copy(content = response.content, isLoading = false)
                            } else {
                                msg
                            }
                        }
                        state.copy(messages = updatedMessages, isLoading = false)
                    }
                },
                onFailure = { error ->
                    _uiState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == assistantMessageId) {
                                msg.copy(content = "抱歉，出现错误: ${error.message}", isLoading = false)
                            } else {
                                msg
                            }
                        }
                        state.copy(messages = updatedMessages, isLoading = false, error = error.message)
                    }
                }
            )
        } catch (e: Exception) {
            _uiState.update { state ->
                val updatedMessages = state.messages.map { msg ->
                    if (msg.id == assistantMessageId) {
                        msg.copy(content = "执行出错: ${e.message}", isLoading = false)
                    } else {
                        msg
                    }
                }
                state.copy(messages = updatedMessages, isLoading = false, error = e.message)
            }
        }
    }

    /**
     * 处理工具调用
     */
    private suspend fun handleToolCall(toolCall: com.dragon.agent.llm.ToolCall): Any? {
        return try {
            val arguments: Map<String, Any> = toolCall.arguments ?: emptyMap()
            val result = toolManager.executeTool(toolCall.name, arguments)
            
            if (result.success) {
                result.content
            } else {
                "工具执行失败: ${result.error}"
            }
        } catch (e: Exception) {
            "工具调用错误: ${e.message}"
        }
    }

    /**
     * 清除对话历史
     */
    fun clearHistory() {
        viewModelScope.launch {
            memoryManager.clearHistory(currentConversationId)
            _uiState.update { it.copy(messages = emptyList()) }
        }
    }

    /**
     * 更新 System Prompt
     */
    fun updateSystemPrompt(prompt: String) {
        _uiState.update { it.copy(systemPrompt = prompt) }
    }

    /**
     * 清除错误
     */
    fun clearError() {
        _uiState.update { it.copy(error = null) }
    }
}
