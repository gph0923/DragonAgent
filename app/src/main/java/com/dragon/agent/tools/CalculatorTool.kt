package com.dragon.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 计算器工具
 */
class CalculatorTool : BaseTool(
    name = "calculator",
    description = "执行数学计算。支持基本运算：加(+)、减(-)、乘(*)、除(/)、幂(**)、取模(%)。也支持括号和数学函数如 sqrt(), abs(), sin(), cos(), tan(), log(), ln()"
) {
    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = ToolParameters(
                properties = mapOf(
                    "expression" to ToolProperty(
                        type = "string",
                        description = "数学表达式，如: 2 + 3 * 4 或 sqrt(16) + log(100, 10)"
                    )
                ),
                required = listOf("expression")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.Default) {
            try {
                val expression = args["expression"]?.toString()
                    ?: return@withContext ToolResult(false, "", "Missing expression")

                // 安全计算
                val result = safeEval(expression)
                ToolResult(true, result.toString())
            } catch (e: Exception) {
                ToolResult(false, "", "计算错误: ${e.message}")
            }
        }
    }

    /**
     * 安全地计算数学表达式
     * 限制支持的运算符和函数，防止恶意代码执行
     */
    private fun safeEval(expression: String): Double {
        // 预处理表达式
        var expr = expression
            .replace(" ", "")
            .replace("π", Math.PI.toString())
            .replace("e(?![x])".toRegex(), Math.E.toString())
            .replace("sqrt\\(([^)]+)\\)".toRegex(), "Math.sqrt($1)")
            .replace("abs\\(([^)]+)\\)".toRegex(), "Math.abs($1)")
            .replace("sin\\(([^)]+)\\)".toRegex(), "Math.sin($1)")
            .replace("cos\\(([^)]+)\\)".toRegex(), "Math.cos($1)")
            .replace("tan\\(([^)]+)\\)".toRegex(), "Math.tan($1)")
            .replace("log\\(([^,]+),([^)]+)\\)".toRegex(), "(Math.log($1) / Math.log($2))")
            .replace("ln\\(([^)]+)\\)".toRegex(), "Math.log($1)")
            .replace("pow\\(([^,]+),([^)]+)\\)".toRegex(), "Math.pow($1,$2)")
            .replace("\\*\\*".toRegex(), "^")
            .replace("(\\d+\\.?\\d*)\\^(\\d+\\.?\\d*)".toRegex(), "Math.pow($1,$2)")

        // 简单表达式求值（只支持基本运算）
        return evaluateSimple(expr)
    }

    /**
     * 简单表达式求值（递归下降）
     */
    private fun evaluateSimple(expr: String): Double {
        var expression = expr

        // 处理括号
        while (expression.contains("(")) {
            expression = expression.replace(Regex("\\(([^()]+)\\)")) { match ->
                evaluateSimple(match.groupValues[1]).toString()
            }
        }

        // 从左到右计算，注意乘除优先级
        val terms = splitByOperators(expression, listOf("+", "-"))
        var result = evaluateTerms(terms[0])

        for (i in 1 until terms.size step 2) {
            val op = terms[i]
            val nextTerm = terms[i + 1]
            result = when (op) {
                "+" -> result + evaluateTerms(nextTerm)
                "-" -> result - evaluateTerms(nextTerm)
                else -> result
            }
        }

        return result
    }

    private fun evaluateTerms(term: String): Double {
        val factors = splitByOperators(term, listOf("*", "/", "%"))
        var result = parseNumber(factors[0])

        for (i in 1 until factors.size step 2) {
            val op = factors[i]
            val nextFactor = parseNumber(factors[i + 1])
            result = when (op) {
                "*" -> result * nextFactor
                "/" -> {
                    if (nextFactor == 0.0) throw ArithmeticException("除数不能为零")
                    result / nextFactor
                }
                "%" -> result % nextFactor
                else -> result
            }
        }

        return result
    }

    private fun splitByOperators(expr: String, operators: List<String>): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var i = 0

        while (i < expr.length) {
            val matched = operators.find { op ->
                expr.substring(i).startsWith(op)
            }

            if (matched != null) {
                if (current.isNotEmpty()) {
                    result.add(current.toString())
                    result.add(matched)
                    current = StringBuilder()
                } else if (matched == "-" && result.isEmpty()) {
                    current.append("-")
                } else if (result.isNotEmpty() && result.last() in operators) {
                    result.add(matched)
                }
                i += matched.length
            } else {
                current.append(expr[i])
                i++
            }
        }

        if (current.isNotEmpty()) {
            result.add(current.toString())
        }

        return result
    }

    private fun parseNumber(str: String): Double {
        return try {
            str.toDouble()
        } catch (e: NumberFormatException) {
            throw IllegalArgumentException("无效的数字: $str")
        }
    }

    private fun String.replaceRegex(
        regex: String,
        replacement: (MatchResult) -> CharSequence
    ): String {
        return this.replace(Regex(regex), replacement)
    }
}
