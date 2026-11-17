package com.skysim.app.core

/**
 * v0.4 新增的 16 颗系外行星。
 *
 * 数据置信度约定（与界面上的标注一致）：
 * - OBSERVED：有光谱 / 凌星观测支持的大气特征（热木星、K2-18b、GJ 1214b、KELT-9b）。
 * - MODELED：只有质量、半径、平衡温度，大气成分由模型推断。
 * - UNKNOWN：连大气是否存在都未定，一律按「无大气」保守处理（天空全黑、无云无雨无虹）。
 *
 * 天空色的推导链：母星色温 → 黑体相对偏色（Stars.relativeColor）→ 乘以大气散射辐亮度；
 * 辐亮度又由 rayTau/aerosolTau（成分与云量）与 L/d²（母星亮度、轨道距离）决定。
 * 不是给每颗行星手写一个渐变色。
 */
object ExoPlanets {

    private const val R_EARTH_KM = 6371.0

    private fun g(massE: Double, radiusE: Double): Double =
        if (radiusE <= 0.0) 0.0 else massE / (radiusE * radiusE) * 9.81

    /** 母星参数统一从 [StarData] 取：行星条目里不再各存一份，避免两处数据打架。 */
    private fun st(id: String): Star = StarData.byId(id)

    val all: List<Planet> = listOf(
        Planet(
            id = "trappist1b", hostStarId = "trappist1", nameZh = "TRAPPIST-1 b", nameEn = "TRAPPIST-1 b", kindZh = "系外行星 · 岩质",
            radiusKm = 1.116 * R_EARTH_KM, orbitalPeriodDays = 1.5109, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(1.374, 1.116),
            pressureAtm = 0.0, scaleHeightKm = 8.0, compositionZh = "未知（保守按无大气）",
            sunDistanceAu = 0.01154, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.42f, 0.33f, 0.30f),
            nightTint = Rgb(0.0006f, 0.0004f, 0.0005f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("trappist1").nameZh, starTempK = st("trappist1").tempK, starRadiusSolar = st("trappist1").radiusSolar,
            starLuminositySolar = st("trappist1").luminositySolar, distanceLy = 40.7, eqTempK = 400.0,
            tidallyLocked = true, confidence = DataConfidence.UNKNOWN,
            radiusEarths = 1.116, massEarths = 1.374,
            skyBasisZh = "无大气 → 天空全黑；母星是 2566 K 的 M8 红矮星，近距轨道让它看起来明显比太阳大，但整颗星只有 0.055% 太阳光度"
        ),
        Planet(
            id = "trappist1c", hostStarId = "trappist1", nameZh = "TRAPPIST-1 c", nameEn = "TRAPPIST-1 c", kindZh = "系外行星 · 岩质",
            radiusKm = 1.097 * R_EARTH_KM, orbitalPeriodDays = 2.4218, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(1.308, 1.097),
            pressureAtm = 0.0, scaleHeightKm = 8.0, compositionZh = "未知（保守按无大气）",
            sunDistanceAu = 0.01580, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.44f, 0.35f, 0.31f),
            nightTint = Rgb(0.0006f, 0.0004f, 0.0005f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("trappist1").nameZh, starTempK = st("trappist1").tempK, starRadiusSolar = st("trappist1").radiusSolar,
            starLuminositySolar = st("trappist1").luminositySolar, distanceLy = 40.7, eqTempK = 342.0,
            tidallyLocked = true, confidence = DataConfidence.UNKNOWN,
            radiusEarths = 1.097, massEarths = 1.308,
            skyBasisZh = "无大气 → 天空全黑；母星偏红（2566 K）"
        ),
        Planet(
            id = "trappist1d", hostStarId = "trappist1", nameZh = "TRAPPIST-1 d", nameEn = "TRAPPIST-1 d", kindZh = "系外行星 · 岩质",
            radiusKm = 0.788 * R_EARTH_KM, orbitalPeriodDays = 4.0498, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(0.388, 0.788),
            pressureAtm = 0.4, scaleHeightKm = 9.0, compositionZh = "模型推测：薄 CO₂ 或 N₂ 大气",
            sunDistanceAu = 0.02227, rayTau = 0.006, aerosolTau = 0.05,
            aerosolTint = Rgb(0.86f, 0.78f, 0.66f), groundTint = Rgb(0.46f, 0.36f, 0.32f),
            nightTint = Rgb(0.0008f, 0.0006f, 0.0007f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("trappist1").nameZh, starTempK = st("trappist1").tempK, starRadiusSolar = st("trappist1").radiusSolar,
            starLuminositySolar = st("trappist1").luminositySolar, distanceLy = 40.7, eqTempK = 288.0,
            tidallyLocked = true, confidence = DataConfidence.MODELED,
            radiusEarths = 0.788, massEarths = 0.388,
            skyBasisZh = "薄 CO₂/N₂ 大气 + 红矮星：天空偏暗红褐、地平线暖色；照度只有地球的 3.6%"
        ),
        Planet(
            id = "trappist1e", hostStarId = "trappist1", nameZh = "TRAPPIST-1 e", nameEn = "TRAPPIST-1 e", kindZh = "系外行星 · 宜居带岩质",
            radiusKm = 0.920 * R_EARTH_KM, orbitalPeriodDays = 6.0996, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(0.692, 0.920),
            pressureAtm = 0.7, scaleHeightKm = 9.0, compositionZh = "模型推测：N₂/CO₂，可能有水",
            sunDistanceAu = 0.02925, rayTau = 0.010, aerosolTau = 0.06,
            aerosolTint = Rgb(0.72f, 0.76f, 0.84f), groundTint = Rgb(0.40f, 0.38f, 0.36f),
            nightTint = Rgb(0.0009f, 0.0007f, 0.0008f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("trappist1").nameZh, starTempK = st("trappist1").tempK, starRadiusSolar = st("trappist1").radiusSolar,
            starLuminositySolar = st("trappist1").luminositySolar, distanceLy = 40.7, eqTempK = 251.0,
            tidallyLocked = true, confidence = DataConfidence.MODELED,
            radiusEarths = 0.920, massEarths = 0.692,
            skyBasisZh = "模型大气 N₂/CO₂ + M 型母星：天空为偏暗的灰蓝并带暖调；潮汐锁定，永昼面太阳固定在天顶附近"
        ),
        Planet(
            id = "trappist1f", hostStarId = "trappist1", nameZh = "TRAPPIST-1 f", nameEn = "TRAPPIST-1 f", kindZh = "系外行星 · 岩质/冰质",
            radiusKm = 1.045 * R_EARTH_KM, orbitalPeriodDays = 9.2067, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(1.039, 1.045),
            pressureAtm = 0.0, scaleHeightKm = 8.0, compositionZh = "未知（保守按无大气）",
            sunDistanceAu = 0.03849, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.55f, 0.57f, 0.60f),
            nightTint = Rgb(0.0006f, 0.0005f, 0.0006f), surfaceKind = SurfaceKind.ICE,
            starNameZh = st("trappist1").nameZh, starTempK = st("trappist1").tempK, starRadiusSolar = st("trappist1").radiusSolar,
            starLuminositySolar = st("trappist1").luminositySolar, distanceLy = 40.7, eqTempK = 219.0,
            tidallyLocked = true, confidence = DataConfidence.UNKNOWN,
            radiusEarths = 1.045, massEarths = 1.039,
            skyBasisZh = "无大气 → 天空全黑；母星是一颗红色小星"
        ),
        Planet(
            id = "trappist1g", hostStarId = "trappist1", nameZh = "TRAPPIST-1 g", nameEn = "TRAPPIST-1 g", kindZh = "系外行星 · 岩质/冰质",
            radiusKm = 1.129 * R_EARTH_KM, orbitalPeriodDays = 12.3524, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(1.321, 1.129),
            pressureAtm = 0.0, scaleHeightKm = 8.0, compositionZh = "未知（保守按无大气）",
            sunDistanceAu = 0.04683, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.58f, 0.60f, 0.64f),
            nightTint = Rgb(0.0006f, 0.0005f, 0.0006f), surfaceKind = SurfaceKind.ICE,
            starNameZh = st("trappist1").nameZh, starTempK = st("trappist1").tempK, starRadiusSolar = st("trappist1").radiusSolar,
            starLuminositySolar = st("trappist1").luminositySolar, distanceLy = 40.7, eqTempK = 199.0,
            tidallyLocked = true, confidence = DataConfidence.UNKNOWN,
            radiusEarths = 1.129, massEarths = 1.321,
            skyBasisZh = "无大气 → 天空全黑；母星偏红"
        ),
        Planet(
            id = "trappist1h", hostStarId = "trappist1", nameZh = "TRAPPIST-1 h", nameEn = "TRAPPIST-1 h", kindZh = "系外行星 · 冰质",
            radiusKm = 0.755 * R_EARTH_KM, orbitalPeriodDays = 18.7665, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(0.331, 0.755),
            pressureAtm = 0.0, scaleHeightKm = 8.0, compositionZh = "未知（保守按无大气）",
            sunDistanceAu = 0.0619, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = Rgb(0.55f, 0.58f, 0.62f),
            nightTint = Rgb(0.01f, 0.01f, 0.02f), surfaceKind = SurfaceKind.ICE,
            starNameZh = "TRAPPIST-1", starTempK = 2566.0, starRadiusSolar = 0.1192,
            starLuminositySolar = 0.000553, distanceLy = 40.66, eqTempK = 168.0,
            tidallyLocked = true, confidence = DataConfidence.MODELED,
            radiusEarths = 0.755, massEarths = 0.331,
            skyBasisZh = "M8V 红矮星（2566 K）+ 无大气：天空近黑，母星又大又红的盘面"
        ),
        Planet(
            id = "proximab", hostStarId = "proxima", nameZh = "比邻星 b", nameEn = "Proxima Centauri b", kindZh = "系外行星 · 岩质",
            radiusKm = 1.07 * R_EARTH_KM, orbitalPeriodDays = 11.186, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(1.07, 1.07),
            pressureAtm = 0.5, scaleHeightKm = 10.0, compositionZh = "模型推测：CO₂/N₂，可能失水",
            sunDistanceAu = 0.04848, rayTau = 0.008, aerosolTau = 0.04,
            aerosolTint = Rgb(0.80f, 0.74f, 0.68f), groundTint = Rgb(0.44f, 0.38f, 0.34f),
            nightTint = Rgb(0.0008f, 0.0006f, 0.0007f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("proxima").nameZh, starTempK = st("proxima").tempK, starRadiusSolar = st("proxima").radiusSolar,
            starLuminositySolar = st("proxima").luminositySolar, distanceLy = 4.24, eqTempK = 234.0,
            tidallyLocked = true, confidence = DataConfidence.MODELED,
            radiusEarths = 1.07, massEarths = 1.07,
            skyBasisZh = "M5.5V（3042 K）红橙母星 + 模型薄大气：天空暗橙灰；耀斑会短时增亮"
        ),
        Planet(
            id = "kepler186f", hostStarId = "kepler186", nameZh = "Kepler-186 f", nameEn = "Kepler-186 f", kindZh = "系外行星 · 宜居带岩质",
            radiusKm = 1.17 * R_EARTH_KM, siderealPeriodOverrideSeconds = 86400.0000,
            orbitalPeriodDays = 129.94, axialTiltDeg = 23.0, gravity = g(1.4, 1.17),
            pressureAtm = 1.0, scaleHeightKm = 8.5, compositionZh = "模型推测：N₂/CO₂ 类地球",
            sunDistanceAu = 0.432, rayTau = 0.022, aerosolTau = 0.05,
            aerosolTint = Rgb(0.78f, 0.80f, 0.86f), groundTint = Rgb(0.40f, 0.42f, 0.38f),
            nightTint = Rgb(0.0008f, 0.0008f, 0.001f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("kepler186").nameZh, starTempK = st("kepler186").tempK, starRadiusSolar = st("kepler186").radiusSolar,
            starLuminositySolar = st("kepler186").luminositySolar, distanceLy = 580.0, eqTempK = 188.0,
            tidallyLocked = false, confidence = DataConfidence.MODELED,
            radiusEarths = 1.17, massEarths = 1.4,
            skyBasisZh = "M1V（3788 K）橙红母星 + 类地大气：天空为暗橙蓝的混合，正午照度约地球的 30%；自转周期未知，按 24 小时处理"
        ),
        Planet(
            id = "k218b", hostStarId = "k218", nameZh = "K2-18 b", nameEn = "K2-18 b", kindZh = "系外行星 · 亚海王星",
            radiusKm = 2.61 * R_EARTH_KM, orbitalPeriodDays = 32.94, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(8.6, 2.61),
            pressureAtm = 1.0, scaleHeightKm = 200.0, compositionZh = "观测：H₂/He + 水汽 + 甲烷（DMS 有争议）",
            sunDistanceAu = 0.1429, rayTau = 0.020, aerosolTau = 0.10,
            aerosolTint = Rgb(0.55f, 0.70f, 0.95f), groundTint = Rgb(0.20f, 0.30f, 0.55f),
            nightTint = Rgb(0.001f, 0.0012f, 0.0018f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starNameZh = st("k218").nameZh, starTempK = st("k218").tempK, starRadiusSolar = st("k218").radiusSolar,
            starLuminositySolar = st("k218").luminositySolar, distanceLy = 124.0, eqTempK = 255.0,
            tidallyLocked = true, confidence = DataConfidence.OBSERVED,
            radiusEarths = 2.61, massEarths = 8.6,
            skyBasisZh = "氢氦大气散射弱 + 水汽云：天空偏蓝青、云顶亮；母星 M2.5V 橙红"
        ),
        Planet(
            id = "hd189733b", hostStarId = "hd189733", nameZh = "HD 189733 b", nameEn = "HD 189733 b", kindZh = "系外行星 · 热木星",
            radiusKm = 12.7 * R_EARTH_KM, orbitalPeriodDays = 2.219, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(352.0, 12.7),
            pressureAtm = 1.0, scaleHeightKm = 600.0, compositionZh = "观测：H₂ 大气 + 硅酸盐云（钠、钾线）",
            sunDistanceAu = 0.03126, rayTau = 0.015, aerosolTau = 0.40,
            aerosolTint = Rgb(0.35f, 0.50f, 1.00f), groundTint = Rgb(0.16f, 0.28f, 0.62f),
            nightTint = Rgb(0.002f, 0.003f, 0.006f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starNameZh = st("hd189733").nameZh, starTempK = st("hd189733").tempK, starRadiusSolar = st("hd189733").radiusSolar,
            starLuminositySolar = st("hd189733").luminositySolar, distanceLy = 64.5, eqTempK = 1200.0,
            tidallyLocked = true, confidence = DataConfidence.OBSERVED,
            radiusEarths = 12.7, massEarths = 352.0,
            skyBasisZh = "硅酸盐雾霾强烈散射蓝光（观测到深蓝）：天空为饱和的深蓝；母星 K1V 偏橙"
        ),
        Planet(
            id = "wasp12b", hostStarId = "wasp12", nameZh = "WASP-12 b", nameEn = "WASP-12 b", kindZh = "系外行星 · 超热木星",
            radiusKm = 21.6 * R_EARTH_KM, orbitalPeriodDays = 1.091, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(446.0, 21.6),
            pressureAtm = 0.3, scaleHeightKm = 900.0, compositionZh = "观测：被母星加热膨胀，金属吸收强、反照率极低",
            sunDistanceAu = 0.0234, rayTau = 0.008, aerosolTau = 0.55,
            aerosolTint = Rgb(0.95f, 0.55f, 0.30f), groundTint = Rgb(0.35f, 0.16f, 0.08f),
            nightTint = Rgb(0.004f, 0.002f, 0.001f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starNameZh = st("wasp12").nameZh, starTempK = st("wasp12").tempK, starRadiusSolar = st("wasp12").radiusSolar,
            starLuminositySolar = st("wasp12").luminositySolar, distanceLy = 1400.0, eqTempK = 2500.0,
            tidallyLocked = true, confidence = DataConfidence.OBSERVED,
            radiusEarths = 21.6, massEarths = 446.0,
            skyBasisZh = "极低反照率 + 高温金属雾：天空暗橙褐、几乎不散射蓝；母星偏白且角直径远大于太阳"
        ),
        Planet(
            id = "51pegb", hostStarId = "51peg", nameZh = "51 Pegasi b", nameEn = "51 Pegasi b", kindZh = "系外行星 · 热木星",
            radiusKm = 14.2 * R_EARTH_KM, orbitalPeriodDays = 4.2308, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(150.0, 14.2),
            pressureAtm = 1.0, scaleHeightKm = 700.0, compositionZh = "观测：一氧化碳（CO）探测，氢氦大气",
            sunDistanceAu = 0.052, rayTau = 0.012, aerosolTau = 0.20,
            aerosolTint = Rgb(0.90f, 0.80f, 0.68f), groundTint = Rgb(0.35f, 0.28f, 0.22f),
            nightTint = Rgb(0.003f, 0.002f, 0.002f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starNameZh = st("51peg").nameZh, starTempK = st("51peg").tempK, starRadiusSolar = st("51peg").radiusSolar,
            starLuminositySolar = st("51peg").luminositySolar, distanceLy = 50.6, eqTempK = 1284.0,
            tidallyLocked = true, confidence = DataConfidence.OBSERVED,
            radiusEarths = 14.2, massEarths = 150.0,
            skyBasisZh = "氢氦大气 + 暖色尘霾：天空为暗黄橙；母星 G2IV 接近太阳色"
        ),
        Planet(
            id = "gj1214b", hostStarId = "gj1214", nameZh = "GJ 1214 b", nameEn = "GJ 1214 b", kindZh = "系外行星 · 水世界候选",
            radiusKm = 2.74 * R_EARTH_KM, orbitalPeriodDays = 1.5804, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(6.5, 2.74),
            pressureAtm = 1.0, scaleHeightKm = 150.0, compositionZh = "观测：特征less 霾层（水汽/氢氦不确定）",
            sunDistanceAu = 0.01505, rayTau = 0.018, aerosolTau = 0.35,
            aerosolTint = Rgb(0.85f, 0.87f, 0.92f), groundTint = Rgb(0.45f, 0.55f, 0.70f),
            nightTint = Rgb(0.001f, 0.0012f, 0.0015f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starNameZh = st("gj1214").nameZh, starTempK = st("gj1214").tempK, starRadiusSolar = st("gj1214").radiusSolar,
            starLuminositySolar = st("gj1214").luminositySolar, distanceLy = 48.0, eqTempK = 590.0,
            tidallyLocked = true, confidence = DataConfidence.OBSERVED,
            radiusEarths = 2.74, massEarths = 6.5,
            skyBasisZh = "高而均匀的霾层 → 天空呈乳白灰蓝、几乎无云隙；母星 M4.5V 橙红"
        ),
        Planet(
            id = "lhs1140b", hostStarId = "lhs1140", nameZh = "LHS 1140 b", nameEn = "LHS 1140 b", kindZh = "系外行星 · 岩质/水世界",
            radiusKm = 1.73 * R_EARTH_KM, orbitalPeriodDays = 24.737, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(6.98, 1.73),
            pressureAtm = 0.8, scaleHeightKm = 12.0, compositionZh = "模型推测：N₂/CO₂ 或蒸气为主的次生大气",
            sunDistanceAu = 0.0946, rayTau = 0.012, aerosolTau = 0.08,
            aerosolTint = Rgb(0.80f, 0.84f, 0.90f), groundTint = Rgb(0.55f, 0.62f, 0.70f),
            nightTint = Rgb(0.001f, 0.0011f, 0.0014f), surfaceKind = SurfaceKind.ICE,
            starNameZh = st("lhs1140").nameZh, starTempK = st("lhs1140").tempK, starRadiusSolar = st("lhs1140").radiusSolar,
            starLuminositySolar = st("lhs1140").luminositySolar, distanceLy = 49.0, eqTempK = 235.0,
            tidallyLocked = true, confidence = DataConfidence.MODELED,
            radiusEarths = 1.73, massEarths = 6.98,
            skyBasisZh = "模型次生大气 + M 型母星：天空偏冷灰蓝并带橙调；潮汐锁定，永昼面正午太阳固定"
        ),
        Planet(
            id = "teegardenb", hostStarId = "teegarden", nameZh = "Teegarden's Star b", nameEn = "Teegarden's Star b", kindZh = "系外行星 · 岩质",
            radiusKm = 1.05 * R_EARTH_KM, orbitalPeriodDays = 4.906, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(1.05, 1.05),
            pressureAtm = 0.4, scaleHeightKm = 8.0, compositionZh = "模型推测：薄大气（未观测）",
            sunDistanceAu = 0.0259, rayTau = 0.006, aerosolTau = 0.03,
            aerosolTint = Rgb(0.85f, 0.70f, 0.60f), groundTint = Rgb(0.46f, 0.38f, 0.34f),
            nightTint = Rgb(0.0007f, 0.0005f, 0.0006f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = st("teegarden").nameZh, starTempK = st("teegarden").tempK, starRadiusSolar = st("teegarden").radiusSolar,
            starLuminositySolar = st("teegarden").luminositySolar, distanceLy = 12.5, eqTempK = 301.0,
            tidallyLocked = true, confidence = DataConfidence.MODELED,
            radiusEarths = 1.05, massEarths = 1.05,
            skyBasisZh = "薄大气 + M7V（2900 K）：天空很暗且强烈偏红橙"
        ),
        Planet(
            id = "kelt9b", hostStarId = "kelt9", nameZh = "KELT-9 b", nameEn = "KELT-9 b", kindZh = "系外行星 · 超热木星",
            radiusKm = 21.2 * R_EARTH_KM, orbitalPeriodDays = 1.481, spinOrbitRatio = 1.0, axialTiltDeg = 0.0, gravity = g(890.0, 21.2),
            pressureAtm = 0.1, scaleHeightKm = 1000.0, compositionZh = "观测：迄今最热系外行星，氢大气被强烈剥离",
            sunDistanceAu = 0.03368, rayTau = 0.010, aerosolTau = 0.05,
            aerosolTint = Rgb(0.98f, 0.72f, 0.55f), groundTint = Rgb(0.30f, 0.14f, 0.08f),
            nightTint = Rgb(0.004f, 0.003f, 0.004f), surfaceKind = SurfaceKind.CLOUD_DECK,
            starNameZh = st("kelt9").nameZh, starTempK = st("kelt9").tempK, starRadiusSolar = st("kelt9").radiusSolar,
            starLuminositySolar = st("kelt9").luminositySolar, distanceLy = 670.0, eqTempK = 4050.0,
            tidallyLocked = true, confidence = DataConfidence.OBSERVED,
            radiusEarths = 21.2, massEarths = 890.0,
            skyBasisZh = "A0 型白蓝母星（10170 K）+ 稀薄高温大气：天空呈刺眼的蓝白、地平线泛白；母星又大又亮"
        )
    )
}
