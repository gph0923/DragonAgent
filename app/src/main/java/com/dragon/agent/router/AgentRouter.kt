package com.dragon.agent.router

import com.dragon.agent.llm.LLMProvider
import com.dragon.agent.skills.SkillManager
import com.dragon.agent.skills.SkillTrigger
import com.dragon.agent.tools.ToolManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 路由决策
 */
data class RouteDecision(
    val intent: Intent,
    val confidence: Float,
    val selectedTools: List<String> = emptyList(),
    val selectedSkills: List<String> = emptyList(),
    val triggeredSkills: List<String> = emptyList(),  // 通过触发器激活的技能
    val suggestedPrompt: String = "",
    val context: Map<String, Any> = emptyMap()
)

/**
 * 意图类型
 */
enum class Intent {
    GENERAL_CHAT,      // 一般对话
    TOOL_USE,         // 需要使用工具
    SKILL_USE,        // 需要使用技能 (通过触发器)
    QUESTION_ANSWER,  // 问答
    TASK_EXECUTION,   // 任务执行
    UNKNOWN           // 未知
}

/**
 * Agent Router - 意图识别 + 路由 + 技能触发
 * 与 OpenClaw 一致：通过 SKILL.md 中的 triggers (keyword/regex/intent) 自动匹配技能
 */
