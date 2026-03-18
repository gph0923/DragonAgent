package com.dragon.agent.llm

import com.dragon.agent.tools.ToolDefinition
import kotlinx.serialization.json.JsonObject

/**
 * LLM Provider 接口
 */
interface LLMProvider {
    /**
     * 发送聊天请求
     */
    suspend fun chat(messages: List<ChatMessage>): LLMResponse

    /**
     * 带工具的聊天请求
     */
    suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse

    suspend fun continueChat(messages: List<ChatMessage>): LLMResponse = chat(messages)
    
    /**
     * 带工具的继续聊天（用于工具调用循环）
     */
    suspend fun continueChatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse = chatWithTools(messages, tools)
    suspend fun embeddings(texts: List<String>): List<FloatArray>
    fun getModelName(): String
    fun getMaxTokens(): Int
    fun getContextLength(): Int
}

/**
 * Chat 消息
 */
data class ChatMessage(
    val role: MessageRole,
    val content: String,
    val name: String? = null,
    val toolCalls: List<ToolCall>? = null,
    val toolCallId: String? = null
)

/**
 * 消息角色
 */
enum class MessageRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

/**
 * LLM 响应
 */
data class LLMResponse(
    val content: String,
    val toolCalls: List<ToolCall>?,
    val usage: TokenUsage,
    val finishReason: FinishReason
)

/**
 * Tool 调用
 */
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: Map<String, Any>
)

/**
 * Token 使用量
 */
data class TokenUsage(
    val promptTokens: Int,
    val completionTokens: Int,
    val totalTokens: Int
) {
    companion object {
        val EMPTY = TokenUsage(0, 0, 0)
    }
}

/**
 * 完成原因
 */
enum class FinishReason {
    STOP,
    LENGTH,
    TOOL_CALLS,
    CONTENT_FILTER,
    ERROR
}
