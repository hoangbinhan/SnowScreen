package com.example.snowscreen.model

data class SnowSettings(
    val flakeCount: Int = 70,
    val speedMultiplier: Float = 0.7f,
    val sizeMultiplier: Float = 0.7f,
    val wind: Float = -1.0f,
    val sensorTiltEnabled: Boolean = true,
    val randomWindGusts: Boolean = true,
    val onlyHomeAndLock: Boolean = false,
    // 8 Snowflake shape toggles
    val enableDendrite: Boolean = true, // Type 0
    val enableHexagon: Boolean = true,  // Type 1
    val enableStar: Boolean = true,     // Type 2
    val enableCluster: Boolean = true,  // Type 3
    val enableDiamond: Boolean = true,  // Type 4
    val enableStar12: Boolean = true,   // Type 5
    val enableCross: Boolean = true,    // Type 6
    val enableDust: Boolean = true,     // Type 7
    val weatherMode: WeatherMode = WeatherMode.GENTLE
)

enum class WeatherMode {
    GENTLE,
    BLIZZARD,
    MAGICAL,
    CUSTOM
}
