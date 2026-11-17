package com.skysim.app.core

/**
 * v0.5 起分批新增的 25 颗系外行星，分布在 10 个新恒星系统里（[StarData] 中同 id 的恒星）。
 *
 * 每个条目只填可查的物理量（半径、质量、周期、半长轴、平衡温度）与大气口径，
 * 母星温度/半径/光度/距离从 [StarData] 取，避免两份数据打架；
 * 天空的推导依据由 [exo] 统一生成，不再手写「天空长什么样」。
 */
object ExoPlanets2 {

    private const val R_EARTH_KM = 6371.0

    private fun g(massE: Double, radiusE: Double): Double =
        if (radiusE <= 0.0) 0.0 else massE / (radiusE * radiusE) * 9.81

    /**
     * 统一构造：潮汐锁定的行星「1 自转 = 1 公转」，自转未知的按 24 小时（并在说明里注明假设）。
     */
    @Suppress("LongParameterList")
    private fun exo(
        id: String,
        nameZh: String,
        kindZh: String,
        host: String,
        radiusE: Double,
        massE: Double,
        periodDays: Double,
        aAu: Double,
        eqT: Double,
        composition: String,
        confidence: DataConfidence,
        ray: Double,
        aer: Double,
        tint: Rgb,
        ground: Rgb,
        night: Rgb,
        surface: SurfaceKind,
        locked: Boolean = true,
        rotationHours: Double = 0.0,
        extra: String = ""
    ): Planet {
        val star = StarData.byId(host)
        // 恒星日是数据；太阳日由 1/T_sid − 1/T_orbit 推出，不再自己乘一个 86400 了事
        val sidSeconds = if (locked) periodDays * 86400.0 else rotationHours * 3600.0
        val basis = buildString {
            append("${star.nameZh}（${star.tempK.toInt()} K，${Stars.spectralTypeOf(star.tempK).name} 型）")
            append(" + $composition")
            if (locked) append("；潮汐锁定：永昼面母星固定") else append("；自转未知，按 ${rotationHours.toInt()} 小时假设")
            if (extra.isNotEmpty()) append("；").append(extra)
        }
        return Planet(
            id = id, nameZh = nameZh, nameEn = nameZh, kindZh = kindZh,
            radiusKm = radiusE * R_EARTH_KM,
            siderealPeriodOverrideSeconds = if (locked) 0.0 else sidSeconds,
            spinOrbitRatio = if (locked) 1.0 else 0.0,
            orbitalPeriodDays = periodDays, axialTiltDeg = if (locked) 0.0 else 23.0,
            gravity = g(massE, radiusE),
            pressureAtm = if (ray > 0.0 || aer > 0.0) 1.0 else 0.0,
            scaleHeightKm = if (radiusE > 6.0) 400.0 else 10.0,
            compositionZh = composition,
            sunDistanceAu = aAu, rayTau = ray, aerosolTau = aer,
            aerosolTint = tint, groundTint = ground, nightTint = night,
            surfaceKind = surface,
            starNameZh = star.nameZh, starTempK = star.tempK, starRadiusSolar = star.radiusSolar,
            starLuminositySolar = star.luminositySolar, distanceLy = star.distanceLy,
            eqTempK = eqT, tidallyLocked = locked, confidence = confidence,
            radiusEarths = radiusE, massEarths = massE,
            hostStarId = host, skyBasisZh = basis
        )
    }

