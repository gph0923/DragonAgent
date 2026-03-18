package com.dragon.agent.data.local.dao

import androidx.room.*
import com.dragon.agent.data.local.entity.ConversationEntity
import com.dragon.agent.data.local.entity.MessageEntity
import kotlinx.coroutines.flow.Flow

/**
 * 对话 DAO
 */
@Dao
interface ConversationDao {

    /**
     * 获取所有对话
     */
    @Query("SELECT * FROM conversations ORDER BY updatedAt DESC")
    fun getAllConversations(): Flow<List<ConversationEntity>>

    /**
     * 获取单个对话
     */
    @Query("SELECT * FROM conversations WHERE id = :id")
    suspend fun getConversation(id: String): ConversationEntity?

    /**
     * 创建对话
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConversation(conversation: ConversationEntity)

    /**
     * 更新对话
     */
    @Update
    suspend fun updateConversation(conversation: ConversationEntity)

    /**
     * 删除对话
     */
    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    /**
     * 更新对话时间
     */
    @Query("UPDATE conversations SET updatedAt = :timestamp WHERE id = :id")
    suspend fun updateTimestamp(id: String, timestamp: Long)
}

/**
 * 消息 DAO
 */
@Dao
interface MessageDao {

    /**
     * 获取对话的所有消息
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    fun getMessages(conversationId: String): Flow<List<MessageEntity>>

    /**
     * 获取对话的消息列表（一次性）
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp ASC")
    suspend fun getMessagesList(conversationId: String): List<MessageEntity>

    /**
     * 获取最近的 N 条消息
     */
    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentMessages(conversationId: String, limit: Int): List<MessageEntity>

    /**
     * 获取消息数量
     */
    @Query("SELECT COUNT(*) FROM messages WHERE conversationId = :conversationId")
    suspend fun getMessageCount(conversationId: String): Int

    /**
     * 插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: MessageEntity): Long

    /**
     * 批量插入消息
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessages(messages: List<MessageEntity>)

    /**
     * 删除对话的所有消息
     */
    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessages(conversationId: String)

    /**
     * 删除单条消息
     */
    @Query("DELETE FROM messages WHERE id = :id")
    suspend fun deleteMessage(id: Long)
}
