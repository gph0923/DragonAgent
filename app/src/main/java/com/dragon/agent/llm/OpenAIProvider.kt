package com.dragon.agent.llm

import com.dragon.agent.data.local.SettingsManager
import com.dragon.agent.tools.ToolDefinition
import com.dragon.agent.tools.ToolProperty
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI Provider 实现
 */
@Singleton
class OpenAIProvider @Inject constructor(
    private val httpClient: OkHttpClient,
    private val json: Json,
    private val settingsManager: SettingsManager
) : LLMProvider {

    private val baseUrl = "https://api.openai.com/v1"
    private val model = "gpt-4-turbo-preview"
    private val maxTokens = 4096
    private val contextLength = 128000

    // 从 SettingsManager 获取 API Key
    private suspend fun getApiKey(): String {
        return settingsManager.settings.first().apiKey
    }

    override suspend fun chat(messages: List<ChatMessage>): LLMResponse {
        val request = ChatCompletionRequest(
            model = model,
            messages = messages.map { it.toOpenAIMessage() },
            maxTokens = maxTokens
        )
        return executeRequest(request)
    }

    override suspend fun chatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse {
        val toolDefs = tools.map { tool ->
            OpenAIToolDefinition(
                type = "function",
                function = OpenAIFunctionDefinition(
                    name = tool.name,
                    description = tool.description,
                    parameters = tool.parameters.toJsonObject()
                )
            )
        }

        val request = ChatCompletionRequest(
            model = model,
            messages = messages.map { it.toOpenAIMessage() },
            tools = toolDefs,
            toolChoice = "auto",
            maxTokens = maxTokens
        )
        return executeRequest(request)
    }

    override suspend fun continueChat(messages: List<ChatMessage>): LLMResponse {
        return chat(messages)
    }

    override suspend fun continueChatWithTools(
        messages: List<ChatMessage>,
        tools: List<ToolDefinition>
    ): LLMResponse {
        return chatWithTools(messages, tools)
    }

    override suspend fun embeddings(texts: List<String>): List<FloatArray> {
        val request = EmbeddingsRequest(
            model = "text-embedding-3-small",
            input = texts
        )

        val jsonBody = json.encodeToString(EmbeddingsRequest.serializer(), request)
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        
        val apiKey = getApiKey()

        val req = Request.Builder()
            .url("$baseUrl/embeddings")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("Embeddings API error: ${response.code}")
                }
                val responseBody = response.body?.string()
                    ?: throw IOException("Empty embeddings response")
                val embeddingsResponse = json.decodeFromString(
                    EmbeddingsResponse.serializer(),
                    responseBody
                )
                embeddingsResponse.data.map { it.embedding }
            }
        }
    }

    override fun getModelName() = model
    override fun getMaxTokens() = maxTokens
    override fun getContextLength() = contextLength

    /**
     * 将 ToolParameters 转换为 JsonObject
     */
    private fun com.dragon.agent.tools.ToolParameters.toJsonObject(): JsonObject {
        val properties: Map<String, JsonElement> = this.properties.mapValues { (_, prop) ->
            buildJsonObject {
                put("type", JsonPrimitive(prop.type))
                put("description", JsonPrimitive(prop.description))
                prop.enum?.let { put("enum", JsonArray(it.map { s -> JsonPrimitive(s) })) }
            }
        }
        return buildJsonObject {
            put("type", JsonPrimitive(this@toJsonObject.type))
            put("properties", JsonObject(properties))
            put("required", JsonArray(this@toJsonObject.required.map { JsonPrimitive(it) }))
        }
    }

    private suspend fun executeRequest(request: ChatCompletionRequest): LLMResponse {
        val jsonBody = json.encodeToString(ChatCompletionRequest.serializer(), request)
        val body = jsonBody.toRequestBody("application/json".toMediaType())
        
        val apiKey = getApiKey()

        val req = Request.Builder()
            .url("$baseUrl/chat/completions")
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Content-Type", "application/json")
            .post(body)
            .build()

        return withContext(Dispatchers.IO) {
            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("API error: ${response.code} ${response.message}")
                }

                val responseBody = response.body?.string()
                    ?: throw IOException("Empty response")

                val completion = json.decodeFromString(
                    ChatCompletionResponse.serializer(),
                    responseBody
                )

                val choice = completion.choices.firstOrNull()
                    ?: throw IOException("No choices in response")

                val toolCalls = choice.message.toolCalls?.map { tc ->
                    ToolCall(
                        id = tc.id,
                        name = tc.function.name,
                        arguments = json.decodeFromString<Map<String, Any>>(tc.function.arguments)
                    )
                }

                LLMResponse(
                    content = choice.message.content ?: "",
                    toolCalls = toolCalls,
                    usage = TokenUsage(
                        promptTokens = completion.usage.promptTokens,
                        completionTokens = completion.usage.completionTokens,
                        totalTokens = completion.usage.totalTokens
                    ),
                    finishReason = when (choice.finishReason) {
                        "stop" -> FinishReason.STOP
                        "length" -> FinishReason.LENGTH
                        "tool_calls" -> FinishReason.TOOL_CALLS
                        "content_filter" -> FinishReason.CONTENT_FILTER
                        else -> FinishReason.ERROR
                    }
                )
            }
        }
    }

    private fun ChatMessage.toOpenAIMessage(): OpenAIMessage {
        return OpenAIMessage(
            role = role.name.lowercase(),
            content = content,
            name = name,
            toolCalls = toolCalls?.let { calls ->
                calls.map { tc ->
                    OpenAIToolCall(
                        id = tc.id,
                        function = OpenAIFunction(
                            name = tc.name,
                            arguments = json.encodeToString(MapSerializer(String.serializer(), String.serializer()), tc.arguments.mapValues { it.value.toString() })
                        )
                    )
                }
            },
            toolCallId = toolCallId
        )
    }
}

