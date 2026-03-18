package com.dragon.agent.skills.clawhub

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query
import javax.inject.Inject
import javax.inject.Singleton

/**
 * ClawHub API client for skill marketplace
 * Note: ClawHub 目前没有公开 REST API，这里使用模拟数据作为临时方案
 */
@Singleton
class ClawHubClient @Inject constructor() {
    
    companion object {
        // ClawHub API 不可用，使用模拟数据
        private val MOCK_SKILLS = listOf(
            ClawHubSkill(
                slug = "weather",
                name = "天气查询",
                version = "1.0.0",
                description = "获取天气预报和空气质量信息，支持全球城市",
                author = "community",
                homepage = "",
                downloads = 1000,
                stars = 50,
                tags = listOf("utilities", "weather"),
                emoji = "🌤️",
                updatedAt = "2026-03-01"
            ),
            ClawHubSkill(
                slug = "calculator",
                name = "计算器",
                version = "1.0.0",
                description = "科学计算器，支持数学表达式求值",
                author = "community",
                homepage = "",
                downloads = 800,
                stars = 30,
                tags = listOf("utilities", "math"),
                emoji = "🧮",
                updatedAt = "2026-03-01"
            ),
            ClawHubSkill(
                slug = "web-search",
                name = "网页搜索",
                version = "1.0.0",
                description = "互联网搜索工具，获取实时信息",
                author = "community",
                homepage = "",
                downloads = 1200,
                stars = 60,
                tags = listOf("utilities", "search"),
                emoji = "🔍",
                updatedAt = "2026-03-01"
            ),
            ClawHubSkill(
                slug = "translate",
                name = "翻译",
                version = "1.0.0",
                description = "多语言翻译工具",
                author = "community",
                homepage = "",
                downloads = 600,
                stars = 25,
                tags = listOf("utilities", "language"),
                emoji = "🌐",
                updatedAt = "2026-03-01"
            ),
            ClawHubSkill(
                slug = "schedule",
                name = "日程管理",
                version = "1.0.0",
                description = "管理日程和提醒",
                author = "community",
                homepage = "",
                downloads = 400,
                stars = 20,
                tags = listOf("productivity", "schedule"),
                emoji = "📅",
                updatedAt = "2026-03-01"
            )
        )
        
        private val MOCK_CATEGORIES = listOf(
            ClawHubCategory("utilities", "工具", "实用小工具", "🛠️", 5),
            ClawHubCategory("productivity", "效率", "提升效率", "⚡", 2),
            ClawHubCategory("communication", "通信", "通讯相关", "💬", 0),
            ClawHubCategory("automation", "自动化", "自动化任务", "🤖", 0)
        )
    }
    
    private val json = Json { 
        ignoreUnknownKeys = true 
        isLenient = true
    }
    
    /**
     * Search skills in marketplace
     * 使用模拟数据，因为 ClawHub API 暂不可用
     */
    suspend fun searchSkills(query: String, limit: Int = 20): Result<List<ClawHubSkill>> {
        return try {
            // 模拟搜索延迟
            kotlinx.coroutines.delay(300)
            
            // 本地过滤模拟数据
            val results = MOCK_SKILLS.filter { skill ->
                skill.name.contains(query, ignoreCase = true) ||
                skill.description.contains(query, ignoreCase = true) ||
                skill.tags.any { it.contains(query, ignoreCase = true) }
            }.take(limit)
            
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get skill details
     * 使用模拟数据
     */
    suspend fun getSkill(slug: String): Result<ClawHubSkill> {
        return try {
            kotlinx.coroutines.delay(200)
            val skill = MOCK_SKILLS.find { it.slug == slug }
            if (skill != null) {
                Result.success(skill)
            } else {
                Result.failure(Exception("Skill not found: $slug"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get skill README content
     * 返回默认 README
     */
    suspend fun getSkillReadme(slug: String, version: String = "latest"): Result<String> {
        return try {
            val skill = MOCK_SKILLS.find { it.slug == slug }
            if (skill != null) {
                Result.success(buildDefaultReadme(skill))
            } else {
                Result.failure(Exception("README not found"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Download skill package
     * 返回空数据（本地安装）
     */
    suspend fun downloadSkill(slug: String, version: String = "latest"): Result<ByteArray> {
        return Result.success(ByteArray(0))
    }
    
    /**
     * Get featured skills
     */
    suspend fun getFeaturedSkills(): Result<List<ClawHubSkill>> {
        return try {
            kotlinx.coroutines.delay(300)
            Result.success(MOCK_SKILLS.take(5))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Get skill categories
     */
    suspend fun getCategories(): Result<List<ClawHubCategory>> {
        return try {
            kotlinx.coroutines.delay(200)
            Result.success(MOCK_CATEGORIES)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * 构建默认 README
     */
    private fun buildDefaultReadme(skill: ClawHubSkill): String {
        return """# ${skill.name}

${skill.description}

## 信息

- 版本: ${skill.version}
- 作者: ${skill.author}
- 标签: ${skill.tags.joinToString(", ")}

## 功能

TODO: 添加技能功能说明

## 使用

TODO: 添加使用说明
"""
    }
}

/**
 * API 接口（已废弃 - ClawHub 暂无公开 API）
 */
@Deprecated("ClawHub API 暂不可用")
private interface ClawHubApi

@Serializable
data class ClawHubSearchResponse(
    val skills: List<ClawHubSkill> = emptyList()
)

@Serializable
data class ClawHubCategoriesResponse(
    val categories: List<ClawHubCategory> = emptyList()
)

@Serializable
data class ClawHubSkill(
    val slug: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String = "",
    val homepage: String = "",
    val downloads: Int = 0,
    val stars: Int = 0,
    val tags: List<String> = emptyList(),
    val emoji: String = "",
    val updatedAt: String = ""
)

@Serializable
data class ClawHubCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val emoji: String = "",
    val skillCount: Int = 0
)
