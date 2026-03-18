package com.dragon.agent.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Resolves and manages skill dependencies
 */
class DependencyResolver(
    private val skillManager: SkillManager
) {
    private val json = Json { ignoreUnknownKeys = true }
    
    // Known skill registry (slug -> required skills)
    private val skillDependencies = mutableMapOf<String, List<String>>()
    
    /**
     * Resolve dependencies for a skill
     */
    suspend fun resolve(skillSlug: String): Result<List<SkillDependency>> = withContext(Dispatchers.IO) {
        try {
            val dependencies = mutableListOf<SkillDependency>()
            
            // Check if we have dependency info
            val requiredDeps = skillDependencies[skillSlug] ?: emptyList()
            
            for (depSlug in requiredDeps) {
                val depSkill = skillManager.skills.value.find { it.manifest.slug == depSlug }
                if (depSkill != null) {
                    dependencies.add(SkillDependency(
                        slug = depSlug,
                        name = depSkill.manifest.name,
                        installed = depSkill.enabled,
                        version = depSkill.manifest.version
                    ))
                } else {
                    dependencies.add(SkillDependency(
                        slug = depSlug,
                        name = depSlug,
                        installed = false,
                        version = ""
                    ))
                }
            }
            
            Result.success(dependencies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
    
    /**
     * Install missing dependencies for a skill
     */
    suspend fun installMissingDependencies(skillSlug: String): Result<Unit> = withContext(Dispatchers.IO) {
        val deps = resolve(skillSlug).getOrElse { return@withContext Result.failure(it) }
        
        for (dep in deps) {
            if (!dep.installed) {
                // Try to install from ClawHub
                skillManager.installFromClawHub(dep.slug).getOrElse {
                    return@withContext Result.failure(IllegalStateException("Missing dependency: ${dep.slug}"))
                }
            }
        }
        
        Result.success(Unit)
    }
    
    /**
     * Check if all dependencies are satisfied
     */
    suspend fun areDependenciesSatisfied(skillSlug: String): Boolean {
        val deps = resolve(skillSlug).getOrElse { return false }
        return deps.all { it.installed }
    }
    
    /**
     * Get dependency tree for a skill
     */
    suspend fun getDependencyTree(skillSlug: String, visited: MutableSet<String> = mutableSetOf()): DependencyTree {
        if (skillSlug in visited) {
            return DependencyTree(skillSlug, emptyList(), true) // Circular
        }
        visited.add(skillSlug)
        
        val deps = resolve(skillSlug).getOrElse { 
            return DependencyTree(skillSlug, emptyList(), false, it.message) 
        }
        
        val children = deps.map { dep ->
            getDependencyTree(dep.slug, visited)
        }
        
        return DependencyTree(
            slug = skillSlug,
            dependencies = children,
            allInstalled = deps.all { it.installed }
        )
    }
    
    /**
     * Register dependency information for a skill
     */
    fun registerDependencies(skillSlug: String, dependsOn: List<String>) {
        skillDependencies[skillSlug] = dependsOn
    }
    
    /**
     * Parse dependencies from SKILL.md
     */
    fun parseDependenciesFromManifest(manifest: SkillManifest): List<String> {
        // Look for dependencies in metadata or trigger hints
        val deps = mutableListOf<String>()
        
        // Check if skill name suggests dependency
        // e.g., "github-issues" might depend on "github"
        val nameParts = manifest.slug.split("-")
        if (nameParts.size > 1) {
            val potentialDep = nameParts.first()
            // Only add if there's a known skill with this prefix
            if (skillManager.skills.value.any { it.manifest.slug.startsWith(potentialDep) }) {
                deps.add(potentialDep)
            }
        }
        
        return deps
    }
}

@Serializable
data class SkillDependency(
    val slug: String,
    val name: String,
    val installed: Boolean,
    val version: String = ""
)

data class DependencyTree(
    val slug: String,
    val dependencies: List<DependencyTree>,
    val allInstalled: Boolean,
    val error: String? = null
) {
    fun toDisplayString(indent: Int = 0): String {
        val prefix = "  ".repeat(indent)
        val status = if (allInstalled) "✅" else "❌"
        val errorStr = if (error != null) " - $error" else ""
        
        val sb = StringBuilder()
        sb.appendLine("$prefix$status $slug$errorStr")
        
        for (dep in dependencies) {
            sb.append(dep.toDisplayString(indent + 1))
        }
        
        return sb.toString()
    }
}
