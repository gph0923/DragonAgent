package com.dragon.agent.router

import javax.inject.Inject
import javax.inject.Singleton

/**
 * 意图检测器
 * 简单的规则-based 意图识别
 */
@Singleton
class IntentDetector @Inject constructor() {

    /**
     * 意图模式定义
     */
    data class IntentPattern(
        val intent: Intent,
        val keywords: List<String>,
        val required: Boolean = false
    )

    /**
     * 检测意图
     */
    fun detect(userInput: String): Intent {
        val input = userInput.lowercase().trim()
        
        return when {
            // 问题检测
            isQuestion(input) -> Intent.QUESTION_ANSWER
            
            // 工具使用检测
            isToolUse(input) -> Intent.TOOL_USE
            
            // 技能使用检测
            isSkillUse(input) -> Intent.SKILL_USE
            
            // 任务执行检测
            isTaskExecution(input) -> Intent.TASK_EXECUTION
            
            // 默认一般对话
            else -> Intent.GENERAL_CHAT
        }
    }

    /**
     * 检测是否为问题
     */
    private fun isQuestion(input: String): Boolean {
        val questionIndicators = listOf(
            "?", "？", "吗", "呢", "是不是", "有没有", 
            "能不能", "会不会", "是什么", "怎么做",
            "为什么", "如何", "怎样", "多少", "几点"
        )
        
        // 以问号结尾
        if (input.endsWith("?") || input.endsWith("？")) {
            return true
        }
        
        // 包含疑问词
        return questionIndicators.any { input.contains(it) }
    }

    /**
     * 检测是否需要使用工具
     */
    private fun isToolUse(input: String): Boolean {
        val toolKeywords = listOf(
            "搜索", "查询", "查一下", "天气", "计算", "算",
            "换算", "翻译", "获取", "下载", "上传",
            "提醒", "定时", "日程", "发送", "发送邮件",
            "打电话", "发短信", "打开", "读取", "写"
        )
        
        return toolKeywords.any { input.contains(it) }
    }

    /**
     * 检测是否需要使用技能
     */
    private fun isSkillUse(input: String): Boolean {
        val skillKeywords = listOf(
            "github", "gitlab", "代码", "编程", "写代码",
            "项目管理", "jira", "confluence",
            "邮件", "日历", "文档", "wiki",
            "slack", "discord", "telegram",
            "数据库", "sql", "mysql", "postgresql"
        )
        
        return skillKeywords.any { input.contains(it) }
    }

    /**
     * 检测是否为任务执行
     */
    private fun isTaskExecution(input: String): Boolean {
        val taskKeywords = listOf(
            "帮我", "请", "能不能", "可以帮我",
            "创建", "生成", "写", "制作", "做",
            "整理", "汇总", "分析", "统计"
        )
        
        return taskKeywords.any { input.contains(it) }
    }

    /**
     * 获取意图的置信度
     */
    fun getConfidence(userInput: String, intent: Intent): Float {
        val input = userInput.lowercase()
        var confidence = 0.5f
        
        // 根据匹配度调整置信度
        when (intent) {
            Intent.QUESTION_ANSWER -> {
                if (input.endsWith("?") || input.endsWith("？")) confidence += 0.3f
                if (listOf("吗", "呢", "是不是").any { input.contains(it) }) confidence += 0.2f
            }
            Intent.TOOL_USE -> {
                if (listOf("搜索", "天气", "计算").any { input.contains(it) }) confidence += 0.4f
            }
            Intent.SKILL_USE -> {
                if (listOf("github", "代码", "编程").any { input.contains(it) }) confidence += 0.4f
            }
            Intent.TASK_EXECUTION -> {
                if (listOf("帮我", "请帮我", "能不能帮我").any { input.contains(it) }) confidence += 0.3f
            }
            Intent.GENERAL_CHAT -> {
                confidence = 0.6f
            }
            Intent.UNKNOWN -> {
                confidence = 0.1f
            }
        }
        
        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 意图转中文描述
     */
    fun intentToChinese(intent: Intent): String {
        return when (intent) {
            Intent.GENERAL_CHAT -> "一般对话"
            Intent.TOOL_USE -> "使用工具"
            Intent.SKILL_USE -> "使用技能"
            Intent.QUESTION_ANSWER -> "问答"
            Intent.TASK_EXECUTION -> "任务执行"
            Intent.UNKNOWN -> "未知"
        }
    }
}
