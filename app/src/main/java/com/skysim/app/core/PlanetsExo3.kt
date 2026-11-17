package com.skysim.app.core

/**
 * v0.8.3 新增的 8 颗系外行星，分布在 5 个恒星系统里。
 *
 * 数据来源（2026-11-07 取自 NASA Exoplanet Archive 的 `pscomppars` 表，TAP 接口）：
 * - Kepler-22 系统：Bonomo et al. 2023（A&A 677, A33，3661 条 HARPS-N 径向速度）为归档默认解；
 *   原始发现论文 Borucki et al. 2012（ApJ 745, 120）给的是 2.38 R⊕ / 0.849 AU / 262 K。
 * - Kepler-452 系统：Jenkins et al. 2015（归档默认行，384.843 d、1.046 AU、1.63 R⊕、265 K）。
 * - Kepler-186 系统：Quintana et al. 2014 发现，归档默认行给 b/c/d/e 的半径与平衡温度；
 *   质量一律是 "M-R relationship"（半径—质量经验关系），不是实测值。
 * - Proxima Centauri：Faria et al. 2022 发现 Proxima d；2025 年 NIRPS 结果（arXiv:2507.21751）
 *   联合 HARPS 档案数据确认 d 并精化 b 与 d 的参数。Proxima c 未收录——2022 年重分析把
 *   ~1900 d 信号归给 HARPS 系统误差，2025 年 NIRPS 只给「不确定」，至今不是已确认行星。
 * - K2-18 c：归档默认行 8.962 d / 0.060 AU / Msini 7.51 M⊕ / 363 K（2017 年 HARPS 发现）；
 *   Radica et al. 2022 重分析给 9.2072 d / 6.99 M⊕，两套值不一致，本表取归档默认解并在此注明。
 *
 * 大气口径：这 8 颗**全部没有任何大气实测**，因此 confidence 一律不是 OBSERVED，
 * 界面上会显示「推断，无大气实测」。没有大气实测时不允许凭空给气压与光学厚度，
 * 因此 rayTau = aerosolTau = 0、pressureAtm = 0，晨昏分层为空表（无大气天体不得出现晨昏色带）。
 *
 * 半径／质量的口径差异也逐颗写明：
 * - Kepler-186 b–e、Kepler-452 b 的质量来自 M-R 关系（模型）；Kepler-22 b 的 9.10 M⊕ 归档标 "Mass"，
 *   但发现论文明确说质量从未测到，只有 3σ 上限 124 M⊕，故按「质量上限」处理。
 * - Proxima b/d 与 K2-18 c 都没有凌星，半径不是实测（归档给的是模型填充值），
 *   因此这三颗的质量用 Msini（最小质量），半径标注为模型值。
 */
object ExoPlanets3 {

    private const val R_EARTH_KM = 6371.0

    private fun g(massE: Double, radiusE: Double): Double =
        if (radiusE <= 0.0) 0.0 else massE / (radiusE * radiusE) * 9.81

