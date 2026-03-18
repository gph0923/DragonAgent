package com.dragon.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 搜索结果项
 */
@Serializable
data class SearchResult(
    val title: String,
    val url: String,
    val snippet: String
)

/**
 * 网络搜索工具
 */
class WebSearchTool : BaseTool(
    name = "web_search",
    description = "搜索互联网获取信息。使用 DuckDuckGo 搜索 API，返回相关网页结果。"
) {
    private val json = Json { ignoreUnknownKeys = true }

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = ToolParameters(
                properties = mapOf(
                    "query" to ToolProperty(
                        type = "string",
                        description = "搜索关键词"
                    ),
                    "max_results" to ToolProperty(
                        type = "number",
                        description = "返回结果数量，默认 5"
                    )
                ),
                required = listOf("query")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val query = args["query"]?.toString()
                    ?: return@withContext ToolResult(false, "", "Missing query")

                val maxResults = (args["max_results"]?.toString()?.toIntOrNull() ?: 5).coerceIn(1, 10)

                val results = search(query, maxResults)

                if (results.isEmpty()) {
                    ToolResult(true, "未找到相关结果")
                } else {
                    val formatted = results.joinToString("\n\n") { result ->
                        "📄 ${result.title}\n🔗 ${result.url}\n📝 ${result.snippet}"
                    }
                    ToolResult(true, formatted)
                }
            } catch (e: Exception) {
                ToolResult(false, "", "搜索失败: ${e.message}")
            }
        }
    }

    /**
     * 使用 DuckDuckGo HTML 搜索
     */
    private fun search(query: String, maxResults: Int): List<SearchResult> {
        val results = mutableListOf<SearchResult>()

        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://html.duckduckgo.com/html/?q=$encodedQuery")

            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val response = connection.inputStream.bufferedReader().readText()

            // 解析 HTML 结果
            val resultRegex = Regex("""<a rel="nofollow" class="result__a" href="([^"]+)"[^>]*>(.+?)</a>""")
            val snippetRegex = Regex("""<a class="result__snippet"[^>]*>(.+?)</a>""")

            val urls = resultRegex.findAll(response).take(maxResults).map { match ->
                val href = match.groupValues[1]
                // 提取实际 URL（DDG 会重定向）
                val realUrl = href.substringAfter("uddg=").substringBefore("&")
                val title = match.groupValues[2]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
                Pair(title, java.net.URLDecoder.decode(realUrl, "UTF-8"))
            }.toList()

            val snippets = snippetRegex.findAll(response).take(maxResults).map { match ->
                match.groupValues[1]
                    .replace(Regex("<[^>]+>"), "")
                    .replace("&amp;", "&")
                    .replace("&quot;", "\"")
                    .replace("&lt;", "<")
                    .replace("&gt;", ">")
            }.toList()

            urls.zip(snippets).forEach { (urlAndTitle, snippet) ->
                results.add(SearchResult(
                    title = urlAndTitle.first,
                    url = urlAndTitle.second,
                    snippet = snippet
                ))
            }
        } catch (e: Exception) {
            // 如果 DuckDuckGo 失败，尝试备用方案
            return searchFallback(query, maxResults)
        }

        return results
    }

    /**
     * 备用搜索方案（使用 Bing API 的免费前端）
     */
    private fun searchFallback(query: String, maxResults: Int): List<SearchResult> {
        // 这里可以添加备用搜索逻辑
        // 目前返回空列表，让上层处理
        return emptyList()
    }
}
