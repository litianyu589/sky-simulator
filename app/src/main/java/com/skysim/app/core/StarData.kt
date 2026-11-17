package com.skysim.app.core

/**
 * 恒星（母星）数据：恒星目录及多恒星光源扩展。
 *
 * 温度、半径、光度、距离都是公开天文量的近似值；半径/光度用太阳单位。
 * 只有恒星、没有已知行星的星系会在 [StarCatalog] 里得到一个「空间站」观察点。
 */
data class Star(
    val id: String,
    val nameZh: String,
    val nameEn: String,
    val tempK: Double,
    val radiusSolar: Double,
    val luminositySolar: Double,
    val distanceLy: Double,
    val noteZh: String = "",
    /** 双星系统伴星参数；为空表示单星系统。 */
    val companionNameZh: String? = null,
    val companionTempK: Double? = null,
    val companionRadiusSolar: Double? = null,
    val companionLuminositySolar: Double? = null,
    /** 双星在行星天空中的示意角分离；不是实时轨道解。 */
    val companionSeparationDeg: Double = 0.0
) {
    val isBinary: Boolean
        get() = companionTempK != null && companionRadiusSolar != null &&
            companionLuminositySolar != null && companionNameZh != null

    val spectralTypeZh: String get() = Stars.spectralTypeOf(tempK).labelZh
    val spectralCode: String get() = Stars.spectralTypeOf(tempK).name
    /** 相对太阳的偏色（太阳 = (1,1,1)） */
    val color: Rgb get() = Stars.relativeColor(tempK)
    val isSun: Boolean get() = id == "sun"
}

object StarData {

    val all: List<Star> = listOf(
        Star("sun", "太阳", "Sun", 5772.0, 1.0, 1.0, 0.0, "太阳系唯一恒星"),
        // 已有系外行星的宿主星
        Star("trappist1", "TRAPPIST-1", "TRAPPIST-1", 2566.0, 0.1192, 0.000553, 40.7, "超冷红矮星，本应用收录 7 颗地球大小行星（b–h）"),
        Star("proxima", "比邻星", "Proxima Centauri", 2900.0, 0.141, 0.00151, 4.24, "离太阳最近的恒星"),
        Star("kepler186", "Kepler-186", "Kepler-186", 3755.0, 0.523, 0.054954, 580.0, "M1V，含宜居带行星 f"),
        Star("k218", "K2-18", "K2-18", 3457.0, 0.411, 0.0253, 124.0, "M2.5V，b 有观测到水汽"),
        Star("hd189733", "HD 189733", "HD 189733", 5052.0, 0.75, 0.345788, 64.5, "K1V，b 是经典热木星"),
        Star("wasp12", "WASP-12", "WASP-12", 6265.0, 1.69, 3.954668, 1400.0, "G0V，b 被母星严重潮汐加热"),
        Star("51peg", "51 Pegasi", "51 Pegasi", 5761.0, 1.19, 1.368705, 50.6, "首颗被发现系外行星的类太阳恒星"),
        Star("gj1214", "GJ 1214", "GJ 1214", 3101.0, 0.2162, 0.0039, 48.0, "M4.5V，b 是「水世界」候选"),
        Star("lhs1140", "LHS 1140", "LHS 1140", 3096.0, 0.2159, 0.0038, 49.0, "M4.5V，b 是致密超级地球"),
        Star("teegarden", "Teegarden's Star", "Teegarden's Star", 3034.0, 0.12, 0.000722, 12.5, "M7V，很近的极低质量恒星"),
        Star("kelt9", "KELT-9", "KELT-9", 9270.0, 2.418, 38.900036, 670.0, "A0V，b 是最热的系外行星之一"),
        // v0.5 新增：多行星系统
        Star("hr8799", "HR 8799", "HR 8799", 7400.0, 1.49338, 5.413399, 133.3, "A5V，四颗巨行星被直接成像"),
        Star("toi700", "TOI-700", "TOI-700", 3459.0, 0.4104, 0.0233, 101.4, "M2V，d 位于宜居带"),
        Star("kepler90", "Kepler-90", "Kepler-90", 6015.0, 1.29, 1.855795, 2790.0, "G0V，已知行星最多的系统之一"),
        Star("55cnc", "55 Cancri", "55 Cancri", 5198.0, 0.98, 0.635404, 41.0, "G8V，含超短周期熔岩行星 e"),
        Star("k23", "K2-3", "K2-3", 3844.0, 0.546, 0.0587, 147.0, "M0V，三颗迷你海王星"),
        Star("kepler11", "Kepler-11", "Kepler-11", 5663.0, 1.065, 1.318864, 2150.0, "G2V，紧凑多行星系统"),
        Star("kepler442", "Kepler-442", "Kepler-442", 4402.0, 0.598, 0.11695, 1206.0, "K5V，b 是宜居带超级地球"),
        Star("gj1132", "GJ 1132", "GJ 1132", 3229.0, 0.2211, 0.00477, 41.0, "M3.5V，b 有大气迹象"),
        Star("hd209458", "HD 209458", "HD 209458", 6091.0, 1.19, 1.70161, 159.0, "G0V，Osiris 是首个凌星热木星"),
        Star("wasp39", "WASP-39", "WASP-39", 5485.0, 0.939, 0.744869, 700.0, "G8V，b 有二氧化碳观测"),
        // v0.8.3 新增：Kepler-22 与 Kepler-452（数值取自 NASA Exoplanet Archive pscomppars 默认行）
        Star("kepler22", "Kepler-22", "Kepler-22", 5596.0, 0.869, 0.6454, 634.9,
            "G5 V；首颗在类太阳恒星宜居带确认的凌星行星（b）的母星，Bonomo 2023 归档解，[Fe/H] −0.255"),
        Star("kepler452", "Kepler-452", "Kepler-452", 5757.0, 1.11, 1.2146, 1799.4,
            "G2 型；有效温度只比太阳低 15 K，含宜居带超级地球 b"),
        // v0.5 新增：只有恒星、没有已知行星（空间站用例）
        Star("sirius", "天狼星 A", "Sirius A", 9940.0, 1.711, 25.4, 8.6, "夜空最亮恒星，A1V"),
        Star("vega", "织女星", "Vega", 9602.0, 2.362, 40.12, 25.04, "A0V，曾被用作光度基准"),
        Star("regulus", "轩辕十四", "Regulus", 12460.0, 3.09, 316.0, 79.3, "B8IV，快速自转的蓝白星"),
        Star("procyon", "南河三 A", "Procyon A", 6530.0, 2.048, 6.93, 11.46, "F5IV，正在离开主序"),
        Star("alphacena", "南门二 A", "Alpha Centauri A", 5790.0, 1.2234, 1.519, 4.37, "G2V，最近的类太阳恒星"),
        Star("arcturus", "大角星", "Arcturus", 4286.0, 25.4, 170.0, 36.7, "K1.5III 橙巨星"),
        Star("wolf359", "沃夫 359", "Wolf 359", 2800.0, 0.16, 0.0014, 7.86, "M6V，离太阳很近的红矮星"),
        Star("kepler16", "Kepler-16 A", "Kepler-16 A", 4450.0, 0.6489, 0.125023, 245.0, "K 型主星；环双星行星 Kepler-16 b 的宿主双星之一",
            companionNameZh = "Kepler-16 B",
            companionTempK = 3311.0,
            companionRadiusSolar = 0.2262,
            companionLuminositySolar = 0.0057,
            companionSeparationDeg = 8.0
        )
    )

