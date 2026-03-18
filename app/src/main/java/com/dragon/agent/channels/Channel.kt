package com.dragon.agent.channels

import kotlinx.coroutines.flow.Flow

/**
 * 消息渠道接口
 * 定义消息接收、发送等通用操作
 */
interface Channel {
    /** 渠道名称 */
    val name: String
    
    /** 渠道是否可用 */
    suspend fun isAvailable(): Boolean
    
    /**
     * 接收消息流
     * 返回 Flow<ChannelMessage> 用于持续接收消息
     */
    fun messages(): Flow<ChannelMessage>
    
    /**
     * 发送消息
     */
    suspend fun sendMessage(message: ChannelMessage): Result<Unit>
    
    /**
     * 获取用户信息
     */
    suspend fun getUserInfo(userId: String): Result<ChannelUser>
    
    /**
     * 初始化渠道（如配置 Webhook、连接等）
     */
    suspend fun initialize(): Result<Unit>
    
    /**
     * 关闭渠道
     */
    suspend fun shutdown(): Result<Unit>
}

/**
 * 渠道消息数据类
 */
data class ChannelMessage(
    val id: String,
    val channel: String,
    val sender: String,          // 发送者 ID
    val senderName: String = "",  // 发送者名称
    val content: String,          // 消息内容
    val rawContent: Any? = null, // 原始消息内容
    val timestamp: Long = System.currentTimeMillis(),
    val chatId: String = "",     // 会话 ID
    val chatType: ChatType = ChatType.UNKNOWN,
    val messageType: MessageType = MessageType.TEXT
)

/**
 * 渠道用户
 */
data class ChannelUser(
    val id: String,
    val name: String,
    val avatar: String = "",
    val isBot: Boolean = false
)

/**
 * 会话类型
 */
enum class ChatType {
    DIRECT,      // 单聊
    GROUP,       // 群聊
    UNKNOWN      // 未知
}

/**
 * 消息类型
 */
enum class MessageType {
    TEXT,       // 文本
    IMAGE,      // 图片
    FILE,       // 文件
    VOICE,      // 语音
    VIDEO,      // 视频
    CARD,       // 卡片消息
    UNKNOWN     // 未知
}
