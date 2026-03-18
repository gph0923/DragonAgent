package com.dragon.agent.channels.feishu

import com.dragon.agent.channels.*
import com.dragon.agent.data.local.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 飞书渠道实现
 */
@Singleton
class FeishuChannel @Inject constructor(
    private val settingsManager: SettingsManager
) : Channel {
    
    override val name = "feishu"
    
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    
    private val messageFlow = MutableSharedFlow<ChannelMessage>(replay = 0, extraBufferCapacity = 100)
    
    private var httpClient: OkHttpClient? = null
    private var appId: String = ""
    private var appSecret: String = ""
    private var verificationToken: String = ""
    private var webhookUrl: String = ""
    
    private var accessToken: String? = null
    private var tokenExpireTime: Long = 0
    
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    override suspend fun isAvailable(): Boolean {
        return appId.isNotEmpty() && appSecret.isNotEmpty()
    }
    
    override fun messages(): Flow<ChannelMessage> = messageFlow.asSharedFlow()
    
    override suspend fun initialize(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 从设置获取飞书配置
            val settings = settingsManager.settings.first()
            
            // 解析飞书配置 (存储在 baseUrl 字段中，格式: appId|appSecret|verificationToken)
            val feishuConfig = settings.baseUrl.split("|")
            if (feishuConfig.size >= 3) {
                appId = feishuConfig[0]
                appSecret = feishuConfig[1]
                verificationToken = feishuConfig[2]
            } else {
                return@withContext Result.failure(IllegalStateException("飞书配置未设置"))
            }
            
            httpClient = OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build()
            
            // 获取 access_token
            val tokenResult = getAccessToken()
            if (tokenResult.isFailure) {
                return@withContext Result.failure(tokenResult.exceptionOrNull() ?: Exception("获取 access_token 失败"))
            }
            
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun sendMessage(message: ChannelMessage): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val client = httpClient ?: return@withContext Result.failure(IllegalStateException("未初始化"))
            
            // 构建飞书消息体
            val content = buildFeishuMessage(message.content, message.messageType)
            
            val requestBody = json.encodeToString(
                FeishuSendMessageRequest.serializer(),
                FeishuSendMessageRequest(
                    receiveId = message.chatId,
                    receiveIdType = if (message.chatType == ChatType.GROUP) "open_id" else "user_id",
                    msgType = "text",
                    content = json.encodeToString(FeishuTextContent.serializer(), FeishuTextContent(message.content))
                )
            ).toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("https://open.feishu.cn/open-apis/im/v1/messages")
                .addHeader("Authorization", "Bearer ${getAccessTokenSync()}")
                .addHeader("Content-Type", "application/json")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    return@withContext Result.failure(IOException("发送失败: ${response.code}"))
                }
                Result.success(Unit)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun getUserInfo(userId: String): Result<ChannelUser> = withContext(Dispatchers.IO) {
        try {
            val client = httpClient ?: return@withContext Result.failure(IllegalStateException("未初始化"))
            
            val request = Request.Builder()
                .url("https://open.feishu.cn/open-apis/auth/v3/user_info/get?user_id=$userId")
                .addHeader("Authorization", "Bearer ${getAccessTokenSync()}")
                .get()
                .build()
            
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("空响应"))
                val feishuUser = json.decodeFromString(FeishuUserResponse.serializer(), body)
                
                Result.success(ChannelUser(
                    id = userId,
                    name = feishuUser.name ?: "",
                    avatar = feishuUser.avatar?.avatarOrigin ?: ""
                ))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    override suspend fun shutdown(): Result<Unit> {
        scope.cancel()
        httpClient = null
        return Result.success(Unit)
    }
    
    /**
     * 处理飞书事件回调
     */
    fun handleCallback(payload: String): Result<ChannelMessage?> {
        return try {
            val callback = json.decodeFromString(FeishuCallback.serializer(), payload)
            
            // 验证 token
            if (callback.verificationToken != verificationToken) {
                return Result.failure(IllegalStateException("Token 验证失败"))
            }
            
            // 处理消息事件
            if (callback.type == "im.message") {
                val message = parseFeishuMessage(callback.event)
                if (message != null) {
                    scope.launch {
                        messageFlow.emit(message)
                    }
                }
                Result.success(message)
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 获取 access_token
     */
    private suspend fun getAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        // 检查缓存
        if (!accessToken.isNullOrEmpty() && System.currentTimeMillis() < tokenExpireTime) {
            return@withContext Result.success(accessToken!!)
        }
        
        try {
            val client = httpClient ?: return@withContext Result.failure(IllegalStateException("未初始化"))
            
            val requestBody = json.encodeToString(
                FeishuTokenRequest.serializer(),
                FeishuTokenRequest(
                    appId = appId,
                    appSecret = appSecret
                )
            ).toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal")
                .post(requestBody)
                .build()
            
            client.newCall(request).execute().use { response ->
                val body = response.body?.string() ?: return@withContext Result.failure(IOException("空响应"))
                val tokenResponse = json.decodeFromString(FeishuTokenResponse.serializer(), body)
                
                if (tokenResponse.code == 0) {
                    accessToken = tokenResponse.tenantAccessToken
                    tokenExpireTime = System.currentTimeMillis() + (tokenResponse.expire - 300) * 1000
                    Result.success(tokenResponse.tenantAccessToken)
                } else {
                    Result.failure(IOException("获取 token 失败: ${tokenResponse.msg}"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    private fun getAccessTokenSync(): String {
        return accessToken ?: ""
    }
    
    /**
     * 解析飞书消息
     */
    private fun parseFeishuMessage(event: FeishuEvent): ChannelMessage? {
        return try {
            val message = event.message ?: return null
            
            ChannelMessage(
                id = message.messageId,
                channel = name,
                sender = message.senderId?.userId ?: "",
                senderName = "",
                content = message.content?.let { json.decodeFromString<FeishuMessageContent>(it).text } ?: "",
                rawContent = event,
                timestamp = message.createTime.toLongOrNull() ?: System.currentTimeMillis(),
                chatId = message.chatId ?: "",
                chatType = if (message.chatId?.startsWith("oc_") == true) ChatType.GROUP else ChatType.DIRECT,
                messageType = MessageType.TEXT
            )
        } catch (e: Exception) {
            null
        }
    }
    
    /**
     * 构建飞书消息
     */
    private fun buildFeishuMessage(content: String, type: MessageType): String {
        return when (type) {
            MessageType.TEXT -> json.encodeToString(FeishuTextContent.serializer(), FeishuTextContent(content))
            else -> json.encodeToString(FeishuTextContent.serializer(), FeishuTextContent(content))
        }
    }
}

// ============ 飞书 API 数据类 ============

@Serializable
data class FeishuTokenRequest(
    val appId: String,
    val appSecret: String
)

@Serializable
data class FeishuTokenResponse(
    val code: Int = 0,
    val msg: String = "",
    val tenantAccessToken: String = "",
    val expire: Int = 0
)

@Serializable
data class FeishuCallback(
    val type: String = "",
    val verificationToken: String = "",
    val event: FeishuEvent = FeishuEvent()
)

@Serializable
data class FeishuEvent(
    val message: FeishuMessage? = null
)

@Serializable
data class FeishuMessage(
    val messageId: String = "",
    val chatId: String? = null,
    val senderId: FeishuSenderId? = null,
    val content: String? = null,
    val createTime: String = ""
)

@Serializable
data class FeishuSenderId(
    val userId: String? = null,
    val openId: String? = null,
    val unionId: String? = null
)

@Serializable
data class FeishuMessageContent(
    val text: String = ""
)

@Serializable
data class FeishuTextContent(
    val text: String
)

@Serializable
data class FeishuUserResponse(
    val code: Int = 0,
    val name: String? = null,
    val avatar: FeishuAvatar? = null
)

@Serializable
data class FeishuAvatar(
    val avatarOrigin: String = ""
)

@Serializable
data class FeishuSendMessageRequest(
    val receiveId: String,
    val receiveIdType: String = "user_id",
    val msgType: String = "text",
    val content: String
)
