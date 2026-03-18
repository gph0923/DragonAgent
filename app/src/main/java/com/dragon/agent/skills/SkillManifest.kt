package com.dragon.agent.skills

import kotlinx.serialization.Serializable

/**
 * Skill metadata parsed from SKILL.md
 */
@Serializable
data class SkillManifest(
    val name: String,
    val slug: String,
    val version: String,
    val homepage: String = "",
    val description: String,
    val metadata: SkillMetadata = SkillMetadata(),
    val triggers: List<SkillTrigger> = emptyList(),  // 支持 keyword/regex/intent
    val tools: List<SkillTool> = emptyList(),
    val settings: List<SkillSetting> = emptyList(),
    val runtime: SkillRuntimeConfig? = null,
    val dependencies: List<String> = emptyList()
)

/**
 * 触发条件 - 与 OpenClaw 一致
 */
@Serializable
data class SkillTrigger(
    val keyword: List<String>? = null,  // 关键词触发
    val regex: String? = null,            // 正则触发
    val intent: String? = null           // 意图触发
) {
    /**
     * 检查输入是否匹配此触发器
     */
    fun matches(input: String): Boolean {
        val lowerInput = input.lowercase()
        
        // 关键词匹配
        keyword?.forEach { kw ->
            if (lowerInput.contains(kw.lowercase())) {
                return true
            }
        }
        
        // 正则匹配
        regex?.let { pattern ->
            try {
                if (Regex(pattern, RegexOption.IGNORE_CASE).containsMatchIn(input)) {
                    return true
                }
            } catch (e: Exception) {
                // 正则表达式无效，跳过
            }
        }
        
        // 意图匹配（需要外部意图识别器）
        // 这里暂时返回 false，实际由 AgentRouter 处理
        intent?.let {
            // 可以扩展为调用 IntentDetector
        }
        
        return false
    }
}

@Serializable
data class SkillRuntimeConfig(
    val type: String = "inline",  // python3, nodejs, inline
    val version: String? = null,
    val timeout: Int = 30
)

@Serializable
data class SkillMetadata(
    val requires: SkillRequirements = SkillRequirements(),
    val os: List<String> = emptyList(),
    val emoji: String = ""
)

@Serializable
data class SkillRequirements(
    val bins: List<String> = emptyList(),
    val env: Map<String, String> = emptyMap()
)

@Serializable
data class SkillTool(
    val name: String,
    val description: String,
    val parameters: String = "" // JSON Schema
)

@Serializable
data class SkillSetting(
    val key: String,
    val name: String,
    val type: String, // text, number, boolean, select
    val default: String = "",
    val options: List<String> = emptyList(),
    val required: Boolean = false
)

/**
 * Installed skill with local state
 */
data class InstalledSkill(
    val manifest: SkillManifest,
    val source: SkillSource,
    val installedAt: Long,
    val enabled: Boolean = true,
    val settings: Map<String, String> = emptyMap()
)

enum class SkillSource {
    BUILTIN,
    LOCAL,
    CLAWHUB
}
