package com.example.snowscreen.model

data class SnowSettings(
    val flakeCount: Int = 180,
    val speedMultiplier: Float = 1.0f,
    val sizeMultiplier: Float = 1.0f,
    val wind: Float = 0.0f,
    val sensorTiltEnabled: Boolean = false,
    val randomWindGusts: Boolean = false,
    val onlyHomeAndLock: Boolean = false,
    val weatherMode: WeatherMode = WeatherMode.GENTLE
)

enum class WeatherMode {
    GENTLE,
    BLIZZARD,
    MAGICAL,
    CUSTOM
}