    private val index: Map<String, Star> = all.associateBy { it.id }

    fun byId(id: String): Star = index[id] ?: all[0]

    /** 恒星系统可见光源列表；第二颗恒星是示意位置，未求解实时双星轨道。 */
    fun visualSources(starId: String, distanceAu: Double): List<StellarSource> {
        val primary = byId(starId)
        val sources = mutableListOf(
            StellarSource(primary.nameZh, primary.tempK, primary.radiusSolar,
                primary.luminositySolar, distanceAu, 0.0, primary.isBinary)
        )
        if (primary.isBinary) {
            sources += StellarSource(primary.companionNameZh!!, primary.companionTempK!!,
                primary.companionRadiusSolar!!, primary.companionLuminositySolar!!,
                distanceAu, primary.companionSeparationDeg, true)
        }
        return sources
    }
}

/** 一个可参与天空渲染的恒星光源。 */
data class StellarSource(
    val nameZh: String,
    val temperatureK: Double,
    val radiusSolar: Double,
    val luminositySolar: Double,
    val distanceAu: Double,
    /** 相对主星的示意方位角偏移；双星暂不进行真实轨道积分。 */
    val azimuthOffsetDeg: Double,
    val isBinaryComponent: Boolean
) {
    val color: Rgb get() = Stars.relativeColor(temperatureK)
    val angularDiameterDeg: Double
        get() = (2.0 * kotlin.math.atan(radiusSolar * Planet.SUN_RADIUS_KM /
            (distanceAu * Planet.AU_KM)) * 180.0 / Math.PI)
    val irradianceSolar: Double get() = luminositySolar / (distanceAu * distanceAu)
}
