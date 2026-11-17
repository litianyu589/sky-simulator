package com.skysim.app.core

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.sqrt

/**
 * v0.7.0 晨昏分层的**物理量输出**。
 *
 * 屏幕上的颜色只能是这些量的渲染结果：[sky]、[glow]、[horizonLum] 与各阶段边界角。
 * 这里没有任何 `planetId` 分支：所有差异都来自数据表（光学厚度、相函数 g、臭氧柱、恒星视直径、地表明暗）。
 */
data class TwilightStage(
    /** 地球为民用蒙影边界；其它天体是模型浅暮边界 */
    val civilDeg: Double,
    /** 地球为航海蒙影边界；其它天体是模型深暮边界 */
    val nauticalDeg: Double,
    /** 地球为天文蒙影边界；其它天体是模型暗暮边界 */
    val astronomicalDeg: Double,
    /** 从日落到天文晨昏结束的时长（地球小时） */
    val durationHours: Double,
    /** 半影角宽度：恒星有限视直径 + 大气散射共同决定的晨昏带宽度（度） */
    val penumbraDeg: Double,
    /** 归一化大气不透明度（地球 = 1） */
    val opacity: Double
)

/**
 * 一条晨昏色带：太阳高度角的区间与该区间内的代表色。
 *
 * v0.8.1 起「分层」不再只是阈值：每条带的颜色直接取该带中点的 [Twilight.radiance]
 * 结果（朝向太阳 / 背向太阳各取一次），所以地球是橙红→橙黄→蓝，火星是砖橙→土黄→粉灰，
 * 金星只有一整片被照亮的硫酸云、没有可分辨的边界，月球一条都没有。
 */
data class TwilightBand(
    val labelZh: String,
    /** 太阳高度角上限（度，更接近 0） */
    val fromDeg: Double,
    /** 太阳高度角下限（度，更负） */
    val toDeg: Double,
    /** 朝太阳一侧的代表色（sRGB 编码后） */
    val towardSun: Rgb,
    /** 背向太阳一侧的代表色（sRGB 编码后） */
    val awayFromSun: Rgb,
    val noteZh: String
)

/** 某一太阳高度角下的天空物理量。 */
data class TwilightProfile(
    val sky: Rgb,
    val glow: Rgb,
    /** 地平线方向相对辐亮度（1 = 天顶） */
    val horizonLum: Double,
    val zenithLum: Double,
    /** 恒星视半径（度） */
    val sunRadiusDeg: Double,
    /** 日面是否还能分辨（厚云/浓雾时不可辨） */
    val sunDiscVisible: Boolean
)

object TwilightModel {

    /** 地球自身的不透明度，作为归一化基准 —— 不写死数值，直接读数据表。 */
    private val EARTH_RAW: Double by lazy { rawOpacity(Planets.byId("earth")) }

    /** 原始不透明度：瑞利 + 气溶胶（前向散射越强，等效不透明度越低）+ 臭氧。 */
    private fun rawOpacity(p: Planet): Double {
        val g = Twilight.mieG(p).toDouble()
        val scatter = p.rayTau + p.aerosolTau * (2.0 - g) + p.ozoneColumn * 0.02
        // 吸收气体把光吃掉而不是散开：甲烷/二氧化碳越多，晨昏越短
        val absorb = Math.exp(-(p.methaneColumn * 0.9 + p.co2Column * 0.12))
        return scatter * absorb
    }

    /** 归一化到地球 = 1。没有连续全球大气柱（包括局部羽流/外逸层）= 0。 */
    fun opacity(planet: Planet): Double =
        if (!planet.hasAtmosphere) 0.0 else rawOpacity(planet) / EARTH_RAW

