package com.dragon.agent.tools

import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * 工具调用结果
 */
@Serializable
data class ToolResult(
    val success: Boolean,
    val content: String,
    val error: String? = null
) {
    /**
     * 兼容旧代码的 output 属性
     */
    val output: String get() = content
}

/**
 * 工具定义
 */
@Serializable
data class ToolDefinition(
    val name: String,
    val description: String,
    val parameters: ToolParameters
)

/**
 * 工具参数 schema
 */
@Serializable
data class ToolParameters(
    val type: String = "object",
    val properties: Map<String, ToolProperty> = emptyMap(),
    val required: List<String> = emptyList()
)

/**
 * 工具属性
 */
@Serializable
data class ToolProperty(
    val type: String,
    val description: String,
    val enum: List<String>? = null
)

/**
 * 工具基类
 */
abstract class BaseTool(
    open val name: String,
    open val description: String
) {
    /**
     * 获取工具定义（供 LLM 使用）
     */
    abstract fun getDefinition(): ToolDefinition

    /**
     * 执行工具
     */
    abstract suspend fun execute(args: Map<String, Any>): ToolResult

    /**
     * 工具命名空间（避免重名）
     */
    open val namespace: String = "default"
}

/**
 * 工具调用请求
 */
@Serializable
data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String  // JSON string
)

/**
 * 工具注册表
 */
class ToolRegistry {
    private val tools = mutableMapOf<String, BaseTool>()

    /**
     * 注册工具
     */
    fun register(tool: BaseTool) {
        val fullName = "${tool.namespace}_${tool.name}"
        tools[fullName] = tool
    }

    /**
     * 获取工具
     */
    fun get(name: String): BaseTool? {
        return tools[name] ?: tools.values.find { it.name == name }
    }

    /**
     * 获取所有工具定义
     */
    fun getAllDefinitions(): List<ToolDefinition> {
        return tools.values.map { it.getDefinition() }
    }

    /**
     * 执行工具调用
     */
    suspend fun execute(call: ToolCall): ToolResult {
        val tool = get(call.name) ?: return ToolResult(
            success = false,
            content = "",
            error = "Tool not found: ${call.name}"
        )

        return try {
            val args = Json.decodeFromString<Map<String, Any>>(call.arguments)
            tool.execute(args)
        } catch (e: Exception) {
            ToolResult(
                success = false,
                content = "",
                error = "Failed to parse arguments: ${e.message}"
            )
        }
    }

    /**
     * 列出所有工具
     */
    fun listTools(): List<BaseTool> = tools.values.toList()
}
