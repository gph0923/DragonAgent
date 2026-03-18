package com.dragon.agent.skills.market

import android.content.Context
import com.dragon.agent.skills.SkillManifest
import com.dragon.agent.skills.SkillParser
import com.dragon.agent.skills.clawhub.ClawHubClient
import com.dragon.agent.skills.clawhub.ClawHubSkill
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipInputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 技能市场服务
 * 负责从 ClawHub 下载技能、管理本地技能
 */
@Singleton
class SkillMarketService @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clawHubClient: ClawHubClient
) {
    private val parser = SkillParser()
    
    // 本地技能目录
    private val localSkillsDir: File
        get() = File(context.filesDir, "skills").also { it.mkdirs() }

    /**
     * 从 ClawHub 搜索技能
     */
    suspend fun searchSkills(query: String): Result<List<MarketSkill>> = withContext(Dispatchers.IO) {
        clawHubClient.searchSkills(query).map { skills ->
            skills.map { it.toMarketSkill() }
        }
    }

    /**
     * 获取推荐技能
     */
    suspend fun getFeaturedSkills(): Result<List<MarketSkill>> = withContext(Dispatchers.IO) {
        clawHubClient.getFeaturedSkills().map { skills ->
            skills.map { it.toMarketSkill() }
        }
    }

    /**
     * 获取技能分类
     */
    suspend fun getCategories(): Result<List<SkillCategory>> = withContext(Dispatchers.IO) {
        clawHubClient.getCategories().map { categories ->
            categories.map { category ->
                SkillCategory(
                    id = category.id,
                    name = category.name,
                    description = category.description,
                    emoji = category.emoji,
                    skillCount = category.skillCount
                )
            }
        }
    }

    /**
     * 安装技能从 ClawHub
     */
    suspend fun installFromClawHub(slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        // 1. 获取技能详情
        val skillInfo = clawHubClient.getSkill(slug).getOrElse {
            return@withContext Result.failure(it)
        }

        // 2. 获取 SKILL.md
        val readmeResult = clawHubClient.getSkillReadme(slug)
        val skillContent = readmeResult.getOrNull() ?: buildDefaultSkillMd(skillInfo)

        // 3. 创建技能目录
        val skillDir = File(localSkillsDir, slug).also { it.mkdirs() }

        // 4. 写入 SKILL.md
        File(skillDir, "SKILL.md").writeText(skillContent)

        // 5. 下载额外文件（如果有）
        downloadSkillFiles(slug, skillDir)

        Result.success(Unit)
    }

    /**
     * 从本地安装技能 (ZIP 文件)
     */
    suspend fun installFromZip(zipFilePath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val zipFile = File(zipFilePath)
            if (!zipFile.exists()) {
                return@withContext Result.failure(Exception("文件不存在"))
            }

            // 解析 ZIP
            val tempDir = File(context.cacheDir, "temp_skill").also { it.deleteRecursively(); it.mkdirs() }

            ZipInputStream(zipFile.inputStream()).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val file = File(tempDir, entry.name)
                    if (entry.isDirectory) {
                        file.mkdirs()
                    } else {
                        file.parentFile?.mkdirs()
                        file.outputStream().use { fos ->
                            zis.copyTo(fos)
                        }
                    }
                    entry = zis.nextEntry
                }
            }

            // 检查 SKILL.md
            val skillMd = File(tempDir, "SKILL.md")
            if (!skillMd.exists()) {
                tempDir.deleteRecursively()
                return@withContext Result.failure(Exception("无效的技能包：缺少 SKILL.md"))
            }

            // 解析 manifest
            val manifest = parser.parseFile(skillMd).getOrElse {
                return@withContext Result.failure(it)
            }

            // 移动到目标目录
            val targetDir = File(localSkillsDir, manifest.slug)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            tempDir.renameTo(targetDir)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 从本地目录安装技能
     */
    suspend fun installFromDirectory(dirPath: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val sourceDir = File(dirPath)
            if (!sourceDir.exists() || !sourceDir.isDirectory) {
                return@withContext Result.failure(Exception("目录不存在"))
            }

            val skillMd = File(sourceDir, "SKILL.md")
            if (!skillMd.exists()) {
                return@withContext Result.failure(Exception("目录中缺少 SKILL.md"))
            }

            val manifest = parser.parseFile(skillMd).getOrElse {
                return@withContext Result.failure(it)
            }

            // 复制到目标目录
            val targetDir = File(localSkillsDir, manifest.slug)
            if (targetDir.exists()) {
                targetDir.deleteRecursively()
            }
            sourceDir.copyRecursively(targetDir, overwrite = true)

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 卸载技能
     */
    suspend fun uninstallSkill(slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val skillDir = File(localSkillsDir, slug)
            if (skillDir.exists()) {
                skillDir.deleteRecursively()
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * 获取已安装的本地技能
     */
    suspend fun getInstalledSkills(): List<LocalSkill> = withContext(Dispatchers.IO) {
        val skills = mutableListOf<LocalSkill>()
        
        localSkillsDir.listFiles()?.forEach { dir ->
            if (dir.isDirectory) {
                val skillMd = File(dir, "SKILL.md")
                if (skillMd.exists()) {
                    parser.parseFile(skillMd).onSuccess { manifest ->
                        skills.add(LocalSkill(
                            slug = manifest.slug,
                            name = manifest.name,
                            version = manifest.version,
                            description = manifest.description,
                            path = dir.absolutePath,
                            source = "local"
                        ))
                    }
                }
            }
        }
        
        skills
    }

    /**
     * 检查技能是否已安装
     */
    fun isInstalled(slug: String): Boolean {
        return File(localSkillsDir, slug).exists()
    }

    /**
     * 下载技能额外文件
     */
    private suspend fun downloadSkillFiles(slug: String, targetDir: File) {
        // 可扩展：下载技能所需的额外资源
    }

    /**
     * 构建默认 SKILL.md
     */
    private fun buildDefaultSkillMd(skill: com.dragon.agent.skills.clawhub.ClawHubSkill): String {
        return """---
name: ${skill.name}
slug: ${skill.slug}
version: ${skill.version}
homepage: ${skill.homepage}
description: ${skill.description}
---

${skill.description}

## 安装

此技能来自 ClawHub 市场。
"""
    }

    private fun ClawHubSkill.toMarketSkill() = MarketSkill(
        slug = slug,
        name = name,
        version = version,
        description = description,
        author = author,
        emoji = emoji,
        tags = tags,
        downloads = downloads,
        stars = stars,
        source = "clawhub"
    )
}

/**
 * 市场技能
 */
data class MarketSkill(
    val slug: String,
    val name: String,
    val version: String,
    val description: String,
    val author: String = "",
    val emoji: String = "📦",
    val tags: List<String> = emptyList(),
    val downloads: Int = 0,
    val stars: Int = 0,
    val source: String = "clawhub"
)

/**
 * 本地技能
 */
data class LocalSkill(
    val slug: String,
    val name: String,
    val version: String,
    val description: String,
    val path: String,
    val source: String = "local"
)

/**
 * 技能分类
 */
data class SkillCategory(
    val id: String,
    val name: String,
    val description: String = "",
    val emoji: String = "",
    val skillCount: Int = 0
)
