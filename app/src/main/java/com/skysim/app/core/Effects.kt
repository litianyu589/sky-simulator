package com.skysim.app.core

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 可独立开关的大气 / 天文效果。
 * 所有效果都必须由「当前星球的物理条件 + 太阳高度角 + 纬度 + 天气」共同约束，
 * 条件不成立时即使开关打开也不绘制（见 [FxRules]）。
 */
enum class EffectId(val labelZh: String, val hintZh: String) {
    CLOUDS("云层", "需要大气；气态巨行星当作云顶"),
    RAIN("降水", "需要大气与可降水成分，火星没有降雨"),
    RAINBOW("彩虹", "需要降水 + 太阳高度 2°–42°"),
    HALO("日晕/幻日", "需要薄云（卷云）+ 太阳在地平线上"),
    AURORA("极光", "需要磁场 + 高纬度 + 夜空"),
    HAZE("霾/沙尘", "需要气溶胶或尘暴大气"),
    LIGHTNING("闪电", "需要暴雨级降水")
}

/** 单个效果的开关与强度（0..1）。 */
data class FxSetting(val enabled: Boolean = false, val strength: Float = 0.6f) {
    fun strengthClamped(): Float = strength.coerceIn(0f, 1f)
}

/**
 * 天气状态：云量分 4 档（晴/少云/多云/阴），降水分 4 档（无/小雨/中雨/暴雨）。
 * [auto] 打开时按本地时间缓慢演变（完全离线，不使用任何真实气象数据）。
 */
data class WeatherState(
    val cloudLevel: Int = 1,
    val precipLevel: Int = 0,
    val auto: Boolean = false
) {
    val cloudCover: Float get() = CLOUD_COVER[cloudLevel.coerceIn(0, 3)]
    val precipIntensity: Float get() = PRECIP_INTENSITY[precipLevel.coerceIn(0, 3)]

    companion object {
        val CLOUD_NAMES = listOf("晴", "少云", "多云", "阴")
        val PRECIP_NAMES = listOf("无", "小雨", "中雨", "暴雨")
        val CLOUD_COVER = floatArrayOf(0f, 0.28f, 0.62f, 0.92f)
        val PRECIP_INTENSITY = floatArrayOf(0f, 0.35f, 0.68f, 1f)
    }
}

/** 全部效果的当前状态。 */
data class EffectState(
    val settings: Map<EffectId, FxSetting> = defaults(),
    val weather: WeatherState = WeatherState()
) {
    fun setting(id: EffectId): FxSetting = settings[id] ?: FxSetting()
    fun with(id: EffectId, s: FxSetting) = copy(settings = settings + (id to s))
    fun withWeather(w: WeatherState) = copy(weather = w)
    fun enabledCount(): Int = settings.count { it.value.enabled }

    companion object {
        fun defaults(): Map<EffectId, FxSetting> = mapOf(
            EffectId.CLOUDS to FxSetting(enabled = true, strength = 0.45f),
            EffectId.RAIN to FxSetting(enabled = false, strength = 0.6f),
            EffectId.RAINBOW to FxSetting(enabled = false, strength = 0.8f),
            EffectId.HALO to FxSetting(enabled = false, strength = 0.6f),
            EffectId.AURORA to FxSetting(enabled = false, strength = 0.7f),
            EffectId.HAZE to FxSetting(enabled = false, strength = 0.35f),
            EffectId.LIGHTNING to FxSetting(enabled = false, strength = 0.7f)
        )
    }
}

/**
 * 触发条件的纯函数实现：不依赖 Android，可直接做单元测试。
 * 这些规则就是说明文档里「每个效果的触发条件」的代码版本。
 */
object FxRules {

    /** 云的存在性 0..1：取决于气压与气溶胶/瑞利光学厚度。气态巨行星是云顶本身，恒为 1。 */
    fun cloudCapability(p: Planet): Float = when {
        p.surfaceKind == SurfaceKind.CLOUD_DECK -> 1f
        p.pressureAtm >= 0.5f && p.aerosolTau > 0.05 -> 1f
        p.pressureAtm >= 0.05f && (p.aerosolTau > 0.02 || p.rayTau > 0.05) -> 0.9f
        p.pressureAtm >= 0.005f -> 0.4f
        else -> 0f
    }

    /** 能不能下雨（或等效的液态/甲烷降水）0..1。火星与气态巨行星没有地面降雨。 */
    fun rainCapability(p: Planet): Float = when (p.id) {
        "earth" -> 1f
        "titan" -> 0.8f     // 甲烷雨
        "venus" -> 0.5f     // 硫酸雨（在约 30km 高度蒸发，视觉上只当高空降水）
        "mars" -> 0f        // 只有尘暴，没有降水
        else -> if (p.surfaceKind == SurfaceKind.CLOUD_DECK) 0f else 0f
    }