    /**
     * 晨昏/暮光模型的三段边界角。
     *
     * 地球严格保持正式定义 6° / 12° / 18°。其它天体用 sqrt(归一化光学厚度)
     * 得到模型化的浅暮/深暮/暗暮范围；它是用于视觉模拟的经验缩放，不是通用天文定义，
     * 更不是完整球面辐射传输的严格解。无全球大气的天体为 0 / 0 / 0。
     */
    fun stage(planet: Planet): TwilightStage {
        val op = opacity(planet)
        val scale = sqrt(op.coerceAtLeast(0.0)).coerceIn(0.0, 3.0)
        val solarDayHours = planet.solarDaySeconds / 3600.0
        val astro = 18.0 * scale
        return TwilightStage(
            civilDeg = 6.0 * scale,
            nauticalDeg = 12.0 * scale,
            astronomicalDeg = astro,
            durationHours = if (astro.isFinite() && solarDayHours.isFinite()) astro / 360.0 * solarDayHours else 0.0,
            penumbraDeg = penumbraDeg(planet),
            opacity = op
        )
    }

    /**
     * 晨昏带半影宽度：太阳（恒星）视直径给出下限，大气散射把边界抹得更宽。
     * 地球：太阳视直径 0.53° → 半影下限 0.27°；加上散射约 5–6°。
     * TRAPPIST-1 e：恒星视直径约 2.2° → 半影下限 1.1°，是地球的 4 倍多。
     */
    fun penumbraDeg(planet: Planet): Double {
        val starHalf = planet.starAngularRadiusDeg
        val scatter = 4.7 * sqrt(opacity(planet).coerceAtLeast(0.0)).coerceIn(0.0, 3.0)
        return starHalf + scatter
    }

    /**
     * 地平线亮度随太阳高度角的 e 折时间常数（小时）。
     * 金星这类厚云世界的常数远大于地球：太阳落下 10° 天空还亮着。
     */
    fun decayTauHours(planet: Planet): Double {
        if (!planet.hasAtmosphere) return 0.0
        val l0 = horizonRadiance(planet, 0f)
        val l6 = horizonRadiance(planet, -6f)
        if (l0 <= 1e-6 || l6 <= 1e-9) return 0.0
        val tauDeg = 6.0 / ln(l0 / l6).coerceAtLeast(1e-3)
        return tauDeg / 360.0 * (planet.solarDaySeconds / 3600.0)
    }

    private fun horizonRadiance(planet: Planet, sunAlt: Float): Double {
        val rgb = Twilight.radiance(planet, 1.5f, 0f, sunAlt, 0f, 0.65f)
        return (rgb.r * 0.3 + rgb.g * 0.6 + rgb.b * 0.1).toDouble()
    }

    /** 给定太阳高度角的完整剖面。 */
    fun profile(planet: Planet, sunAltDeg: Float): TwilightProfile {
        val horizon = Twilight.radiance(planet, 1.5f, 0f, sunAltDeg, 0f, 0.65f)
        val zenith = Twilight.radiance(planet, 80f, 0f, sunAltDeg, 0f, 0.65f)
        val glow = Twilight.radiance(planet, 3f, 0f, sunAltDeg, 0f, 0.65f)
        fun lum(c: Rgb) = (c.r * 0.3 + c.g * 0.6 + c.b * 0.1).toDouble()
        val hz = lum(zenith).coerceAtLeast(1e-6)
        return TwilightProfile(
            sky = horizon,
            glow = glow,
            horizonLum = lum(horizon) / hz,
            zenithLum = lum(zenith),
            sunRadiusDeg = planet.starAngularRadiusDeg,
            sunDiscVisible = planet.aerosolTau < 1.5 && planet.hasAtmosphere
        )
    }

    /**
     * 这个天体是否有「可分辨的分层边界」。
     *
     * 判据是归一化不透明度：薄到中等大气（地球 1、火星约 3.3、土卫六一档）里，
     * 阳光进不到的那一层和还在阳光里的那一层之间有真实的边界，肉眼能看到一条条色带；
     * 光学厚度几十倍地球（金星 40 以上）时，多次散射把边界抹平，整片天空只是一起变暗。
     */
    fun hasDiscreteBands(planet: Planet): Boolean {
        if (!planet.hasAtmosphere) return false
        val op = opacity(planet)
        return op >= 0.05 && op < 8.0
    }

