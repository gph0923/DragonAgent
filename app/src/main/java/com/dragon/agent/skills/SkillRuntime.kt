package com.dragon.agent.skills

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runtime interface for executing skill code
 */
interface SkillRuntime {
    val name: String
    val supportedExtensions: List<String>
    
    /**
     * Execute skill code and return result
     */
    suspend fun execute(
        script: String,
        function: String,
        params: Map<String, String>
    ): Result<SkillResult>
    
    /**
     * Check if runtime is available on this system
     */
    fun isAvailable(): Boolean
    
    /**
     * Get required dependencies for this runtime
     */
    fun getRequiredDeps(): List<String> = emptyList()
}

data class SkillResult(
    val success: Boolean,
    val output: String = "",
    val error: String = "",
    val tools: List<SkillTool> = emptyList()
)

/**
 * Python3 runtime for skill execution
 */
class Python3Runtime : SkillRuntime {
    override val name = "Python3"
    override val supportedExtensions = listOf("py")
    
    private val json = kotlinx.serialization.json.Json { encodeDefaults = true }
    
    override fun isAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("python3", "--version"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun execute(
        script: String,
        function: String,
        params: Map<String, String>
    ): Result<SkillResult> = withContext(Dispatchers.IO) {
        try {
            // Convert params to simple JSON string manually
            val paramsJson = params.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
            
            val code = buildString {
                append(script)
                append("\n\n")
                append("if __name__ == \"__main__\":\n")
                append("    import json\n")
                append("    params = json.loads('''$paramsJson''')\n")
                append("    result = $function(params)\n")
                append("    print(json.dumps(result))\n")
            }
            
            val process = Runtime.getRuntime().exec(arrayOf("python3", "-c", code))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            Result.success(SkillResult(
                success = process.waitFor() == 0,
                output = output,
                error = error
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * NodeJS runtime for skill execution  
 */
class NodeJSRuntime : SkillRuntime {
    override val name = "NodeJS"
    override val supportedExtensions = listOf("js")
    
    override fun isAvailable(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("node", "--version"))
            process.waitFor() == 0
        } catch (e: Exception) {
            false
        }
    }
    
    override suspend fun execute(
        script: String,
        function: String,
        params: Map<String, String>
    ): Result<SkillResult> = withContext(Dispatchers.IO) {
        try {
            // Convert params to simple JSON string manually
            val paramsJson = params.entries.joinToString(",", "{", "}") { "\"${it.key}\":\"${it.value}\"" }
            
            val code = buildString {
                append(script)
                append("\n\n")
                append("const params = $paramsJson;\n")
                append("const result = $function(params);\n")
                append("console.log(JSON.stringify(result));\n")
            }
            
            val process = Runtime.getRuntime().exec(arrayOf("node", "-e", code))
            val output = process.inputStream.bufferedReader().readText()
            val error = process.errorStream.bufferedReader().readText()
            
            Result.success(SkillResult(
                success = process.waitFor() == 0,
                output = output,
                error = error
            ))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * Simple inline runtime for basic skill operations
 */
class InlineSkillRuntime : SkillRuntime {
    override val name = "Inline"
    override val supportedExtensions = emptyList()
    
    override fun isAvailable(): Boolean = true
    
    override suspend fun execute(
        script: String,
        function: String,
        params: Map<String, String>
    ): Result<SkillResult> {
        // For inline skills, the script defines tools directly
        val result = SkillResult(
            success = true,
            output = script,
            tools = parseInlineTools(script)
        )
        return Result.success(result)
    }
    
    private fun parseInlineTools(script: String): List<SkillTool> {
        val tools = mutableListOf<SkillTool>()
        // Parse tool definitions from Kotlin script
        val toolPattern = Regex("""tool\(\s*["'](.+?)["']\s*,\s*["'](.+?)["']""")
        toolPattern.findAll(script).forEach { match ->
            tools.add(SkillTool(
                name = match.groupValues[1],
                description = match.groupValues[2]
            ))
        }
        return tools
    }
}