    /** 极光能力与出现的最小地磁纬度：地球极光椭圆约 65°–70°，中纬偶发；木星/土星极区更强；
     *  天王星/海王星磁轴倾角极大，极光可以下探到 40°–50°。没有全球磁场的天体（金星、火星、月球）为 0。 */
    private val AURORA_TABLE: Map<String, Pair<Float, Float>> = mapOf(
        "earth" to (55f to 1f),
        "jupiter" to (45f to 1f),
        "saturn" to (50f to 0.9f),
        "uranus" to (40f to 0.8f),
        "neptune" to (40f to 0.7f),
        "titan" to (70f to 0.25f),  // 处于土星磁层内，极弱
        "ganymede" to (60f to 0.55f) // 唯一已知有自身磁场的卫星
    )

    fun auroraCapability(p: Planet): Float = AURORA_TABLE[p.id]?.second ?: 0f
    fun auroraMinLatitude(p: Planet): Float = AURORA_TABLE[p.id]?.first ?: 90f

    /** 彩虹：太阳高度 2°–42°（高于 42° 时虹顶落到地平线下，看不到），且需要降水。 */
    fun rainbowVisible(p: Planet, sunAltDeg: Float, precipIntensity: Float): Boolean {
        if (precipIntensity <= 0.02f) return false
        return when (p.id) {
            // 地球水滴彩虹的经典太阳高度范围。
            "earth" -> sunAltDeg in 2f..42f && rainCapability(p) > 0f
            // 土卫六可能出现甲烷液滴彩虹；折射率不同，采用更宽、更高的示意角度范围。
            "titan" -> sunAltDeg in 5f..50f && rainCapability(p) > 0f
            // 金星硫酸液滴降水发生在高空，不能画成观察者地面视角的普通彩虹。
            else -> false
        }
    }

    /** 日晕：需要薄云（卷云，这里近似为「有云但不算阴」）且太阳在地平线以上。 */
    fun haloVisible(p: Planet, sunAltDeg: Float, cloudCover: Float): Boolean =
        // 当前绘制的是地球冰晶卷云 22° 日晕近似；不把它错误套用到硫酸云或气态巨行星云顶。
        p.id == "earth" && sunAltDeg > 3f && cloudCover in 0.15f..0.80f

    /** 极光：需要磁场、纬度足够高、并且是夜空（太阳低于 -6°）。 */
    fun auroraVisible(p: Planet, latitudeDeg: Float, sunAltDeg: Float): Boolean =
        auroraCapability(p) > 0f &&
            abs(latitudeDeg) >= auroraMinLatitude(p) &&
            sunAltDeg < -6f

    /** 霾 / 沙尘：气溶胶厚或者地表是尘暴环境。 */
    fun hazeCapability(p: Planet): Float = when {
        p.surfaceKind == SurfaceKind.DUST -> 0.9f
        p.aerosolTau >= 0.25 -> 0.8f
        p.aerosolTau >= 0.08 -> 0.45f
        else -> 0.1f
    }

    /** 闪电：只有暴雨级的对流降水才打雷；火星的尘暴不算。 */
    fun lightningVisible(p: Planet, precipLevel: Int): Boolean =
        rainCapability(p) >= 0.75f && precipLevel >= 3

    /**
     * 天气自动演变：完全确定性的时间函数，不联网、不用随机数。
     * 云量用两个周期（约 6 小时与 2.3 小时）的正弦叠加缓慢起伏，降水只在云量 ≥ 多云时出现。
     */
    fun autoWeather(uctSeconds: Double): WeatherState {
        val t = uctSeconds / 3600.0
        val cloudWave = 0.5 + 0.34 * sin(2.0 * PI * t / 6.0) + 0.16 * sin(2.0 * PI * t / 2.3 + 1.0)
        val cloudLevel = (cloudWave.coerceIn(0.0, 0.999) * 4.0).toInt().coerceIn(0, 3)
        val precipLevel = if (cloudLevel < 2) 0 else {
            val pw = 0.5 + 0.4 * sin(2.0 * PI * t / 1.3 + 2.0)
            (pw.coerceIn(0.0, 0.999) * 4.0).toInt().coerceIn(0, 3)
        }
        return WeatherState(cloudLevel = cloudLevel, precipLevel = precipLevel, auto = true)
    }

    /** 若处于自动模式，则返回按时间演变后的天气，否则原样返回。 */
    fun resolveWeather(w: WeatherState, uctSeconds: Double): WeatherState =
        if (w.auto) autoWeather(uctSeconds) else w
}