    /**
     * 该天体的晨昏色带表。颜色来自 [Twilight.radiance] 的实际计算，不是调色板。
     * 无大气（月球、水星）返回空表 —— 这是「没有晨昏分层」的物理结论，不是缺数据。
     */
    fun bands(planet: Planet, strength: Float = 0.65f): List<TwilightBand> {
        if (!planet.hasAtmosphere || opacity(planet) < 0.03) return emptyList()
        val st = stage(planet)
        if (st.astronomicalDeg < 0.5) return emptyList()
        val s = strength.coerceIn(0f, 1f)

        fun colourAt(sunAlt: Float, azDeg: Float): Rgb =
            SkyModel.toSrgb(Twilight.radiance(planet, 4f, azDeg, sunAlt, 0f, s))

        if (!hasDiscreteBands(planet)) {
            val mid = (-st.astronomicalDeg * 0.45).toFloat()
            return listOf(
                TwilightBand(
                    labelZh = "整片云顶变暗",
                    fromDeg = 0.0,
                    toDeg = -st.astronomicalDeg,
                    towardSun = colourAt(mid, 0f),
                    awayFromSun = colourAt(mid, 180f),
                    noteZh = "不透明度 %.0f×地球：多次散射抹平了边界，没有可分辨的色带".format(st.opacity)
                )
            )
        }

        val bounds = doubleArrayOf(0.0, st.civilDeg, st.nauticalDeg, st.astronomicalDeg)
        val labels = arrayOf("地平辉光带", "中层余晖带", "暗蓝尾带")
        val notes = arrayOf(
            colourNote(planet, 1),
            colourNote(planet, 2),
            colourNote(planet, 3)
        )
        val out = ArrayList<TwilightBand>(3)
        for (k in 0 until 3) {
            val from = bounds[k]
            val to = bounds[k + 1]
            if (to - from < 0.2) continue
            val mid = (-(from + to) / 2.0).toFloat()
            out.add(
                TwilightBand(
                    labelZh = labels[k],
                    fromDeg = if (from == 0.0) 0.0 else -from,
                    toDeg = -to,
                    towardSun = colourAt(mid, 0f),
                    awayFromSun = colourAt(mid, 180f),
                    noteZh = notes[k]
                )
            )
        }
        return out
    }

    private fun colourNote(planet: Planet, band: Int): String {
        val dusty = planet.aerosolTau > planet.rayTau * 8.0
        return when {
            planet.ozoneColumn > 0.3f && band == 2 -> "臭氧吸收黄绿，背日一侧偏粉紫"
            dusty && band == 1 -> "尘埃前向散射：朝太阳一侧橙褐、贴地平线最亮"
            dusty && band == 2 -> "尘埃后向散射的砖红余晖，没有臭氧紫带"
            dusty -> "尘埃把暮光拖得很长，天空整体偏土黄"
            band == 3 -> "只剩大气多次散射的深蓝底光"
            else -> "瑞利散射主导的冷色"
        }
    }

    /** 一句话解释这个天体的晨昏为什么长这样（UI / 文档用）。 */
    fun explain(planet: Planet): String {
        val st = stage(planet)
        return when {
            !planet.hasAtmosphere && planet.sodiumGlow <= 0.0 ->
                "无大气：太阳落山即夜，晨昏时长为 0，白天也能看到星星"
            !planet.hasAtmosphere ->
                "只有稀薄外逸层：晨昏几乎与真空一样锐利，仅有一层极弱的钠辉光"
            planet.id == "earth" ->
                "地球标准晨昏蒙影：民用 −%.1f°、航海 −%.1f°、天文 −%.1f°；模型日落后 %.2f 小时进入全黑"
                    .format(st.civilDeg, st.nauticalDeg, st.astronomicalDeg, st.durationHours)
            else ->
                "模型暮光范围（非地球标准分级）：不透明度 %.2f×地球，浅暮 / 深暮 / 暗暮边界 %.1f° / %.1f° / %.1f°，日落后约 %.2f 小时进入暗夜"
                    .format(st.opacity, st.civilDeg, st.nauticalDeg, st.astronomicalDeg, st.durationHours)
        }
    }
}