@Singleton
class AgentRouter @Inject constructor(
    private val llmProvider: LLMProvider,
    private val toolManager: ToolManager,
    private val skillManager: SkillManager
) {

    /**
     * 分析用户输入并做出路由决策
     * 与 OpenClaw 一致：优先通过技能触发器匹配
     */
    suspend fun route(userInput: String): RouteDecision = withContext(Dispatchers.Default) {
        val lowerInput = userInput.lowercase()
        
        // 1. 技能触发器匹配 (与 OpenClaw 一致)
        val triggeredSkills = matchSkillTriggers(userInput)
        
        // 2. 如果匹配到技能，标记为 SKILL_USE
        val intent = if (triggeredSkills.isNotEmpty()) {
            Intent.SKILL_USE
        } else {
            // 否则进行意图识别
            detectIntent(lowerInput)
        }
        
        // 3. 选择工具
        val tools = selectTools(lowerInput, intent, triggeredSkills)
        
        // 4. 构建提示词
        val prompt = buildPrompt(intent, tools, triggeredSkills)
        
        RouteDecision(
            intent = intent,
            confidence = calculateConfidence(lowerInput, triggeredSkills),
            selectedTools = tools,
            selectedSkills = triggeredSkills,
            triggeredSkills = triggeredSkills,
            suggestedPrompt = prompt
        )
    }

    /**
     * 技能触发器匹配 - 与 OpenClaw 一致
     * 匹配 SKILL.md 中定义的 triggers
     */
    private fun matchSkillTriggers(userInput: String): List<String> {
        val triggered = mutableListOf<String>()
        
        // 获取所有启用的技能
        val skills = skillManager.getEnabledSkills()
        
        for (skill in skills) {
            for (trigger in skill.manifest.triggers) {
                if (trigger.matches(userInput)) {
                    triggered.add(skill.manifest.slug)
                    println("[AgentRouter] Skill '${skill.manifest.slug}' triggered by: $trigger")
                    break // 每个技能只触发一次
                }
            }
        }
        
        return triggered
    }

    /**
     * 意图检测
     */
    private fun detectIntent(input: String): Intent {
        val toolKeywords = listOf(
            "搜索", "查询", "天气", "计算", "换算", "翻译", 
            "提醒", "日程", "查", "找", "获取"
        )
        
        val taskKeywords = listOf(
            "帮我", "请", "能不能", "可以帮我", "做",
            "创建", "生成", "写", "制作"
        )
        
        return when {
            toolKeywords.any { input.contains(it) } -> Intent.TOOL_USE
            taskKeywords.any { input.contains(it) } -> Intent.TASK_EXECUTION
            input.endsWith("?") || input.endsWith("？") -> Intent.QUESTION_ANSWER
            input.length < 20 -> Intent.GENERAL_CHAT
            else -> Intent.GENERAL_CHAT
        }
    }

    /**
     * 工具选择
     */
    private fun selectTools(input: String, intent: Intent, triggeredSkills: List<String>): List<String> {
        val selected = mutableListOf<String>()
        
        // 1. 如果有触发的技能，从技能中获取工具
        if (triggeredSkills.isNotEmpty()) {
            val skills = skillManager.getEnabledSkills()
            for (skillSlug in triggeredSkills) {
                val skill = skills.find { it.manifest.slug == skillSlug }
                skill?.manifest?.tools?.forEach { tool ->
                    selected.add(tool.name)
                }
            }
            if (selected.isNotEmpty()) {
                return selected
            }
        }
        
        // 2. 关键词匹配工具
        when {
            input.contains("天气") -> selected.add("get_weather")
            input.contains("搜索") || input.contains("查一下") -> selected.add("web_search")
            input.contains("计算") || input.contains("算") -> selected.add("calculator")
            input.contains("文件") -> selected.add("file_read")
            input.contains("网络") || input.contains("请求") -> selected.add("http_request")
        }
        
        // 3. 如果是工具意图但没有匹配到，自动选择所有可用工具
        if (intent == Intent.TOOL_USE && selected.isEmpty()) {
            selected.addAll(toolManager.getTools().map { it.name })
        }
        
        return selected
    }

    /**
     * 构建提示词 - 与 OpenClaw 一致
     */
    private fun buildPrompt(intent: Intent, tools: List<String>, skills: List<String>): String {
        val sb = StringBuilder()
        
        when (intent) {
            Intent.SKILL_USE -> {
                sb.append("用户输入触发了技能。\n")
                if (skills.isNotEmpty()) {
                    sb.append("触发的技能: ${skills.joinToString(", ")}\n")
                }
            }
            Intent.TOOL_USE -> sb.append("用户需要使用工具来完成任务。\n")
            Intent.TASK_EXECUTION -> sb.append("用户需要执行一个任务。\n")
            Intent.QUESTION_ANSWER -> sb.append("用户提出了一个问题，请准确回答。\n")
            Intent.GENERAL_CHAT -> sb.append("这是一般对话，请友好回复。\n")
            Intent.UNKNOWN -> sb.append("请根据用户输入判断意图。\n")
        }
        
        if (tools.isNotEmpty()) {
            sb.append("\n可用工具: ${tools.joinToString(", ")}\n")
        }
        
        return sb.toString()
    }

    /**
     * 计算置信度
     */
    private fun calculateConfidence(input: String, triggeredSkills: List<String>): Float {
        var confidence = 0.5f
        
        // 如果触发了技能，置信度更高
        if (triggeredSkills.isNotEmpty()) {
            confidence += 0.3f
        }
        
        // 关键词匹配增加置信度
        if (input.contains("天气") || input.contains("搜索")) confidence += 0.1f
        if (input.contains("帮") || input.contains("请")) confidence += 0.1f
        if (input.length in 5..50) confidence += 0.1f
        
        return confidence.coerceIn(0f, 1f)
    }

    /**
     * 获取所有可用工具描述（供 LLM 使用）
     */
    fun getAvailableToolsDescription(): String {
        val tools = toolManager.getTools()
        return tools.joinToString("\n") { tool ->
            "- ${tool.name}: ${tool.description}"
        }
    }

    /**
     * 获取所有技能描述
     */
    fun getAvailableSkillsDescription(): String {
        val skills = skillManager.getEnabledSkills()
        
        return if (skills.isEmpty()) {
            "无"
        } else {
            skills.joinToString("\n") { skill ->
                val triggers = skill.manifest.triggers.joinToString(", ") { trigger ->
                    when {
                        !trigger.keyword.isNullOrEmpty() -> "关键词: ${trigger.keyword}"
                        !trigger.regex.isNullOrEmpty() -> "正则: ${trigger.regex}"
                        !trigger.intent.isNullOrEmpty() -> "意图: ${trigger.intent}"
                        else -> ""
                    }
                }
                "- ${skill.manifest.name}: ${skill.manifest.description} (触发: $triggers)"
            }
        }
    }
    
    /**
     * 手动触发技能（用于测试或手动激活）
     */
    fun triggerSkill(skillSlug: String): Boolean {
        val skill = skillManager.getEnabledSkills().find { it.manifest.slug == skillSlug }
        return skill != null
    }
}
