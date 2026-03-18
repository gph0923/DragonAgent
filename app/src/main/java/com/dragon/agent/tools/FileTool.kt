package com.dragon.agent.tools

import android.content.Context
import android.os.Environment
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 文件操作工具
 * 注意：在 Android 环境中，应用只能访问自己的私有目录
 */
class FileTool @javax.inject.Inject constructor(
    @ApplicationContext private val context: Context
) : BaseTool(
    name = "file",
    description = "在应用私有存储中读取和写入文件。操作应用内部私有目录下的文件。"
) {

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = ToolParameters(
                properties = mapOf(
                    "operation" to ToolProperty(
                        type = "string",
                        description = "操作类型: read, write, list, delete, exists",
                        enum = listOf("read", "write", "list", "delete", "exists")
                    ),
                    "path" to ToolProperty(
                        type = "string",
                        description = "文件路径（相对于应用私有目录）"
                    ),
                    "content" to ToolProperty(
                        type = "string",
                        description = "写入文件的内容（write 操作时需要）"
                    )
                ),
                required = listOf("operation", "path")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val operation = args["operation"]?.toString() ?: "list"
                val path = args["path"]?.toString() ?: ""
                val content = args["content"]?.toString()

                // 安全检查：只允许在私有目录操作
                val safePath = sanitizePath(path)
                if (safePath == null) {
                    return@withContext ToolResult(false, "", "无效的路径")
                }

                when (operation) {
                    "read" -> readFile(safePath)
                    "write" -> writeFile(safePath, content ?: "")
                    "list" -> listFiles(safePath)
                    "delete" -> deleteFile(safePath)
                    "exists" -> fileExists(safePath)
                    else -> ToolResult(false, "", "未知操作: $operation")
                }
            } catch (e: Exception) {
                ToolResult(false, "", "文件操作失败: ${e.message}")
            }
        }
    }

    /**
     * 路径安全检查
     */
    private fun sanitizePath(path: String): String? {
        // 禁止路径遍历
        if (path.contains("..")) return null

        // 只能访问私有目录
        val baseDir = context.filesDir.absolutePath
        val targetPath = "$baseDir/$path"

        // 确保路径在私有目录内
        if (!targetPath.startsWith(baseDir)) return null

        return targetPath
    }

    /**
     * 读取文件
     */
    private fun readFile(path: String): ToolResult {
        val file = File(path)
        if (!file.exists()) {
            return ToolResult(false, "", "文件不存在: $path")
        }
        if (!file.isFile) {
            return ToolResult(false, "", "不是文件: $path")
        }

        val content = file.readText()
        return ToolResult(true, "📄 文件: $path\n\n$content")
    }

    /**
     * 写入文件
     */
    private fun writeFile(path: String, content: String): ToolResult {
        val file = File(path)

        // 确保父目录存在
        file.parentFile?.mkdirs()

        file.writeText(content)
        return ToolResult(true, "✅ 文件已写入: $path (${content.length} 字符)")
    }

    /**
     * 列出目录
     */
    private fun listFiles(path: String): ToolResult {
        val dir = File(path)
        if (!dir.exists()) {
            return ToolResult(false, "", "目录不存在: $path")
        }
        if (!dir.isDirectory) {
            return ToolResult(false, "", "不是目录: $path")
        }

        val files = dir.listFiles() ?: return ToolResult(true, "📁 目录为空: $path")

        val list = files.joinToString("\n") { file ->
            val type = if (file.isDirectory) "📁" else "📄"
            val size = if (file.isFile) " (${file.length()} bytes)" else ""
            "$type ${file.name}$size"
        }

        return ToolResult(true, "📁 目录: $path\n\n$list")
    }

    /**
     * 删除文件
     */
    private fun deleteFile(path: String): ToolResult {
        val file = File(path)
        if (!file.exists()) {
            return ToolResult(false, "", "文件不存在: $path")
        }

        val deleted = file.delete()
        return if (deleted) {
            ToolResult(true, "🗑️ 文件已删除: $path")
        } else {
            ToolResult(false, "", "删除失败: $path")
        }
    }

    /**
     * 检查文件是否存在
     */
    private fun fileExists(path: String): ToolResult {
        val file = File(path)
        val exists = file.exists()
        return ToolResult(
            true,
            if (exists) {
                "✅ 文件存在: $path (${if (file.isDirectory) "目录" else "文件"})"
            } else {
                "❌ 文件不存在: $path"
            }
        )
    }
}
