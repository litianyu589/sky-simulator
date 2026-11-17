package com.skysim.app.core

/**
 * v0.6.4 的场景设置：晨昏分层 + 地形光影。全部为纯数据，便于持久化与测试。
 * 默认值取保守：分层开启、强度 0.65，地形开启、阴影强度 0.65、植被 0.35、画质中档。
 */
data class SceneOptions(
    val twilightOn: Boolean = true,
    val twilightStrength: Float = 0.65f,
    val terrainOn: Boolean = true,
    val shadowStrength: Float = 0.65f,
    val vegetation: Float = 0.35f,
    val quality: TerrainQuality = TerrainQuality.MEDIUM
) {
    val twilight: Twilight.Setting
        get() = Twilight.Setting(twilightOn, twilightStrength)

    fun clamped(): SceneOptions = copy(
        twilightStrength = twilightStrength.coerceIn(0f, 1f),
        shadowStrength = shadowStrength.coerceIn(0f, 1f),
        vegetation = vegetation.coerceIn(0f, 1f)
    )

    companion object {
        val OFF = SceneOptions(
            twilightOn = false, twilightStrength = 0f,
            terrainOn = false, shadowStrength = 0f,
            vegetation = 0f
        )
    }
}
