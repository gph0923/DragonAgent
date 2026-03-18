package com.dragon.agent.channels

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject

/**
 * 消息格式化器
 * 处理不同渠道的消息格式转换
 */
object MessageFormatter {
    
    private val json = Json { ignoreUnknownKeys = true }
    
    /**
     * 格式化消息为统一格式
     */
    fun format(message: ChannelMessage): String {
        return when (message.messageType) {
            MessageType.TEXT -> formatTextMessage(message)
            MessageType.IMAGE -> formatImageMessage(message)
            MessageType.FILE -> formatFileMessage(message)
            MessageType.VOICE -> formatVoiceMessage(message)
            MessageType.VIDEO -> formatVideoMessage(message)
            MessageType.CARD -> formatCardMessage(message)
            MessageType.UNKNOWN -> message.content
        }
    }
    
    /**
     * 格式化文本消息
     */
    private fun formatTextMessage(message: ChannelMessage): String {
        var content = message.content.trim()
        
        // 移除 @机器人 提及
        content = content.replace(Regex("@机器人\\s*"), "")
        content = content.replace(Regex("@[\\w]+\\s*"), "")
        
        return content
    }
    
    /**
     * 格式化图片消息
     */
    private fun formatImageMessage(message: ChannelMessage): String {
        return buildString {
            append("[图片]")
            if (message.rawContent != null) {
                append(" ")
                append(extractImageInfo(message.rawContent))
            }
        }
    }
    
    /**
     * 格式化文件消息
     */
    private fun formatFileMessage(message: ChannelMessage): String {
        return buildString {
            append("[文件]")
            if (message.rawContent != null) {
                append(" ")
                append(extractFileInfo(message.rawContent))
            }
        }
    }
    
    /**
     * 格式化语音消息
     */
    private fun formatVoiceMessage(message: ChannelMessage): String {
        return "[语音消息]"
    }
    
    /**
     * 格式化视频消息
     */
    private fun formatVideoMessage(message: ChannelMessage): String {
        return "[视频消息]"
    }
    
    /**
     * 格式化卡片消息
     */
    private fun formatCardMessage(message: ChannelMessage): String {
        return "[卡片消息] ${message.content}"
    }
    
    /**
     * 提取图片信息
     */
    private fun extractImageInfo(rawContent: Any): String {
        return try {
            when (rawContent) {
                is String -> {
                    val content = json.parseToJsonElement(rawContent)
                    val imageKey = content.jsonObject?.keys?.firstOrNull() ?: "image"
                    "[$imageKey]"
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 提取文件信息
     */
    private fun extractFileInfo(rawContent: Any): String {
        return try {
            when (rawContent) {
                is String -> {
                    val content = json.parseToJsonElement(rawContent)
                    val fileName = content.jsonObject?.get("file_name")?.toString() ?: "未知文件"
                    fileName
                }
                else -> ""
            }
        } catch (e: Exception) {
            ""
        }
    }
    
    /**
     * 解析消息类型
     */
    fun parseMessageType(content: String): MessageType {
        return when {
            content.startsWith("{") && content.contains("image") -> MessageType.IMAGE
            content.startsWith("{") && content.contains("file") -> MessageType.FILE
            content.startsWith("{") && content.contains("voice") -> MessageType.VOICE
            content.startsWith("{") && content.contains("video") -> MessageType.VIDEO
            content.startsWith("{") && content.contains("card") -> MessageType.CARD
            else -> MessageType.TEXT
        }
    }
    
    /**
     * 清理消息内容
     */
    fun cleanContent(content: String): String {
        return content
            .trim()
            .replace(Regex("\\s+"), " ")  // 合并多余空格
            .replace(Regex("[\u0000-\u001F]"), "")  // 移除控制字符
    }
}

/**
 * 回复格式化器
 */
object ReplyFormatter {
    
    /**
     * 格式化 AI 回复为渠道兼容格式
     */
    fun format(response: String, channel: String): String {
        return when (channel) {
            "feishu" -> formatForFeishu(response)
            "telegram" -> formatForTelegram(response)
            "discord" -> formatForDiscord(response)
            else -> response
        }
    }
    
    /**
     * 飞书格式
     */
    private fun formatForFeishu(response: String): String {
        // 飞书支持富文本，但简单文本最安全
        return response
    }
    
    /**
     * Telegram 格式
     */
    private fun formatForTelegram(response: String): String {
        // Telegram 支持 Markdown
        return response
            .replace("*", "\\*")
            .replace("_", "\\_")
            .replace("`", "\\`")
    }
    
    /**
     * Discord 格式
     */
    private fun formatForDiscord(response: String): String {
        // Discord 支持 Markdown
        return response
    }
    
    /**
     * 拆分长消息
     */
    fun splitLongMessage(response: String, maxLength: Int = 4000): List<String> {
        if (response.length <= maxLength) {
            return listOf(response)
        }
        
        val messages = mutableListOf<String>()
        val lines = response.split("\n")
        var current = StringBuilder()
        
        for (line in lines) {
            if (current.length + line.length + 1 > maxLength) {
                if (current.isNotEmpty()) {
                    messages.add(current.toString())
                    current = StringBuilder()
                }
                // 如果单行超过限制，强制拆分
                if (line.length > maxLength) {
                    messages.add(line.take(maxLength))
                } else {
                    current.append(line)
                }
            } else {
                if (current.isNotEmpty()) {
                    current.append("\n")
                }
                current.append(line)
            }
        }
        
        if (current.isNotEmpty()) {
            messages.add(current.toString())
        }
        
        return messages
    }
}