    val all: List<Planet> = listOf(
        // ---- HR 8799：A5V 年轻系统，四颗巨行星被直接成像（观测） ----
        exo(
            "hr8799b", "HR 8799 b", "系外行星 · 直接成像巨行星", "hr8799",
            13.4, 2224.0, 168_015.0, 68.0, 1100.0,
            "观测：直接成像，年轻巨行星、强云层", DataConfidence.OBSERVED,
            0.01, 0.45, Rgb(0.95f, 0.62f, 0.45f), Rgb(0.35f, 0.18f, 0.12f), Rgb(0.002f, 0.001f, 0.001f),
            SurfaceKind.CLOUD_DECK, locked = false, rotationHours = 24.0, extra = "轨道 68 AU，一年约 460 地球年"
        ),
        exo(
            "hr8799c", "HR 8799 c", "系外行星 · 直接成像巨行星", "hr8799",
            13.4, 2224.0, 69_398.0, 38.0, 1200.0,
            "观测：直接成像，光谱含 CO/H₂O", DataConfidence.OBSERVED,
            0.01, 0.40, Rgb(0.93f, 0.60f, 0.48f), Rgb(0.33f, 0.17f, 0.12f), Rgb(0.002f, 0.001f, 0.001f),
            SurfaceKind.CLOUD_DECK, locked = false, rotationHours = 24.0, extra = "轨道 38 AU"
        ),
        exo(
            "hr8799d", "HR 8799 d", "系外行星 · 直接成像巨行星", "hr8799",
            13.4, 2224.0, 36_525.0, 24.0, 1300.0,
            "观测：直接成像", DataConfidence.OBSERVED,
            0.01, 0.38, Rgb(0.95f, 0.66f, 0.50f), Rgb(0.34f, 0.18f, 0.13f), Rgb(0.002f, 0.001f, 0.001f),
            SurfaceKind.CLOUD_DECK, locked = false, rotationHours = 24.0, extra = "轨道 24 AU"
        ),
        exo(
            "hr8799e", "HR 8799 e", "系外行星 · 直接成像巨行星", "hr8799",
            13.4, 2224.0, 18_628.0, 16.4, 1500.0,
            "观测：直接成像 + 干涉光谱（含铁、硅酸盐云）", DataConfidence.OBSERVED,
            0.01, 0.50, Rgb(0.98f, 0.70f, 0.45f), Rgb(0.38f, 0.20f, 0.12f), Rgb(0.002f, 0.001f, 0.001f),
            SurfaceKind.CLOUD_DECK, locked = false, rotationHours = 24.0, extra = "轨道 14.5 AU，是四颗里最热的一颗"
        ),
        // ---- TOI-700：M2V，三颗行星，d 在宜居带 ----
        exo(
            "toi700b", "TOI-700 b", "系外行星 · 岩质", "toi700",
            0.91, 0.85, 9.977, 0.0678, 400.0,
            "未知（保守按无大气）", DataConfidence.UNKNOWN,
            0.0, 0.0, Rgb(0.8f, 0.8f, 0.8f), Rgb(0.45f, 0.36f, 0.32f), Rgb(0.0006f, 0.0004f, 0.0005f),
            SurfaceKind.ROCK
        ),
        exo(
            "toi700c", "TOI-700 c", "系外行星 · 迷你海王星", "toi700",
            2.65, 7.7, 16.05, 0.0931, 330.0,
            "模型推测：富挥发分的氢气/水汽包层", DataConfidence.MODELED,
            0.02, 0.12, Rgb(0.62f, 0.72f, 0.92f), Rgb(0.25f, 0.32f, 0.50f), Rgb(0.001f, 0.0012f, 0.0018f),
            SurfaceKind.CLOUD_DECK
        ),
        exo(
            "toi700d", "TOI-700 d", "系外行星 · 宜居带岩质", "toi700",
            1.19, 1.7, 37.43, 0.1642, 269.0,
            "模型推测：可能保留薄大气（未观测到）", DataConfidence.MODELED,
            0.008, 0.05, Rgb(0.74f, 0.78f, 0.86f), Rgb(0.40f, 0.42f, 0.38f), Rgb(0.0008f, 0.0008f, 0.001f),
            SurfaceKind.ROCK
        ),
        // ---- Kepler-90：G0V，三颗（其中 i 是机器学习发现） ----
        exo(
            "kepler90b", "Kepler-90 b", "系外行星 · 超级地球", "kepler90",
            1.31, 2.4, 7.008, 0.074, 700.0,
            "未知（保守按无大气）", DataConfidence.UNKNOWN,
            0.0, 0.0, Rgb(0.8f, 0.8f, 0.8f), Rgb(0.44f, 0.36f, 0.32f), Rgb(0.0006f, 0.0004f, 0.0005f),
            SurfaceKind.ROCK
        ),
        exo(
            "kepler90c", "Kepler-90 c", "系外行星 · 超级地球", "kepler90",
            1.19, 2.1, 8.72, 0.089, 650.0,
            "未知（保守按无大气）", DataConfidence.UNKNOWN,
            0.0, 0.0, Rgb(0.8f, 0.8f, 0.8f), Rgb(0.46f, 0.38f, 0.33f), Rgb(0.0006f, 0.0004f, 0.0005f),
            SurfaceKind.ROCK
        ),
        exo(
            "kepler90i", "Kepler-90 i", "系外行星 · 岩质", "kepler90",
            1.32, 2.3, 14.45, 0.12014, 550.0,
            "未知（保守按无大气）", DataConfidence.UNKNOWN,
            0.0, 0.0, Rgb(0.8f, 0.8f, 0.8f), Rgb(0.47f, 0.39f, 0.34f), Rgb(0.0006f, 0.0004f, 0.0005f),
            SurfaceKind.ROCK
        ),
        // ---- 55 Cancri：G8V，四颗，含超短周期熔岩行星 e ----
        exo(
            "55cncb", "55 Cancri b", "系外行星 · 热木星", "55cnc",
            9.3, 264.0, 14.65, 0.118, 700.0,
            "模型推测：氢氦大气（径向速度测量，无凌星）", DataConfidence.MODELED,
            0.012, 0.18, Rgb(0.86f, 0.74f, 0.62f), Rgb(0.30f, 0.24f, 0.20f), Rgb(0.003f, 0.002f, 0.002f),
            SurfaceKind.CLOUD_DECK
        ),
        exo(
            "55cncc", "55 Cancri c", "系外行星 · 气态巨行星", "55cnc",
            11.0, 76.0, 44.4, 0.247, 480.0,
            "模型推测：氢氦大气", DataConfidence.MODELED,
            0.012, 0.15, Rgb(0.88f, 0.78f, 0.66f), Rgb(0.32f, 0.26f, 0.22f), Rgb(0.003f, 0.002f, 0.002f),
            SurfaceKind.CLOUD_DECK
        ),
        exo(
            "55cncf", "55 Cancri f", "系外行星 · 气态巨行星", "55cnc",
            11.0, 45.0, 260.0, 0.802, 260.0,
            "模型推测：氢氦大气（位于宜居带边缘）", DataConfidence.MODELED,
            0.012, 0.14, Rgb(0.90f, 0.84f, 0.72f), Rgb(0.34f, 0.30f, 0.26f), Rgb(0.003f, 0.002f, 0.002f),
            SurfaceKind.CLOUD_DECK, locked = false, rotationHours = 24.0
        ),
        exo(
            "55cnce", "55 Cancri e", "系外行星 · 熔岩超级地球", "55cnc",
            2.0, 8.08, 0.7365, 0.01544, 1950.0,
            "观测：凌星 + 二次食，昼面高温、可能有熔岩", DataConfidence.OBSERVED,
            0.006, 0.10, Rgb(0.98f, 0.45f, 0.22f), Rgb(0.45f, 0.12f, 0.06f), Rgb(0.004f, 0.001f, 0.001f),
            SurfaceKind.ROCK, extra = "轨道周期不到 18 小时"
        ),
        // ---- K2-3：M0V，三颗迷你海王星 ----
        exo(
            "k23b", "K2-3 b", "系外行星 · 迷你海王星", "k23",
            2.1, 6.6, 10.05, 0.0747, 460.0,
            "模型推测：挥发性包层", DataConfidence.MODELED,
            0.018, 0.12, Rgb(0.66f, 0.74f, 0.90f), Rgb(0.26f, 0.32f, 0.46f), Rgb(0.001f, 0.0012f, 0.0016f),
            SurfaceKind.CLOUD_DECK
        ),
        exo(
            "k23c", "K2-3 c", "系外行星 · 迷你海王星", "k23",
            1.6, 2.7, 24.65, 0.1357, 340.0,
            "模型推测：薄挥发性包层", DataConfidence.MODELED,
            0.014, 0.08, Rgb(0.70f, 0.78f, 0.90f), Rgb(0.28f, 0.34f, 0.44f), Rgb(0.001f, 0.0012f, 0.0016f),
            SurfaceKind.CLOUD_DECK
        ),
        exo(
            "k23d", "K2-3 d", "系外行星 · 超级地球", "k23",
            1.5, 2.4, 44.57, 0.2014, 280.0,
            "模型推测：可能有薄大气", DataConfidence.MODELED,
            0.010, 0.06, Rgb(0.74f, 0.80f, 0.88f), Rgb(0.36f, 0.38f, 0.36f), Rgb(0.0008f, 0.0008f, 0.001f),
            SurfaceKind.ROCK
        ),
        // ---- Kepler-11：G2V，紧凑系统，三颗 ----
        exo(
            "kepler11b", "Kepler-11 b", "系外行星 · 超级地球", "kepler11",
            1.8, 4.3, 10.30, 0.091, 900.0,
            "模型推测：富氢薄包层", DataConfidence.MODELED,
            0.014, 0.10, Rgb(0.84f, 0.78f, 0.70f), Rgb(0.34f, 0.30f, 0.26f), Rgb(0.002f, 0.0015f, 0.0015f),
            SurfaceKind.ROCK
        ),
        exo(
            "kepler11c", "Kepler-11 c", "系外行星 · 迷你海王星", "kepler11",
            3.0, 13.5, 13.02, 0.107, 830.0,
            "模型推测：氢氦包层", DataConfidence.MODELED,
            0.016, 0.12, Rgb(0.76f, 0.80f, 0.88f), Rgb(0.28f, 0.32f, 0.42f), Rgb(0.002f, 0.0015f, 0.0015f),
            SurfaceKind.CLOUD_DECK
        ),
        exo(
            "kepler11e", "Kepler-11 e", "系外行星 · 迷你海王星", "kepler11",
            4.5, 8.0, 31.99, 0.195, 620.0,
            "模型推测：富氢包层（密度很低）", DataConfidence.MODELED,
            0.016, 0.14, Rgb(0.72f, 0.78f, 0.90f), Rgb(0.26f, 0.31f, 0.44f), Rgb(0.002f, 0.0015f, 0.0015f),
            SurfaceKind.CLOUD_DECK
        ),
        // ---- Kepler-442：K5V，宜居带超级地球 ----
        exo(
            "kepler442b", "Kepler-442 b", "系外行星 · 宜居带超级地球", "kepler442",
            1.34, 2.3, 112.3, 0.409, 233.0,
            "模型推测：可能为岩质并有薄大气", DataConfidence.MODELED,
            0.012, 0.06, Rgb(0.78f, 0.76f, 0.72f), Rgb(0.40f, 0.38f, 0.34f), Rgb(0.0008f, 0.0008f, 0.001f),
            SurfaceKind.ROCK, locked = false, rotationHours = 24.0
        ),
        // ---- GJ 1132：M3.5V，b 有大气迹象 ----
        exo(
            "gj1132b", "GJ 1132 b", "系外行星 · 岩质", "gj1132",
            1.13, 1.66, 1.628, 0.0157, 410.0,
            "模型推测：可能有次生大气（观测有争议）", DataConfidence.MODELED,
            0.010, 0.12, Rgb(0.80f, 0.72f, 0.66f), Rgb(0.42f, 0.30f, 0.26f), Rgb(0.0008f, 0.0006f, 0.0006f),
            SurfaceKind.ROCK
        ),
        // ---- HD 209458：G0V，Osiris ----
        exo(
            "hd209458b", "HD 209458 b", "系外行星 · 热木星", "hd209458",
            15.4, 226.0, 3.5247, 0.04707, 1450.0,
            "观测：钠线、氢逃逸（首个被探测大气的系外行星）", DataConfidence.OBSERVED,
            0.012, 0.30, Rgb(0.70f, 0.62f, 0.90f), Rgb(0.26f, 0.22f, 0.40f), Rgb(0.003f, 0.002f, 0.004f),
            SurfaceKind.CLOUD_DECK
        ),
        // ---- Kepler-16：环双星行星；第二光源由 StarData 的 companion 字段提供 ----
        exo(
            "kepler16b", "Kepler-16 b", "系外行星 · 环双星气态巨行星", "kepler16",
            8.45, 105.0, 228.776, 0.7048, 188.0,
            "氢氦为主的气态包层；大气细节为模型示意", DataConfidence.MODELED,
            0.008, 0.16, Rgb(0.72f, 0.68f, 0.58f), Rgb(0.34f, 0.30f, 0.24f),
            Rgb(0.001f, 0.001f, 0.001f), SurfaceKind.CLOUD_DECK,
            locked = false, rotationHours = 24.0,
            extra = "环绕双星运行；双太阳相对位置为示意值，未进行双星轨道积分"
        ),
        // ---- WASP-39：G8V，b 有 CO₂ 与水的观测 ----
        exo(
            "wasp39b", "WASP-39 b", "系外行星 · 热木星", "wasp39",
            14.2, 89.0, 4.055, 0.04828, 1166.0,
            "观测：水汽、二氧化碳、钠（JWST 透射光谱）", DataConfidence.OBSERVED,
            0.012, 0.26, Rgb(0.80f, 0.78f, 0.86f), Rgb(0.28f, 0.26f, 0.34f), Rgb(0.003f, 0.002f, 0.002f),
            SurfaceKind.CLOUD_DECK
        )
    )
}
