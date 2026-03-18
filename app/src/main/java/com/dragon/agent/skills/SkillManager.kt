package com.dragon.agent.skills

import android.content.Context
import com.dragon.agent.skills.clawhub.ClawHubClient
import com.dragon.agent.skills.clawhub.ClawHubSkill
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages skill lifecycle - install, uninstall, enable, disable
 */
@Singleton
class SkillManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val clawHubClient: ClawHubClient
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        prettyPrint = true
    }
    
    private val parser = SkillParser()
    
    // Available runtimes
    private val runtimes = listOf(
        InlineSkillRuntime(),
        Python3Runtime(),
        NodeJSRuntime()
    )
    
    private val _skills = MutableStateFlow<List<InstalledSkill>>(emptyList())
    val skills: StateFlow<List<InstalledSkill>> = _skills.asStateFlow()
    
    private val skillsDir: File
        get() = File(context.filesDir!!, "skills").also { it.mkdirs() }
    
    private val builtinDir: File
        get() = File(context.assets, "skills")
    
    /**
     * Initialize skills - load builtins and installed skills
     */
    suspend fun initialize() = withContext(Dispatchers.IO) {
        val allSkills = mutableListOf<InstalledSkill>()
        
        // Load built-in skills from assets
        loadBuiltinSkills().onSuccess { allSkills.addAll(it) }
        
        // Load installed skills
        loadInstalledSkills().onSuccess { allSkills.addAll(it) }
        
        _skills.value = allSkills
    }
    
    /**
     * Load built-in skills from assets
     */
    private fun loadBuiltinSkills(): Result<List<InstalledSkill>> {
        return try {
            val skills = mutableListOf<InstalledSkill>()
            
            // Check if skills folder exists in assets
            val assetList = context.assets.list("skills") ?: return Result.success(emptyList())
            
            for (skillDir in assetList) {
                try {
                    val skillContent = context.assets.open("skills/$skillDir/SKILL.md")
                        .bufferedReader()
                        .use { it.readText() }
                    
                    parser.parse(skillContent).onSuccess { manifest ->
                        skills.add(InstalledSkill(
                            manifest = manifest,
                            source = SkillSource.BUILTIN,
                            installedAt = 0,
                            enabled = true
                        ))
                    }
                } catch (e: Exception) {
                    // Skip invalid skills
                }
            }
            
            Result.success(skills)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Load installed skills from disk
     */
    private fun loadInstalledSkills(): Result<List<InstalledSkill>> {
        return try {
            val skills = mutableListOf<InstalledSkill>()
            val metadataFile = File(skillsDir, "skills.json")
            
            if (metadataFile.exists()) {
                val metadata = json.decodeFromString<List<SkillStorageMetadata>>(metadataFile.readText())
                
                for (skillMeta in metadata) {
                    val skillDir = File(skillsDir, skillMeta.slug)
                    if (skillDir.exists()) {
                        val skillFile = File(skillDir, "SKILL.md")
                        if (skillFile.exists()) {
                            parser.parseFile(skillFile).onSuccess { manifest ->
                                skills.add(InstalledSkill(
                                    manifest = manifest,
                                    source = SkillSource.LOCAL,
                                    installedAt = skillMeta.installedAt,
                                    enabled = skillMeta.enabled,
                                    settings = skillMeta.settings
                                ))
                            }
                        }
                    }
                }
            }
            
            Result.success(skills)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Install a skill from local directory
     */
    suspend fun installLocalSkill(sourceDir: File): Result<InstalledSkill> = withContext(Dispatchers.IO) {
        val skillFile = File(sourceDir, "SKILL.md")
        if (!skillFile.exists()) {
            return@withContext Result.failure(IllegalArgumentException("No SKILL.md found"))
        }
        
        val manifest = parser.parseFile(skillFile)
            .getOrElse { return@withContext Result.failure(it) }
        
        // Copy skill to skills directory
        val targetDir = File(skillsDir, manifest.slug)
        sourceDir.copyRecursively(targetDir, overwrite = true)
        
        val installed = InstalledSkill(
            manifest = manifest,
            source = SkillSource.LOCAL,
            installedAt = System.currentTimeMillis(),
            enabled = true
        )
        
        saveSkillMetadata(installed)
        _skills.value = _skills.value + installed
        
        Result.success(installed)
    }
    
    /**
     * Install skill from ClawHub
     */
    suspend fun installFromClawHub(slug: String, version: String = "latest"): Result<InstalledSkill> = withContext(Dispatchers.IO) {
        // 获取技能详情
        val skillInfo = clawHubClient.getSkill(slug).getOrElse {
            return@withContext Result.failure(it)
        }
        
        // 获取 SKILL.md 内容
        val readmeResult = clawHubClient.getSkillReadme(slug, version)
        
        // 解析 manifest
        val manifest = if (readmeResult.isSuccess) {
            val skillContent = readmeResult.getOrThrow()
            if (skillContent.startsWith("---")) {
                parser.parse(skillContent).getOrElse { 
                    return@withContext Result.failure(IllegalArgumentException("Invalid skill format"))
                }
            } else {
                buildSkillManifest(skillInfo)
            }
        } else {
            buildSkillManifest(skillInfo)
        }
        
        // 创建技能目录
        val targetDir = File(skillsDir, manifest.slug).also { it.mkdirs() }
        
        // 写入 SKILL.md（仅当成功获取内容时）
        if (readmeResult.isSuccess) {
            File(targetDir, "SKILL.md").writeText(readmeResult.getOrThrow())
        }
        
        val installed = InstalledSkill(
            manifest = manifest,
            source = SkillSource.CLAWHUB,
            installedAt = System.currentTimeMillis(),
            enabled = true
        )
        
        saveSkillMetadata(installed)
        _skills.value = _skills.value + installed
        
        Result.success(installed)
    }
    
    private fun buildSkillManifest(info: ClawHubSkill): SkillManifest {
        return SkillManifest(
            name = info.name,
            slug = info.slug,
            version = info.version,
            homepage = info.homepage,
            description = info.description,
            metadata = SkillMetadata(
                emoji = info.emoji,
                requires = SkillRequirements(),
                os = emptyList()
            )
        )
    }
    
    /**
     * Uninstall a skill
     */
    suspend fun uninstallSkill(slug: String): Result<Unit> = withContext(Dispatchers.IO) {
        val skill = _skills.value.find { it.manifest.slug == slug }
            ?: return@withContext Result.failure(IllegalArgumentException("Skill not found"))
        
        if (skill.source == SkillSource.BUILTIN) {
            return@withContext Result.failure(IllegalArgumentException("Cannot uninstall built-in skills"))
        }
        
        // Remove from disk
        File(skillsDir, slug).deleteRecursively()
        
        // Remove from state
        _skills.value = _skills.value.filter { it.manifest.slug != slug }
        
        // Update metadata
        saveAllSkillMetadata()
        
        Result.success(Unit)
    }
    
    /**
     * Enable/disable a skill
     */
    suspend fun setSkillEnabled(slug: String, enabled: Boolean): Result<Unit> = withContext(Dispatchers.IO) {
        val index = _skills.value.indexOfFirst { it.manifest.slug == slug }
        if (index < 0) return@withContext Result.failure(IllegalArgumentException("Skill not found"))
        
        val updated = _skills.value[index].copy(enabled = enabled)
        _skills.value = _skills.value.toMutableList().also { it[index] = updated }
        
        saveSkillMetadata(updated)
        Result.success(Unit)
    }
    
    /**
     * Update skill settings
     */
    suspend fun updateSkillSettings(slug: String, settings: Map<String, String>): Result<Unit> = withContext(Dispatchers.IO) {
        val index = _skills.value.indexOfFirst { it.manifest.slug == slug }
        if (index < 0) return@withContext Result.failure(IllegalArgumentException("Skill not found"))
        
        val updated = _skills.value[index].copy(settings = settings)
        _skills.value = _skills.value.toMutableList().also { it[index] = updated }
        
        saveSkillMetadata(updated)
        Result.success(Unit)
    }
    
    /**
     * Get enabled skills
     */
    fun getEnabledSkills(): List<InstalledSkill> {
        return _skills.value.filter { it.enabled }
    }
    
    /**
     * Find skills matching trigger phrase
     */
    fun findByTrigger(trigger: String): List<InstalledSkill> {
        val lowerTrigger = trigger.lowercase()
        return getEnabledSkills().filter { skill ->
            skill.manifest.triggers.any { t ->
                t.keyword?.any { it.lowercase().contains(lowerTrigger) } == true ||
                t.regex?.contains(lowerTrigger) == true ||
                t.intent?.contains(lowerTrigger) == true
            }
        }
    }
    
    /**
     * Get all tools exposed by enabled skills
     */
    fun getSkillTools(): List<SkillTool> {
        return getEnabledSkills().flatMap { it.manifest.tools }
    }
    
    /**
     * Save skill metadata to disk
     */
    private fun saveSkillMetadata(skill: InstalledSkill) {
        val metadataFile = File(skillsDir, "skills.json")
        val existing = if (metadataFile.exists()) {
            json.decodeFromString<List<SkillStorageMetadata>>(metadataFile.readText()).toMutableList()
        } else {
            mutableListOf()
        }
        
        // Remove existing entry for this skill
        existing.removeAll { it.slug == skill.manifest.slug }
        
        // Add updated entry
        existing.add(SkillStorageMetadata(
            slug = skill.manifest.slug,
            installedAt = skill.installedAt,
            enabled = skill.enabled,
            settings = skill.settings
        ))
        
        metadataFile.writeText(json.encodeToString(existing))
    }
    
    /**
     * Save all skill metadata
     */
    private fun saveAllSkillMetadata() {
        val metadata = _skills.value
            .filter { it.source != SkillSource.BUILTIN }
            .map { skill ->
                SkillStorageMetadata(
                    slug = skill.manifest.slug,
                    installedAt = skill.installedAt,
                    enabled = skill.enabled,
                    settings = skill.settings
                )
            }
        
        val metadataFile = File(skillsDir, "skills.json")
        metadataFile.writeText(json.encodeToString(metadata))
    }
    
    /**
     * Get available runtime for skill
     */
    fun getRuntimeForSkill(skill: InstalledSkill): SkillRuntime? {
        // First check for built-in runtime
        return runtimes.firstOrNull { it.isAvailable() }
    }
}

@kotlinx.serialization.Serializable
private data class SkillStorageMetadata(
    val slug: String,
    val installedAt: Long,
    val enabled: Boolean,
    val settings: Map<String, String> = emptyMap()
)
