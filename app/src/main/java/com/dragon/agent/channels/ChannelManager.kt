package com.dragon.agent.channels

import com.dragon.agent.channels.feishu.FeishuChannel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 渠道管理器
 * 统一管理所有消息渠道
 */
@Singleton
class ChannelManager @Inject constructor(
    private val feishuChannel: FeishuChannel
) {
    private val _channels = MutableStateFlow<Map<String, Channel>>(emptyMap())
    val channels: StateFlow<Map<String, Channel>> = _channels.asStateFlow()
    
    private val _activeChannel = MutableStateFlow<Channel?>(null)
    val activeChannel: StateFlow<Channel?> = _activeChannel.asStateFlow()
    
    /**
     * 初始化所有渠道
     */
    suspend fun initializeAll() {
        val channelMap = mutableMapOf<String, Channel>()
        
        // 初始化飞书渠道
        if (feishuChannel.isAvailable()) {
            feishuChannel.initialize().onSuccess {
                channelMap["feishu"] = feishuChannel
            }
        }
        
        _channels.value = channelMap
        
        // 设置默认渠道
        if (channelMap.isNotEmpty()) {
            _activeChannel.value = channelMap.values.firstOrNull()
        }
    }
    
    /**
     * 获取指定渠道
     */
    fun getChannel(name: String): Channel? {
        return _channels.value[name]
    }
    
    /**
     * 设置活跃渠道
     */
    fun setActiveChannel(name: String) {
        _activeChannel.value = _channels.value[name]
    }
    
    /**
     * 获取所有可用渠道名称
     */
    fun getAvailableChannels(): List<String> {
        return _channels.value.keys.toList()
    }
    
    /**
     * 关闭所有渠道
     */
    suspend fun shutdownAll() {
        _channels.value.values.forEach { channel ->
            try {
                channel.shutdown()
            } catch (e: Exception) {
                // 忽略关闭错误
            }
        }
        _channels.value = emptyMap()
        _activeChannel.value = null
    }
}
