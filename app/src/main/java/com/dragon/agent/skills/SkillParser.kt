package com.dragon.agent.skills

import kotlinx.serialization.json.Json
import java.io.File

/**
 * Parser for SKILL.md format (与 OpenClaw 一致)
 * Parses YAML frontmatter and extracts skill metadata, triggers, tools, and settings
 */
class SkillParser {
    
    companion object {
        private val json = Json { ignoreUnknownKeys = true }
        
        // 匹配 YAML frontmatter
        private val FRONTMATTER_REGEX = Regex("^---\n([\\s\\S]*?)\n---", RegexOption.MULTILINE)
        
        // 匹配触发器列表项
        private val TRIGGER_ITEM_REGEX = Regex("""^\s*-\s*(.+?)$""", RegexOption.MULTILINE)
        
        // 匹配 key: value
        private val KEY_VALUE_REGEX = Regex("""^\s*(\w+):\s*(.*)$""")
    }
    
    /**
     * Parse SKILL.md content into SkillManifest
     */
    fun parse(content: String): Result<SkillManifest> {
        return try {
            val frontmatterMatch = FRONTMATTER_REGEX.find(content)
                ?: return Result.failure(IllegalArgumentException("No frontmatter found"))
            
            val yamlContent = frontmatterMatch.groupValues[1]
            val manifest = parseYamlFrontmatter(yamlContent)
            
            // Parse triggers from content (与 OpenClaw 一致)
            val triggers = parseTriggers(content)
            
            // Parse tools section if present
            val tools = parseToolsSection(content)
            
            Result.success(manifest.copy(
                triggers = triggers,
                tools = tools
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Parse SKILL.md file
     */
    fun parseFile(file: File): Result<SkillManifest> {
        return parse(file.readText())
    }
    
    /**
     * Parse YAML frontmatter (简化版解析器)
     */
    private fun parseYamlFrontmatter(yaml: String): SkillManifest {
        var name = ""
        var slug = ""
        var version = ""
        var homepage = ""
        var description = ""
        var emoji = ""
        val requiresBins = mutableListOf<String>()
        val os = mutableListOf<String>()
        
        val lines = yaml.lines()
        var currentKey = ""
        var currentValue = StringBuilder()
        
        fun flushValue() {
            if (currentKey.isNotEmpty()) {
                val value = currentValue.toString().trim()
                when (currentKey) {
                    "name" -> name = value
                    "slug" -> slug = value
                    "version" -> version = value
                    "homepage" -> homepage = value
                    "description" -> description = value
                    "requires" -> {} // handled in metadata
                    "bins" -> requiresBins.addAll(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    "os" -> os.addAll(value.split(",").map { it.trim() }.filter { it.isNotEmpty() })
                    "emoji" -> emoji = value
                }
                currentValue.clear()
            }
        }
        
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("- ") && currentKey == "requires") {
                requiresBins.add(trimmed.removePrefix("- ").trim())
            } else if (trimmed.contains(":") && !trimmed.startsWith(" ")) {
                flushValue()
                val parts = trimmed.split(":", limit = 2)
                currentKey = parts[0].trim()
                currentValue = StringBuilder(parts.getOrElse(1) { "" }.trim())
            } else if (trimmed.startsWith("- ") && currentKey != "requires") {
                val item = trimmed.removePrefix("- ").trim()
                when (currentKey) {
                    "os" -> os.add(item)
                }
            } else {
                currentValue.append(" ").append(trimmed)
            }
        }
        flushValue()
        
        if (slug.isEmpty() && name.isNotEmpty()) {
            slug = name.lowercase().replace(Regex("[^a-z0-9]"), "-")
        }
        
        return SkillManifest(
            name = name,
            slug = slug,
            version = version.ifEmpty { "1.0.0" },
            homepage = homepage,
            description = description,
            metadata = SkillMetadata(
                requires = SkillRequirements(bins = requiresBins),
                os = os,
                emoji = emoji
            )
        )
    }
    
    /**
     * 解析触发器 - 与 OpenClaw SKILL.md 一致
     * 支持格式:
     * triggers:
     *   - keyword: ["天气", "weather"]
     *   - intent: get_weather
     *   - regex: ".*天气.*"
     */
    private fun parseTriggers(content: String): List<SkillTrigger> {
        val triggers = mutableListOf<SkillTrigger>()
        
        // 查找 triggers 区块
        val triggersPattern = Regex(
            """(?i)triggers:\s*\n((?:[ \t]+.+\n)*)""",
            RegexOption.MULTILINE
        )
        val matchResult = triggersPattern.find(content) ?: return triggers
        
        val triggersContent = matchResult.groupValues[1]
        
        // 解析每个触发器
        var currentTrigger = mutableMapOf<String, Any>()
        
        for (line in triggersContent.lines()) {
            val trimmed = line.trim()
            
            when {
                // 新触发器开始 (- keyword / - intent / - regex)
                trimmed.startsWith("- ") -> {
                    // 保存上一个触发器
                    if (currentTrigger.isNotEmpty()) {
                        triggers.add(parseTrigger(currentTrigger))
                        currentTrigger = mutableMapOf()
                    }
                    
                    val item = trimmed.removePrefix("- ").trim()
                    
                    // 解析 - keyword: [...] 或 - intent: xxx 或 - regex: xxx
                    when {
                        item.startsWith("keyword:") -> {
                            val value = item.removePrefix("keyword:").trim()
                            currentTrigger["keyword"] = parseKeywordValue(value)
                        }
                        item.startsWith("intent:") -> {
                            val value = item.removePrefix("intent:").trim()
                            currentTrigger["intent"] = value
                        }
                        item.startsWith("regex:") -> {
                            val value = item.removePrefix("regex:").trim()
                            currentTrigger["regex"] = value
                        }
                        item.startsWith("keyword") -> {
                            // 多行 keyword 处理
                            currentTrigger["keyword_multiline"] = true
                        }
                    }
                }
                // 多行值 (如 keyword: [...] 跨多行)
                trimmed.startsWith("[") || trimmed.startsWith("\"") -> {
                    currentTrigger["keyword"] = parseKeywordValue(trimmed)
                }
                // key: value 格式
                trimmed.contains(":") -> {
                    val parts = trimmed.split(":", limit = 2)
                    val key = parts[0].trim()
                    val value = parts.getOrElse(1) { "" }.trim()
                    
                    when (key) {
                        "keyword" -> currentTrigger["keyword"] = parseKeywordValue(value)
                        "intent" -> currentTrigger["intent"] = value
                        "regex" -> currentTrigger["regex"] = value
                    }
                }
            }
        }
        
        // 保存最后一个触发器
        if (currentTrigger.isNotEmpty()) {
            triggers.add(parseTrigger(currentTrigger))
        }
        
        return triggers
    }
    
    /**
     * 解析 keyword 值 (支持 ["a", "b"] 格式)
     */
    private fun parseKeywordValue(value: String): List<String> {
        val result = mutableListOf<String>()
        
        // 清理值
        var cleaned = value.trim()
            .removePrefix("[")
            .removeSuffix("]")
            .replace("\"", "")
            .replace("'", "")
        
        if (cleaned.contains(",")) {
            result.addAll(cleaned.split(",").map { it.trim() }.filter { it.isNotEmpty() })
        } else if (cleaned.isNotEmpty()) {
            result.add(cleaned)
        }
        
        return result
    }
    
    /**
     * 将 Map 转换为 SkillTrigger
     */
    private fun parseTrigger(map: Map<String, Any>): SkillTrigger {
        return SkillTrigger(
            keyword = map["keyword"] as? List<String>,
            regex = map["regex"] as? String,
            intent = map["intent"] as? String
        )
    }
    
    /**
     * Parse tools section from content
     */
    private fun parseToolsSection(content: String): List<SkillTool> {
        val tools = mutableListOf<SkillTool>()
        
        val afterFrontmatter = FRONTMATTER_REGEX.replace(content, "")
        val toolsSection = Regex(
            """(?i)##\s*(?:Tools|Exposed Tools)\s*\n((?:.*\n)*?)##""",
            RegexOption.MULTILINE
        ).find(afterFrontmatter)
        
        if (toolsSection != null) {
            val sectionContent = toolsSection.groupValues[1]
            val toolDefPattern = Regex("""###\s+(.+?)\n([^\n]*(?:\n(?!\s*###|\n##|$))[^\n]*)*)""")
            toolDefPattern.findAll(sectionContent).forEach { match ->
                val toolName = match.groupValues[1].trim()
                val toolDesc = match.groupValues[2].lines().firstOrNull { it.isNotBlank() }?.trim() ?: ""
                tools.add(SkillTool(name = toolName, description = toolDesc))
            }
        }
        
        return tools
    }
}
