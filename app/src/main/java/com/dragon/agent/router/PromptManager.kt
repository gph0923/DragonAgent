package com.dragon.agent.router

import com.dragon.agent.data.local.SettingsManager
import com.dragon.agent.data.local.UserSettings
import com.dragon.agent.skills.SkillManager
import com.dragon.agent.tools.ToolManager
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 提示词管理器
 * 负责动态构建系统提示词
 */
@Singleton
class PromptManager @Inject constructor(
    private val settingsManager: SettingsManager,
    private val toolManager: ToolManager,
    private val skillManager: SkillManager,
    private val agentRouter: AgentRouter
) {

    companion object {
        // 默认系统提示词模板
        const val DEFAULT_PROMPT_TEMPLATE = """
你是一个智能 AI 助手，名字叫 DragonAgent。

## 你的能力
1. 对话交流
2. 使用工具完成特定任务
3. 使用技能增强功能

## 可用工具
{TOOLS}

## 可用技能
{SKILLS}

## 指令
1. 根据用户输入判断是否需要使用工具或技能
2. 如果需要使用工具，使用 function calling
3. 回答要简洁准确
4. 如果不确定用户意图，可以询问确认

## 当前对话
{CONTEXT}
"""
    }

    /**
     * 构建完整的系统提示词
     */
    suspend fun buildSystemPrompt(
        userInput: String = "",
        contextMessages: List<String> = emptyList()
    ): String {
        // 获取基础设置
        val settings = settingsManager.settings.first()
        
        // 获取可用工具和技能描述
        val toolsDesc = agentRouter.getAvailableToolsDescription()
        val skillsDesc = agentRouter.getAvailableSkillsDescription()
        
        // 构建上下文
        val context = buildContext(contextMessages)
        
        // 替换模板变量
        var prompt = settings.systemPrompt.ifBlank { DEFAULT_PROMPT_TEMPLATE }
        
        prompt = prompt
            .replace("{TOOLS}", toolsDesc.ifBlank { "无" })
            .replace("{SKILLS}", skillsDesc.ifBlank { "无" })
            .replace("{CONTEXT}", context)
        
        return prompt
    }

    /**
     * 根据意图构建提示词
     */
    suspend fun buildPromptForIntent(
        routeDecision: RouteDecision,
        contextMessages: List<String> = emptyList()
    ): String {
        val basePrompt = buildSystemPrompt(contextMessages = contextMessages)
        
        // 添加意图特定指令
        val intentInstruction = when (routeDecision.intent) {
            com.dragon.agent.router.Intent.TOOL_USE -> 
                "\n\n## 当前任务\n用户需要使用工具: ${routeDecision.selectedTools.joinToString(", ")}\n"
            com.dragon.agent.router.Intent.SKILL_USE ->
                "\n\n## 当前任务\n用户需要使用技能: ${routeDecision.selectedSkills.joinToString(", ")}\n"
            com.dragon.agent.router.Intent.TASK_EXECUTION ->
                "\n\n## 当前任务\n请帮助用户完成任务。\n"
            com.dragon.agent.router.Intent.QUESTION_ANSWER ->
                "\n\n## 当前任务\n请准确回答用户的问题。\n"
            else -> ""
        }
        
        return basePrompt + intentInstruction
    }

    /**
     * 构建对话上下文
     */
    private fun buildContext(messages: List<String>): String {
        if (messages.isEmpty()) {
            return "无历史对话"
        }
        
        val recentMessages = messages.takeLast(10) // 只取最近10条
        return recentMessages.joinToString("\n") { msg ->
            "[$msg]"
        }
    }

    /**
     * 获取快速回复提示（用于意图识别后）
     */
    fun getQuickReplyPrompt(userInput: String): String {
        return """
判断用户输入的意图，只需要回复一个词：
- 如果是问题：回复"问答"
- 如果是需要工具：回复"工具"
- 如果是需要技能：回复"技能"
- 如果是一般聊天：回复"聊天"
- 如果是任务执行：回复"任务"

用户输入: $userInput
        """.trimIndent()
    }

    /**
     * 获取技能触发提示
     */
    fun getSkillTriggerPrompt(): String {
        val skills = skillManager.getEnabledSkills()
        
        if (skills.isEmpty()) {
            return "当前没有加载任何技能"
        }
        
        val skillTriggers = skills.flatMap { skill ->
            skill.manifest.triggers.map { trigger ->
                "${skill.manifest.name}: $trigger"
            }
        }
        
        return """
检查用户输入是否触发以下技能：
${skillTriggers.joinToString("\n")}

如果触发，返回技能名称；否则返回"无"
        """.trimIndent()
    }
}
