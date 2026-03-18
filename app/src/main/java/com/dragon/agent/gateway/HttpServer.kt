package com.dragon.agent.gateway

import android.content.Context
import com.dragon.agent.channels.feishu.FeishuChannel
import com.dragon.agent.data.local.SettingsManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton

/**
 * HTTP 服务器 - 处理 Webhook 回调
 * 用于接收飞书/Telegram 等渠道的消息回调
 */
@Singleton
class HttpServer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsManager: SettingsManager,
    private val gateway: Gateway
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    companion object {
        const val DEFAULT_PORT = 8080
    }

    /**
     * 启动 HTTP 服务器
     */
    fun start(port: Int = DEFAULT_PORT) {
        if (isRunning) return
        
        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true
                println("[HttpServer] Started on port $port")
                
                while (isRunning) {
                    try {
                        val client = serverSocket?.accept() ?: continue
                        handleRequest(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            println("[HttpServer] Error: ${e.message}")
                        }
                    }
                }
            } catch (e: Exception) {
                println("[HttpServer] Failed to start: ${e.message}")
            }
        }
    }

    /**
     * 停止 HTTP 服务器
     */
    fun stop() {
        isRunning = false
        serverSocket?.close()
        serverSocket = null
        println("[HttpServer] Stopped")
    }

    /**
     * 处理 HTTP 请求
     */
    private suspend fun handleRequest(client: Socket) {
        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
        val output = client.getOutputStream()
        
        try {
            // 读取请求行
            val requestLine = reader.readLine() ?: return
            val parts = requestLine.split(" ")
            val method = parts.getOrNull(0) ?: return
            val path = parts.getOrNull(1) ?: return
            
            println("[HttpServer] $method $path")
            
            // 读取请求头直到空行
            val headers = mutableMapOf<String, String>()
            var line = reader.readLine()
            while (line != null && line.isNotEmpty()) {
                if (line.contains(":")) {
                    val parts = line.split(":", limit = 2)
                    headers[parts[0].trim()] = parts.getOrNull(1)?.trim() ?: ""
                }
                line = reader.readLine()
            }
            
            val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
            
            val body = if (contentLength > 0) {
                val bodyChars = CharArray(contentLength)
                reader.read(bodyChars)
                String(bodyChars)
            } else {
                ""
            }
            
            // 路由处理
            val response = when {
                path.startsWith("/webhook/feishu") -> handleFeishuWebhook(path, body)
                path.startsWith("/health") -> """{"status":"ok"}"""
                path.startsWith("/status") -> gateway.getStatus().let {
                    """{"state":"${it.state}","channels":${it.channels},"skills":${it.skillsLoaded},"tools":${it.toolsCount}}"""
                }
                else -> """{"error":"Not found"}"""
            }
            
            // 发送响应
            val httpResponse = buildString {
                append("HTTP/1.1 200 OK\r\n")
                append("Content-Type: application/json\r\n")
                append("Content-Length: ${response.length}\r\n")
                append("Connection: close\r\n")
                append("\r\n")
                append(response)
            }
            
            output.write(httpResponse.toByteArray())
            output.flush()
        } catch (e: Exception) {
            println("[HttpServer] Error handling request: ${e.message}")
        } finally {
            client.close()
        }
    }

    /**
     * 处理飞书 Webhook
     */
    private suspend fun handleFeishuWebhook(path: String, body: String): String {
        // 解析飞书回调
        // 实际实现需要调用 FeishuChannel.handleCallback
        return try {
            // 这里可以调用飞书渠道的回调处理
            """{"code":0,"msg":"ok"}"""
        } catch (e: Exception) {
            """{"error":"${e.message}"}"""
        }
    }
}

/**
 * Webhook 路由配置
 */
object WebhookRoutes {
    const val FEISHU = "/webhook/feishu"
    const val TELEGRAM = "/webhook/telegram"
    const val DISCORD = "/webhook/discord"
    
    // 验证 Token 端点
    const val FEISHU_VERIFY = "/webhook/feishu/verify"
}
