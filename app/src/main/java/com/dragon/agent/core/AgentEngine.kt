package com.dragon.agent.core

import com.dragon.agent.llm.ChatMessage
import com.dragon.agent.llm.LLMProvider
import com.dragon.agent.llm.LLMResponse
import com.dragon.agent.llm.MessageRole
import com.dragon.agent.llm.ToolCall
import com.dragon.agent.tools.ToolDefinition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Agent 引擎 - 核心执行逻辑
 */
@Singleton
class AgentEngine @Inject constructor(
    private val llmProvider: LLMProvider
) {

    /**
     * 执行对话
     */
    suspend fun execute(
        systemPrompt: String,
        userInput: String,
        history: List<ChatMessage> = emptyList()
    ): Result<LLMResponse> = withContext(Dispatchers.Default) {
        try {
            // 构建消息列表
            val messages = buildMessages(systemPrompt, userInput, history)

            // 调用 LLM
            val response = llmProvider.chat(messages)

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 带工具调用的对话（Phase 2 扩展）
     */
    suspend fun executeWithTools(
        systemPrompt: String,
        userInput: String,
        history: List<ChatMessage> = emptyList(),
        tools: List<ToolDefinition> = emptyList(),
        onToolCall: suspend (ToolCall) -> Any? = { null }
    ): Result<LLMResponse> = withContext(Dispatchers.Default) {
        try {
            val messages = buildMessages(systemPrompt, userInput, history)

            // 第一次调用（带工具）
            val response = if (tools.isNotEmpty()) {
                llmProvider.chatWithTools(messages, tools)
            } else {
                llmProvider.chat(messages)
            }

            // 工具调用循环
            var loopCount = 0
            while (response.toolCalls != null && loopCount < 5) {
                // 添加助手消息和工具结果
                messages.add(ChatMessage(
                    role = MessageRole.ASSISTANT,
                    content = response.content,
                    toolCalls = response.toolCalls
                ))

                // 执行工具
                val toolCalls = response.toolCalls
                for (toolCall in toolCalls) {
                    val result = onToolCall(toolCall)
                    messages.add(ChatMessage(
                        role = MessageRole.TOOL,
                        content = result?.toString() ?: "",
                        toolCallId = toolCall.id
                    ))
                }

                // 继续调用（带工具）
                response = if (tools.isNotEmpty()) {
                    llmProvider.continueChatWithTools(messages, tools)
                } else {
                    llmProvider.continueChat(messages)
                }
                loopCount++
            }

            Result.success(response)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildMessages(
        systemPrompt: String,
        userInput: String,
        history: List<ChatMessage>
    ): MutableList<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()

        // System prompt
        if (systemPrompt.isNotBlank()) {
            messages.add(ChatMessage(
                role = MessageRole.SYSTEM,
                content = systemPrompt
            ))
        }

        // 历史消息
        messages.addAll(history)

        // 当前输入
        messages.add(ChatMessage(
            role = MessageRole.USER,
            content = userInput
        ))

        return messages
    }
}
