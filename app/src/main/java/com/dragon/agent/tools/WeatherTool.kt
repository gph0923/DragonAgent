package com.dragon.agent.tools

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * 天气数据
 */
@Serializable
data class WeatherData(
    val location: String,
    val temperature: Double,
    val feelsLike: Double,
    val humidity: Int,
    val windSpeed: Double,
    val condition: String,
    val description: String
)

/**
 * 天气查询工具
 * 使用 Open-Meteo API（免费，无需 API Key）
 */
class WeatherTool : BaseTool(
    name = "weather",
    description = "查询指定城市的天气信息。使用 Open-Meteo API，返回温度、体感温度、湿度、风速和天气状况。"
) {
    private val json = Json { ignoreUnknownKeys = true }

    // 常用城市坐标映射
    private val cityCoords = mapOf(
        "北京" to Pair(39.9042, 116.4074),
        "上海" to Pair(31.2304, 121.4737),
        "广州" to Pair(23.1291, 113.2644),
        "深圳" to Pair(22.5431, 114.0579),
        "杭州" to Pair(30.2741, 120.1551),
        "成都" to Pair(30.5728, 104.0668),
        "武汉" to Pair(30.5928, 114.3055),
        "西安" to Pair(34.3416, 108.9398),
        "南京" to Pair(32.0603, 118.7969),
        "重庆" to Pair(29.4316, 106.9123),
        "天津" to Pair(39.3434, 117.3616),
        "苏州" to Pair(31.2990, 120.5853),
        "郑州" to Pair(34.7466, 113.6253),
        "长沙" to Pair(28.2282, 112.9388),
        "青岛" to Pair(36.0671, 120.3826),
        "沈阳" to Pair(41.8057, 123.4328),
        "大连" to Pair(38.9140, 121.6147),
        "厦门" to Pair(24.4798, 118.0894),
        "昆明" to Pair(25.0406, 102.7129),
        "哈尔滨" to Pair(45.8038, 126.5340)
    )

    override fun getDefinition(): ToolDefinition {
        return ToolDefinition(
            name = name,
            description = description,
            parameters = ToolParameters(
                properties = mapOf(
                    "city" to ToolProperty(
                        type = "string",
                        description = "城市名称，如：北京、上海、广州"
                    )
                ),
                required = listOf("city")
            )
        )
    }

    override suspend fun execute(args: Map<String, Any>): ToolResult {
        return withContext(Dispatchers.IO) {
            try {
                val city = args["city"]?.toString()
                    ?: return@withContext ToolResult(false, "", "Missing city")

                val weather = getWeather(city)
                ToolResult(true, formatWeather(weather))
            } catch (e: Exception) {
                ToolResult(false, "", "查询天气失败: ${e.message}")
            }
        }
    }

    /**
     * 获取天气数据
     */
    private fun getWeather(city: String): WeatherData {
        val coords = cityCoords[city] ?: run {
            // 尝试通过城市名称获取坐标
            getCoordsFromCity(city)
        }

        val (lat, lon) = coords

        val url = URL("https://api.open-meteo.com/v1/forecast?" +
                "latitude=$lat&longitude=$lon" +
                "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m" +
                "&timezone=auto")

        val connection = url.openConnection() as HttpURLConnection
        connection.requestMethod = "GET"
        connection.connectTimeout = 10000
        connection.readTimeout = 10000

        val response = connection.inputStream.bufferedReader().readText()
        val data = json.parseToJsonElement(response).jsonObject

        val current = data["current"]!!.jsonObject

        val temp = current["temperature_2m"]!!.jsonPrimitive.content.toDouble()
        val humidity = current["relative_humidity_2m"]!!.jsonPrimitive.content.toInt()
        val feelsLike = current["apparent_temperature"]!!.jsonPrimitive.content.toDouble()
        val windSpeed = current["wind_speed_10m"]!!.jsonPrimitive.content.toDouble()
        val weatherCode = current["weather_code"]!!.jsonPrimitive.content.toInt()

        val (condition, description) = getWeatherCondition(weatherCode)

        return WeatherData(
            location = city,
            temperature = temp,
            feelsLike = feelsLike,
            humidity = humidity,
            windSpeed = windSpeed,
            condition = condition,
            description = description
        )
    }

    /**
     * 从城市名获取坐标（简化版，默认北京）
     */
    private fun getCoordsFromCity(city: String): Pair<Double, Double> {
        // 这里可以添加调用地理编码 API 的逻辑
        // 目前返回北京坐标作为默认值
        throw IllegalArgumentException("暂不支持查询城市: $city，请使用支持的城市列表中的城市")
    }

    /**
     * 格式化天气信息
     */
    private fun formatWeather(weather: WeatherData): String {
        return """
            🌤️ ${weather.location} 天气预报

            📌 当前天气: ${weather.description}
            🌡️ 温度: ${weather.temperature}°C
            👔 体感温度: ${weather.feelsLike}°C
            💧 湿度: ${weather.humidity}%
            💨 风速: ${weather.windSpeed} km/h
        """.trimIndent()
    }

    /**
     * WMO 天气代码映射
     */
    private fun getWeatherCondition(code: Int): Pair<String, String> {
        return when (code) {
            0 -> "Clear" to "晴"
            1, 2, 3 -> "Cloudy" to "多云"
            45, 48 -> "Fog" to "雾"
            51, 53, 55 -> "Drizzle" to "毛毛雨"
            56, 57 -> "Freezing Drizzle" to "冻毛毛雨"
            61, 63, 65 -> "Rain" to "雨"
            66, 67 -> "Freezing Rain" to "冻雨"
            71, 73, 75 -> "Snow" to "雪"
            77 -> "Snow Grains" to "雪粒"
            80, 81, 82 -> "Rain Showers" to "阵雨"
            85, 86 -> "Snow Showers" to "阵雪"
            95 -> "Thunderstorm" to "雷暴"
            96, 99 -> "Thunderstorm with Hail" to "雷暴冰雹"
            else -> "Unknown" to "未知"
        }
    }
}
