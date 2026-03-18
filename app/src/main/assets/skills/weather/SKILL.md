---
name: Weather
slug: weather
version: 1.0.0
homepage: 
description: 获取天气预报和空气质量信息
metadata: {"emoji":"🌤️","requires":{"bins":[]},"os":["android"]}
---

## 触发条件

```yaml
triggers:
  - keyword: ["天气", "Weather", "预报", "温度", "下雨", "晴天"]
  - intent: get_weather
  - intent: get_forecast
```

## 运行环境

```yaml
runtime:
  type: inline
  timeout: 30
```

## 暴露的工具

### get_current_weather
获取当前天气

**参数:**
- location: 城市名称
- units: 摄氏度(celsius)/华氏度(fahrenheit)

### get_forecast
获取天气预报

**参数:**
- location: 城市名称
- days: 天数 (1-7)

## 数据来源

使用 Open-Meteo API（免费，无需 API Key）

## 示例

```
用户: 北京天气怎么样
助手: 🌤️ 北京当前天气
温度: 22°C
condition: 晴
湿度: 45%
风速: 12 km/h

用户: 上海明天天气
助手: 🌧️ 上海明日预报
最高: 28°C
最低: 20°C
天气: 小雨
```
