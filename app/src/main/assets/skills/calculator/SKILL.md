---
name: Calculator
slug: calculator
version: 1.0.0
homepage: 
description: 数学计算器，支持基本运算和科学计算
metadata: {"emoji":"🧮","requires":{"bins":[]},"os":["android"]}
---

## 触发条件

```yaml
triggers:
  - keyword: ["计算", "算", "等于", "加", "减", "乘", "除"]
  - intent: calculate
```

## 运行环境

```yaml
runtime:
  type: inline
  timeout: 10
```

## 暴露的工具

### calculate
数学计算

**参数:**
- expression: 数学表达式

**示例:**
```
输入: 2 + 3 * 4
输出: 14

输入: sqrt(16) + pow(2, 3)
输出: 20
```

## 支持的运算

| 类别 | 运算 |
|------|------|
| 基本 | +, -, *, /, % |
| 幂 | ^, ** |
| 函数 | abs, sqrt, sin, cos, tan, log, ln, exp |
| 常量 | pi, e |
