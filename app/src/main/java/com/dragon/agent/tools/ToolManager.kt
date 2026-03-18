package com.dragon.agent.tools

import android.content.Context
import com.dragon.agent.tools.android.*
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 工具管理器
 * 负责工具的注册、获取和调用
 */
@Singleton
class ToolManager @Inject constructor(
    @ApplicationContext private val context: Context
) {

    private val registry = ToolRegistry()
    private val json = Json { encodeDefaults = true }

    init {
        // 注册内置工具
        registerBuiltInTools()
    }

    /**
     * 注册内置工具
     */
    private fun registerBuiltInTools() {
        // ===== 通用工具 =====
        // 计算器工具
        registry.register(CalculatorTool())

        // 搜索工具
        registry.register(WebSearchTool())

        // 天气工具
        registry.register(WeatherTool())

        // HTTP 请求工具
        registry.register(HttpRequestTool())

        // 文件操作工具
        registry.register(FileTool(context))

        // ===== Android 原生工具 =====
        // 通讯录
        registry.register(ContactsTool())

        // 短信
        registry.register(SmsTool())

        // 通话记录
        registry.register(CallLogTool())

        // 应用列表
        registry.register(AppsTool())

        // 设备信息
        registry.register(DeviceInfoTool())

        // 系统设置
        registry.register(SettingsTool())

        // 常用 Intent
        registry.register(IntentTool())
    }

    /**
     * 获取工具定义列表（供 LLM 使用）
     */
    fun getToolDefinitions(): List<ToolDefinition> {
        return registry.getAllDefinitions()
    }
    
    /**
     * 获取所有工具列表
     */
    fun getTools(): List<BaseTool> {
        return registry.listTools()
    }
    
    /**
     * 注册自定义工具（通过名称和描述）
     */
    fun registerTool(name: String, description: String, parameters: Map<String, ToolProperty>) {
        // 创建一个简单的工具代理
        val tool = object : BaseTool() {
            override val name: String = name
            override val description: String = description
            
            override fun getDefinition(): ToolDefinition {
                return ToolDefinition(
                    name = name,
                    description = description,
                    parameters = ToolParameters(
                        properties = parameters.mapValues { (key, prop) ->
                            ToolProperty(
                                type = prop.type,
                                description = prop.description,
                                enum = prop.enum
                            )
                        }
                    )
                )
            }
            
            override suspend fun execute(args: Map<String, Any>): ToolResult {
                return ToolResult(success = true, content = "Tool $name executed with params: $args")
            }
        }
        registry.register(tool)
    }

    /**
     * 执行工具调用
     */
    suspend fun executeTool(name: String, arguments: Map<String, Any>): ToolResult {
        val call = ToolCall(
            id = System.currentTimeMillis().toString(),
            name = name,
            arguments = json.encodeToString(
                MapSerializer(String.serializer(), String.serializer()),
                arguments.mapValues { it.value.toString() }
            )
        )
        return registry.execute(call)
    }

    /**
     * 直接通过 ToolCall 执行
     */
    suspend fun execute(call: ToolCall): ToolResult {
        return registry.execute(call)
    }

    /**
     * 列出所有已注册工具
     */
    fun listTools(): List<BaseTool> {
        return registry.listTools()
    }

    /**
     * 检查工具是否存在
     */
    fun hasTool(name: String): Boolean {
        return registry.get(name) != null
    }

    /**
     * 添加自定义工具
     */
    fun addTool(tool: BaseTool) {
        registry.register(tool)
    }
}
