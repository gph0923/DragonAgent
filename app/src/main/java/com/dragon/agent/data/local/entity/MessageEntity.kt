package com.dragon.agent.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * 对话消息实体
 */
@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val conversationId: String,
    val role: String,          // user, assistant, system
    val content: String,
    val toolCalls: String? = null,  // JSON string of tool calls
    val toolResults: String? = null, // JSON string of tool results
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * 对话会话实体
 */
@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey
    val id: String,
    val title: String = "新对话",
    val systemPrompt: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
