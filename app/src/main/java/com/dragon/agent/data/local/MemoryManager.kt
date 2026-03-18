package com.dragon.agent.data.local

import com.dragon.agent.data.local.dao.ConversationDao
import com.dragon.agent.data.local.dao.MessageDao
import com.dragon.agent.data.local.entity.ConversationEntity
import com.dragon.agent.data.local.entity.MessageEntity
import com.dragon.agent.llm.ChatMessage
import com.dragon.agent.llm.MessageRole
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 记忆管理器
 * 负责对话历史的持久化和检索
 */
@Singleton
class MemoryManager @Inject constructor(
    private val messageDao: MessageDao,
    private val conversationDao: ConversationDao
) {

    companion object {
        private const val DEFAULT_CONVERSATION_ID = "default"
        private const val MAX_CONTEXT_MESSAGES = 20  // 最多保留 20 条消息用于上下文
        private const val SUMMARY_THRESHOLD = 50    // 超过50条消息时触发摘要
    }

    /**
     * 获取当前对话 ID（单会话模式）
     */
    fun getCurrentConversationId(): String = DEFAULT_CONVERSATION_ID

    /**
     * 确保默认对话存在
     */
    suspend fun ensureDefaultConversation() {
        if (conversationDao.getConversation(DEFAULT_CONVERSATION_ID) == null) {
            conversationDao.insertConversation(
                ConversationEntity(
                    id = DEFAULT_CONVERSATION_ID,
                    title = "新对话"
                )
            )
        }
    }

    /**
     * 保存消息到历史
     */
    suspend fun saveMessage(
        conversationId: String = DEFAULT_CONVERSATION_ID,
        messageRole: MessageRole,
        content: String,
        toolCalls: String? = null,
        toolResults: String? = null
    ) {
        val entity = MessageEntity(
            conversationId = conversationId,
            role = messageRole.name.lowercase(),
            content = content,
            toolCalls = toolCalls,
            toolResults = toolResults,
            timestamp = System.currentTimeMillis()
        )
        messageDao.insertMessage(entity)
        conversationDao.updateTimestamp(conversationId, System.currentTimeMillis())

        // 检查是否需要生成摘要
        val count = messageDao.getMessageCount(conversationId)
        if (count > SUMMARY_THRESHOLD) {
            // 实际生产中可以使用 LLM 来生成更好的摘要
            // 这里使用简单的压缩逻辑
            compressHistoryIfNeeded(conversationId)
        }
    }

    /**
     * 获取对话历史
     */
    fun getHistory(conversationId: String = DEFAULT_CONVERSATION_ID): Flow<List<ChatMessage>> {
        return messageDao.getMessages(conversationId).map { entities ->
            entities.map { it.toChatMessage() }
        }
    }

    /**
     * 获取对话历史（一次性加载）
     */
    suspend fun getHistoryList(conversationId: String = DEFAULT_CONVERSATION_ID): List<ChatMessage> {
        return messageDao.getMessagesList(conversationId).map { it.toChatMessage() }
    }

    /**
     * 获取最近 N 条消息（用于上下文）
     */
    suspend fun getRecentMessages(
        conversationId: String = DEFAULT_CONVERSATION_ID,
        count: Int = MAX_CONTEXT_MESSAGES
    ): List<ChatMessage> {
        return messageDao.getRecentMessages(conversationId, count)
            .reversed()  // 按时间正序
            .map { it.toChatMessage() }
    }

    /**
     * 获取消息数量
     */
    suspend fun getMessageCount(conversationId: String = DEFAULT_CONVERSATION_ID): Int {
        return messageDao.getMessageCount(conversationId)
    }

    /**
     * 清除对话历史
     */
    suspend fun clearHistory(conversationId: String = DEFAULT_CONVERSATION_ID) {
        messageDao.deleteMessages(conversationId)
    }

    /**
     * 删除单条消息
     */
    suspend fun deleteMessage(messageId: Long) {
        messageDao.deleteMessage(messageId)
    }

    /**
     * 创建新对话
     */
    suspend fun createConversation(title: String = "新对话"): String {
        val id = UUID.randomUUID().toString()
        conversationDao.insertConversation(
            ConversationEntity(
                id = id,
                title = title
            )
        )
        return id
    }

    /**
     * 获取所有对话列表
     */
    fun getAllConversations(): Flow<List<ConversationEntity>> {
        return conversationDao.getAllConversations()
    }

    /**
     * 删除对话
     */
    suspend fun deleteConversation(conversationId: String) {
        messageDao.deleteMessages(conversationId)
        conversationDao.deleteConversation(conversationId)
    }

    /**
     * 更新对话标题
     */
    suspend fun updateConversationTitle(conversationId: String, title: String) {
        val conversation = conversationDao.getConversation(conversationId)
        conversation?.let {
            conversationDao.updateConversation(it.copy(title = title))
        }
    }

    /**
     * 压缩历史记录（当消息过多时）
     * 简单实现：保留最近的 N 条消息，并添加摘要
     */
    private suspend fun compressHistoryIfNeeded(conversationId: String) {
        val count = messageDao.getMessageCount(conversationId)
        if (count <= SUMMARY_THRESHOLD) return

        // 获取所有消息
        val allMessages = messageDao.getMessagesList(conversationId)
        if (allMessages.size <= MAX_CONTEXT_MESSAGES) return

        // 保留最近的消息
        val toKeep = allMessages.takeLast(MAX_CONTEXT_MESSAGES)
        val toSummarize = allMessages.dropLast(MAX_CONTEXT_MESSAGES)

        // 生成摘要
        val summary = generateSimpleSummary(toSummarize)

        // 删除旧消息，插入摘要
        toSummarize.forEach { messageDao.deleteMessage(it.id) }

        // 插入摘要作为系统消息
        messageDao.insertMessage(
            MessageEntity(
                conversationId = conversationId,
                role = "system",
                content = "[历史摘要] $summary",
                timestamp = System.currentTimeMillis()
            )
        )
    }

    /**
     * 简单摘要生成（基于规则，不使用 LLM）
     */
    private fun generateSimpleSummary(messages: List<MessageEntity>): String {
        if (messages.isEmpty()) return "无历史"

        val userCount = messages.count { it.role == "user" }
        val assistantCount = messages.count { it.role == "assistant" }

        // 获取用户关注的话题（简单关键词提取）
        val userMessages = messages.filter { it.role == "user" }
        val topics = extractTopics(userMessages)

        return buildString {
            append("本轮对话共 $userCount 轮用户提问，")
            append("$assistantCount 次 AI 回复。")
            if (topics.isNotEmpty()) {
                append("主要讨论话题: ${topics.take(3).joinToString("、")}")
            }
        }
    }

    /**
     * 简单话题提取（从消息中提取关键词）
     */
    private fun extractTopics(messages: List<MessageEntity>): List<String> {
        // 简单实现：提取消息中的关键词
        // 实际可以使用 TF-IDF 或其他 NLP 方法
        val keywordPatterns = listOf(
            "天气", "搜索", "计算", "编程", "代码", "问题", "帮助",
            "文件", "阅读", "写入", "新闻", "信息", "学习", "了解"
        )

        val allText = messages.joinToString(" ") { it.content }
        return keywordPatterns.filter { allText.contains(it) }
    }

    /**
     * 生成记忆摘要（用于外部调用）
     */
    suspend fun generateSummary(conversationId: String = DEFAULT_CONVERSATION_ID): String {
        val messages = messageDao.getMessagesList(conversationId)
        if (messages.isEmpty()) return "无对话历史"

        val userMessages = messages.filter { it.role == "user" }
        val assistantMessages = messages.filter { it.role == "assistant" }

        return buildString {
            appendLine("## 对话摘要")
            appendLine("- 用户消息数: ${userMessages.size}")
            appendLine("- AI 回复数: ${assistantMessages.size}")
            appendLine("- 总消息数: ${messages.size}")
            if (messages.isNotEmpty()) {
                appendLine("- 最近一次对话: ${messages.last().content.take(50)}...")
            }
            if (messages.size > MAX_CONTEXT_MESSAGES) {
                appendLine("- ⚠️ 消息已超过 ${MAX_CONTEXT_MESSAGES} 条，部分历史已压缩")
            }
        }
    }
}

/**
 * 扩展函数：将实体转换为 ChatMessage
 */
private fun MessageEntity.toChatMessage(): ChatMessage {
    val role = when (this.role.lowercase()) {
        "user" -> MessageRole.USER
        "assistant" -> MessageRole.ASSISTANT
        "system" -> MessageRole.SYSTEM
        "tool" -> MessageRole.TOOL
        else -> MessageRole.ASSISTANT
    }
    return ChatMessage(role, content)
}