    /**
     * 统一构造：无大气实测的系外行星。
     *
     * 自转周期一律没有测量。短周期（P < 10 d）按潮汐锁定假设（1 自转 = 1 公转），
     * 其余按 24 小时假设；两种都在 [skyBasisZh] 里写明是假设。
     */
    @Suppress("LongParameterList")
    private fun airless(
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
        ground: Rgb,
        night: Rgb,
        surface: SurfaceKind,
        locked: Boolean,
        extra: String
    ): Planet {
        val star = StarData.byId(host)
        val basis = buildString {
            append("${star.nameZh}（${star.tempK.toInt()} K，${Stars.spectralTypeOf(star.tempK).name} 型）")
            append(" + $composition")
            if (locked) append("；自转未测，按潮汐锁定假设（永昼面母星固定）")
            else append("；自转未测，按 24 小时假设")
            if (extra.isNotEmpty()) append("；").append(extra)
        }
        return Planet(
            id = id, nameZh = nameZh, nameEn = nameZh, kindZh = kindZh,
            radiusKm = radiusE * R_EARTH_KM,
            siderealPeriodOverrideSeconds = if (locked) 0.0 else 24.0 * 3600.0,
            spinOrbitRatio = if (locked) 1.0 else 0.0,
            orbitalPeriodDays = periodDays, axialTiltDeg = if (locked) 0.0 else 23.0,
            gravity = g(massE, radiusE),
            pressureAtm = 0.0, scaleHeightKm = 0.0, compositionZh = composition,
            sunDistanceAu = aAu, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.8f, 0.8f, 0.8f), groundTint = ground, nightTint = night,
            surfaceKind = surface,
            starNameZh = star.nameZh, starTempK = star.tempK, starRadiusSolar = star.radiusSolar,
            starLuminositySolar = star.luminositySolar, distanceLy = star.distanceLy,
            eqTempK = eqT, tidallyLocked = locked, confidence = DataConfidence.UNKNOWN,
            radiusEarths = radiusE, massEarths = massE,
            hostStarId = host, skyBasisZh = basis
        )
    }

    val all: List<Planet> = listOf(
        // ---- Kepler-22：首颗在类太阳恒星宜居带里确认的凌星行星（2011-12-05 宣布） ----
        airless(
            "kepler22b", "Kepler-22 b", "系外行星 · 宜居带超级地球", "kepler22",
            2.10, 9.10, 289.863876, 0.812, 279.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.40f, 0.34f, 0.28f), Rgb(0.0007f, 0.0006f, 0.0006f), SurfaceKind.ROCK,
            locked = false,
            extra = "半径 2.10±0.12 R⊕ 是 Bonomo 2023 的修订值（2011 年发现值 2.38 R⊕）；" +
                "质量 9.10 M⊕ 归档标为 Mass，但发现论文明确质量从未测到、只有 3σ 上限 124 M⊕，按上限处理；" +
                "偏心率 0.72 也只有 Bonomo 2023 一源支持"
        ),
        // ---- Kepler-452：G2 型、约 1800 光年、位于宜居带的超级地球 ----
        airless(
            "kepler452b", "Kepler-452 b", "系外行星 · 宜居带超级地球", "kepler452",
            1.63, 3.29, 384.843, 1.046, 265.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.42f, 0.36f, 0.29f), Rgb(0.0008f, 0.0007f, 0.0006f), SurfaceKind.ROCK,
            locked = false,
            extra = "母星与太阳同为 G2 型、有效温度 5757 K 只比太阳低 15 K；质量 3.29 M⊕ 来自 M-R 关系"
        ),
        // ---- Kepler-186：M1V，五颗行星全部为地球大小；f 是首颗宜居带地球大小行星 ----
        airless(
            "kepler186b", "Kepler-186 b", "系外行星 · 岩质", "kepler186",
            1.07, 1.24, 3.8867907, 0.03430, 571.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.44f, 0.33f, 0.29f), Rgb(0.0006f, 0.0004f, 0.0005f), SurfaceKind.ROCK,
            locked = true, extra = "平衡温度 571 K，是系统里最内侧的一颗"
        ),
        airless(
            "kepler186c", "Kepler-186 c", "系外行星 · 岩质", "kepler186",
            1.25, 2.10, 7.267302, 0.04510, 464.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.45f, 0.34f, 0.29f), Rgb(0.0006f, 0.0004f, 0.0005f), SurfaceKind.ROCK,
            locked = true, extra = "平衡温度 464 K"
        ),
        airless(
            "kepler186d", "Kepler-186 d", "系外行星 · 岩质", "kepler186",
            1.40, 2.54, 13.342996, 0.07810, 379.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.46f, 0.35f, 0.30f), Rgb(0.0006f, 0.0004f, 0.0005f), SurfaceKind.ROCK,
            locked = true, extra = "平衡温度 379 K"
        ),
        airless(
            "kepler186e", "Kepler-186 e", "系外行星 · 岩质", "kepler186",
            1.27, 2.15, 22.407704, 0.11000, 319.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.47f, 0.36f, 0.30f), Rgb(0.0006f, 0.0004f, 0.0005f), SurfaceKind.ROCK,
            locked = false, extra = "平衡温度 319 K；周期 22.4 d 已不保证潮汐锁定，按 24 小时假设"
        ),
        // ---- Proxima Centauri：离太阳最近的恒星，d 是 2022 年宣布、2025 年 NIRPS 确认的第四颗 ----
        airless(
            "proximad", "比邻星 d", "系外行星 · 亚地球", "proxima",
            0.692, 0.26, 5.12338, 0.02881, 282.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.50f, 0.30f, 0.26f), Rgb(0.0008f, 0.0005f, 0.0004f), SurfaceKind.ROCK,
            locked = true,
            extra = "Msini 只有 0.26 M⊕，是已知最轻的系外行星之一；没有凌星，半径 0.692 R⊕ 是模型填充值"
        ),
        // ---- K2-18 c：非凌星超级地球，与 b 同一系统 ----
        airless(
            "k218c", "K2-18 c", "系外行星 · 超级地球", "k218",
            2.65, 7.51, 8.962, 0.060, 363.0,
            "未知（无大气实测，保守按无大气）",
            Rgb(0.36f, 0.34f, 0.38f), Rgb(0.0010f, 0.0010f, 0.0014f), SurfaceKind.ROCK,
            locked = true,
            extra = "非凌星：半径 2.65 R⊕ 是模型填充值，质量用 Msini 7.51 M⊕；" +
                "Radica et al. 2022 重分析给 9.2072 d / 6.99 M⊕，与归档默认解不一致"
        )
    )
}