// Serialization DTOs
@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<OpenAIMessage>,
    val tools: List<OpenAIToolDefinition>? = null,
    val toolChoice: String? = null,
    val maxTokens: Int? = null
)

@Serializable
private data class OpenAIMessage(
    val role: String,
    val content: String,
    val name: String? = null,
    val toolCalls: List<OpenAIToolCall>? = null,
    @SerialName("tool_call_id")
    val toolCallId: String? = null
)

@Serializable
private data class OpenAIToolCall(
    val id: String,
    val function: OpenAIFunction
)

@Serializable
private data class OpenAIFunction(
    val name: String,
    val arguments: String
)

@Serializable
private data class OpenAIToolDefinition(
    val type: String = "function",
    val function: OpenAIFunctionDefinition
)

@Serializable
private data class OpenAIFunctionDefinition(
    val name: String,
    val description: String,
    val parameters: JsonObject
)

@Serializable
private data class ChatCompletionResponse(
    val id: String,
    val choices: List<Choice>,
    val usage: Usage
)

@Serializable
private data class Choice(
    val message: Message,
    @SerialName("finish_reason")
    val finishReason: String
)

@Serializable
private data class Message(
    val role: String,
    val content: String?,
    @SerialName("tool_calls")
    val toolCalls: List<OpenAIToolCall>? = null
)

@Serializable
private data class Usage(
    @SerialName("prompt_tokens")
    val promptTokens: Int,
    @SerialName("completion_tokens")
    val completionTokens: Int,
    @SerialName("total_tokens")
    val totalTokens: Int
)

@Serializable
private data class EmbeddingsRequest(
    val model: String,
    val input: List<String>
)

@Serializable
private data class EmbeddingsResponse(
    val data: List<EmbeddingData>
)

@Serializable
private data class EmbeddingData(
    val embedding: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as EmbeddingData
        return embedding.contentEquals(other.embedding)
    }

    override fun hashCode(): Int {
        return embedding.contentHashCode()
    }
}
