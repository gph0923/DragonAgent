package com.dragon.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * HTTP 请求工具
 * 用于调用外部 API
 */
class HttpRequestTool : BaseTool(
    name = "http_request",
    description = "发送 HTTP 请求到指定的 URL。支持 GET、POST、PUT、DELETE 方法，可发送 JSON 数据。"
) {
    private val json = Json { ignoreUnknownKeys = true }

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = ToolParameters(
                properties = mapOf(
                    "url" to ToolProperty(
                        type = "string",
                        description = "请求的 URL"
                    ),
                    "method" to ToolProperty(
                        type = "string",
                        description = "HTTP 方法: GET, POST, PUT, DELETE",
                        enum = listOf("GET", "POST", "PUT", "DELETE")
                    ),
                    "headers" to ToolProperty(
                        type = "object",
                        description = "请求头，如: {\"Content-Type\": \"application/json\"}"
                    ),
                    "body" to ToolProperty(
                        type = "string",
                        description = "请求体（POST/PUT 时使用）"
                    ),
                    "timeout" to ToolProperty(
                        type = "number",
                        description = "超时时间（毫秒），默认 30000"
                    )
                ),
                required = listOf("url", "method")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val urlStr = args["url"]?.toString()
                    ?: return@withContext ToolResult(false, "", "Missing url")

                val method = (args["method"]?.toString() ?: "GET").uppercase()
                val body = args["body"]?.toString()
                val timeout = args["timeout"]?.toString()?.toIntOrNull() ?: 30000

                // 解析 headers
                val headers = parseHeaders(args["headers"])

                val result = httpRequest(urlStr, method, headers, body, timeout)

                ToolResult(true, result)
            } catch (e: Exception) {
                ToolResult(false, "", "HTTP 请求失败: ${e.message}")
            }
        }
    }

    /**
     * 解析 headers
     */
    @Suppress("UNCHECKED_CAST")
    private fun parseHeaders(headersArg: Any?): Map<String, String> {
        if (headersArg == null) return emptyMap()

        return when (headersArg) {
            is Map<*, *> -> {
                headersArg.mapNotNull { (k, v) ->
                    (k?.toString())?.let { key ->
                        (v?.toString())?.let { value ->
                            key to value
                        }
                    }
                }.toMap()
            }
            is String -> {
                // JSON 字符串
                try {
                    json.decodeFromString<Map<String, String>>(headersArg)
                } catch (e: Exception) {
                    emptyMap()
                }
            }
            else -> emptyMap()
        }
    }

    /**
     * 发送 HTTP 请求
     */
    private fun httpRequest(
        urlStr: String,
        method: String,
        headers: Map<String, String>,
        body: String?,
        timeout: Int
    ): String {
        val url = URL(urlStr)
        val connection = url.openConnection() as HttpURLConnection

        connection.requestMethod = method
        connection.connectTimeout = timeout
        connection.readTimeout = timeout
        connection.doInput = true

        // 设置默认 headers
        connection.setRequestProperty("User-Agent", "DragonAgent/1.0")
        connection.setRequestProperty("Accept", "application/json")

        // 设置自定义 headers
        headers.forEach { (key, value) ->
            connection.setRequestProperty(key, value)
        }

        // 设置请求体
        if (body != null && method in listOf("POST", "PUT", "PATCH")) {
            connection.doOutput = true
            if (!headers.containsKey("Content-Type")) {
                connection.setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.bufferedWriter().use { writer ->
                writer.write(body)
            }
        }

        // 获取响应
        val responseCode = connection.responseCode
        val responseBody = connection.inputStream.bufferedReader().use { reader ->
            reader.readText()
        }

        // 构建响应结果
        return buildString {
            appendLine("📡 HTTP $method ${connection.url}")
            appendLine("📊 Status: $responseCode")
            appendLine()
            appendLine("📋 Response:")
            appendLine(responseBody.take(2000))  // 限制长度
            if (responseBody.length > 2000) {
                appendLine("... (truncated)")
            }
        }
    }
}
