package com.skysim.app.core

import kotlin.math.pow
import kotlin.math.sqrt

/** 一个恒星系统：恒星 + 该系统内的天体 + 可选的空间站观察点。 */
data class StarSystem(
    val star: Star,
    val bodies: List<Planet>,
    val station: Planet?
) {
    val planetCount: Int get() = bodies.count { it.group == PlanetGroup.EXOPLANET || it.group == PlanetGroup.PLANET }
    val hasPlanets: Boolean get() = bodies.isNotEmpty()
}

/**
 * 恒星 → 行星两级目录。
 *
 * 一级是恒星（30 颗，含太阳），二级是该恒星下的天体。
 * 只有恒星、没有已知行星的星系会自动生成一个「空间站」观察点：
 * 位置取等效应日照距离 a = √(L/L☉) AU（也就是该恒星宜居带中心），
 * 轨道周期用开普勒第三定律 + 质光关系 L ≈ M^3.5 估算，所以每颗恒星的站都不一样。
 */
object StarCatalog {

    val stars: List<Star> get() = StarData.all

    fun bodies(starId: String): List<Planet> = Planets.all.filter { it.hostStarId == starId }

    fun byId(starId: String): Star = StarData.byId(starId)

    fun visualSources(starId: String, distanceAu: Double): List<StellarSource> =
        StarData.visualSources(starId, distanceAu)

    /** 只有恒星、没有已知行星时才提供空间站 */
    fun stationFor(star: Star): Planet? {
        if (!star.isSun && bodies(star.id).isNotEmpty()) return null
        if (star.isSun) return null
        val a = sqrt(star.luminositySolar).coerceIn(0.004, 400.0)
        val mass = star.luminositySolar.pow(1.0 / 3.5).coerceIn(0.06, 30.0)
        val periodDays = 365.25 * sqrt(a * a * a / mass)
        return Planet(
            id = "station:${star.id}",
            nameZh = "空间站 · ${star.nameZh}".replace(" · 太阳", ""),
            nameEn = "Station",
            kindZh = "空间站 · 观察点",
            radiusKm = 0.1, siderealPeriodOverrideSeconds = periodDays * 86400.0,
            orbitalPeriodDays = periodDays, axialTiltDeg = 0.0, gravity = 0.0,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无大气（舱外真空）",
            sunDistanceAu = a, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.5f, 0.5f, 0.5f), groundTint = Rgb(0.10f, 0.11f, 0.13f),
            nightTint = Rgb(0.0f, 0.0f, 0.0f), surfaceKind = SurfaceKind.ROCK,
            starNameZh = star.nameZh, starTempK = star.tempK, starRadiusSolar = star.radiusSolar,
            starLuminositySolar = star.luminositySolar, distanceLy = star.distanceLy,
            eqTempK = 279.0, tidallyLocked = false, confidence = DataConfidence.OBSERVED,
            hostStarId = star.id, isStation = true,
            skyBasisZh = "空间站无大气：天空纯黑，只有 ${star.nameZh} 本体与星空；站位于 a = √L ≈ ${"%.3f".format(a)} AU"
        )
    }

    val systems: List<StarSystem> by lazy {
        stars.map { s -> StarSystem(s, bodies(s.id), stationFor(s)) }
    }

    /** 便捷入口：某个天体 id 属于哪颗恒星 */
    fun starOf(planet: Planet): Star = byId(planet.hostStarId)
}
