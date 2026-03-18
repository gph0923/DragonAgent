package com.dragon.agent.router

import com.dragon.agent.tools.ToolDefinition
import com.dragon.agent.tools.ToolManager
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具选择器
 * 根据用户输入和上下文自动选择合适的工具
 */
@Singleton
class ToolSelector @Inject constructor(
    private val toolManager: ToolManager
) {

    /**
     * 关键词到工具的映射
     */
    private val keywordToolMap = mapOf(
        // 天气相关
        listOf("天气", "温度", "下雨", "晴天", "阴天") to "get_weather",
        
        // 搜索相关
        listOf("搜索", "查询", "找", "搜", "查一下") to "web_search",
        
        // 计算相关
        listOf("计算", "算一下", "等于", "加", "减", "乘", "除", "+", "-", "*", "/") to "calculator",
        
        // 文件相关
        listOf("读取", "打开", "查看文件", "写文件") to "file_read",
        
        // HTTP 相关
        listOf("请求", "api", "http", "获取数据") to "http_request",
        
        // 提醒相关
        listOf("提醒", "闹钟", "定时") to "reminder",
        
        // 翻译相关
        listOf("翻译", "英文", "中文", "语言") to "translate"
    )

    /**
     * 基于关键词选择工具
     */
    fun selectByKeyword(userInput: String): List<String> {
        val lowerInput = userInput.lowercase()
        val selected = mutableListOf<String>()
        
        for ((keywords, toolName) in keywordToolMap) {
            if (keywords.any { lowerInput.contains(it) }) {
                selected.add(toolName)
            }
        }
        
        return selected.distinct()
    }

    /**
     * 基于意图选择工具
     */
    fun selectByIntent(routeDecision: RouteDecision): List<String> {
        return routeDecision.selectedTools
    }

    /**
     * 自动选择工具（结合关键词和意图）
     */
    fun selectTools(userInput: String, routeDecision: RouteDecision? = null): List<ToolDefinition> {
        val selectedNames = mutableSetOf<String>()
        
        // 1. 关键词匹配
        selectedNames.addAll(selectByKeyword(userInput))
        
        // 2. 意图驱动（如果有）
        routeDecision?.let {
            if (it.intent == Intent.TOOL_USE || it.intent == Intent.TASK_EXECUTION) {
                selectedNames.addAll(it.selectedTools)
            }
        }
        
        // 3. 如果没有匹配到但意图是工具使用，选择所有工具
        if (selectedNames.isEmpty() && routeDecision?.intent == Intent.TOOL_USE) {
            selectedNames.addAll(toolManager.getTools().map { it.name })
        }
        
        // 获取工具定义
        val allTools = toolManager.getToolDefinitions()
        return allTools.filter { it.name in selectedNames }
    }

    /**
     * 检查是否需要工具
     */
    fun needsTool(userInput: String): Boolean {
        val toolKeywords = keywordToolMap.keys.flatten()
        return toolKeywords.any { userInput.lowercase().contains(it) }
    }

    /**
     * 获取工具使用建议
     */
    fun getSuggestion(userInput: String): String? {
        val selected = selectByKeyword(userInput)
        
        return when {
            selected.isEmpty() -> null
            selected.size == 1 -> "建议使用工具: ${selected[0]}"
            else -> "建议使用工具: ${selected.joinToString(", ")}"
        }
    }
}
