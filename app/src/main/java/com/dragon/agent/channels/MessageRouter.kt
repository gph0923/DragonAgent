package com.dragon.agent.channels

import com.dragon.agent.core.AgentEngine
import com.dragon.agent.data.local.MemoryManager
import com.dragon.agent.llm.MessageRole
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 消息路由器
 * 处理消息路由、对话管理、AI 回复
 */
@Singleton
class MessageRouter @Inject constructor(
    private val agentEngine: AgentEngine,
    private val memoryManager: MemoryManager,
    private val channelManager: ChannelManager
) {
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    
    // 会话管理: chatId -> AgentSession
    private val sessions = mutableMapOf<String, AgentSession>()
    
    // 消息处理协程
    private var messageProcessor: Job? = null
    
    /**
     * 启动消息路由
     */
    fun start() {
        // 监听所有渠道的消息
        channelManager.channels.value.values.forEach { channel ->
            scope.launch {
                channel.messages().collect { message ->
                    handleMessage(message)
                }
            }
        }
    }
    
    /**
     * 停止消息路由
     */
    fun stop() {
        messageProcessor?.cancel()
        sessions.clear()
    }
    
    /**
     * 处理接收到的消息
     */
    private suspend fun handleMessage(message: ChannelMessage) {
        // 跳过机器人自己发送的消息
        if (isBotMessage(message)) return
        
        // 获取或创建会话
        val session = getOrCreateSession(message.chatId)
        
        // 检查是否需要 @at 机器人 (飞书/钉钉场景)
        if (!shouldProcessMessage(message, session)) return
        
        // 获取用户信息
        val userInfo = getUserInfo(message.sender)
        
        // 格式化消息
        val formattedMessage = formatIncomingMessage(message, userInfo)
        
        // 保存到记忆
        memoryManager.saveMessage(
            conversationId = message.chatId,
            messageRole = MessageRole.USER,
            content = formattedMessage
        )
        
        // 调用 AI 处理
        val response = withContext(Dispatchers.Default) {
            agentEngine.execute(
                systemPrompt = "",
                userInput = formattedMessage
            )
        }
        
        // 发送回复
        val reply = formatOutgoingMessage(response.getOrNull()?.content ?: "处理失败")
        sendReply(message, reply)
        
        // 保存 AI 回复到记忆
        memoryManager.saveMessage(
            conversationId = message.chatId,
            messageRole = MessageRole.ASSISTANT,
            content = reply
        )
    }
    
    /**
     * 判断是否处理消息
     */
    private fun shouldProcessMessage(message: ChannelMessage, session: AgentSession): Boolean {
        return when (message.channel) {
            "feishu" -> {
                // 飞书群聊需要 @at 机器人
                val content = message.content
                if (message.chatType == ChatType.GROUP) {
                    content.contains("@机器人") || content.contains(session.botName)
                } else {
                    true // 单聊直接处理
                }
            }
            else -> true
        }
    }
    
    /**
     * 判断是否机器人消息
     */
    private fun isBotMessage(message: ChannelMessage): Boolean {
        // 可以通过 sender ID 判断是否是机器人
        return message.sender.startsWith("bot_") || message.sender == "system"
    }
    
    /**
     * 获取或创建会话
     */
    private fun getOrCreateSession(chatId: String): AgentSession {
        return sessions.getOrPut(chatId) {
            AgentSession(
                chatId = chatId,
                createdAt = System.currentTimeMillis(),
                messageCount = 0
            )
        }
    }
    
    /**
     * 获取用户信息
     */
    private suspend fun getUserInfo(senderId: String): ChannelUser? {
        val channel = channelManager.activeChannel.value ?: return null
        return channel.getUserInfo(senderId).getOrNull()
    }
    
    /**
     * 格式化输入消息
     */
    private fun formatIncomingMessage(message: ChannelMessage, user: ChannelUser?): String {
        return buildString {
            append("[${user?.name ?: message.sender}] ")
            append(formatMessageContent(message.content, message.messageType))
        }
    }
    
    /**
     * 格式化输出消息
     */
    private fun formatOutgoingMessage(response: String): String {
        return buildString {
            append("🤖 ")
            append(response)
        }
    }
    
    /**
     * 根据消息类型格式化内容
     */
    private fun formatMessageContent(content: String, type: MessageType): String {
        return when (type) {
            MessageType.TEXT -> content
            MessageType.IMAGE -> "[图片消息]"
            MessageType.FILE -> "[文件消息]"
            MessageType.VOICE -> "[语音消息]"
            MessageType.VIDEO -> "[视频消息]"
            MessageType.CARD -> "[卡片消息]"
            MessageType.UNKNOWN -> content
        }
    }
    
    /**
     * 发送回复
     */
    private suspend fun sendReply(original: ChannelMessage, reply: String) {
        val channel = channelManager.activeChannel.value ?: return
        
        val replyMessage = ChannelMessage(
            id = "",
            channel = channel.name,
            sender = "bot",
            content = reply,
            chatId = original.chatId,
            chatType = original.chatType,
            messageType = MessageType.TEXT
        )
        
        channel.sendMessage(replyMessage)
    }
    
    /**
     * 获取会话统计
     */
    fun getSessionStats(): Map<String, SessionStats> {
        return sessions.mapValues { (_, session) ->
            SessionStats(
                messageCount = session.messageCount,
                lastActivity = session.lastActivity
            )
        }
    }
    
    /**
     * 清除会话
     */
    fun clearSession(chatId: String) {
        sessions.remove(chatId)
    }
}

/**
 * Agent 会话
 */
data class AgentSession(
    val chatId: String,
    val createdAt: Long,
    val messageCount: Int = 0,
    val lastActivity: Long = System.currentTimeMillis(),
    val botName: String = "机器人"
)

/**
 * 会话统计
 */
data class SessionStats(
    val messageCount: Int,
    val lastActivity: Long
)
