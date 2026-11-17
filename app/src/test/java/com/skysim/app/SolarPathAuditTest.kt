package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyGeo
import com.skysim.app.core.Twilight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.tan
import kotlin.math.PI

/**
 * v0.6.9 太阳轨迹审计。
 *
 * 每条断言都对照**闭式天文公式**，不是对照实现自己：
 *   sin(alt) = sinφ·sinδ + cosφ·cosδ·cosH
 *   cos(A)   = sinδ / cosφ           （日出方位角，alt = 0）
 *   δ        = asin(sin ε · sin Λ)   （ε = 有效轴倾角，Λ = 年内相位）
 * 日出时刻用扫描 + 二分从 alt(t) 反求，不依赖实现里的 H0 公式。
 */
class SolarPathAuditTest {

    private fun p(id: String): Planet = Planets.byId(id)

    private fun geo(planet: Planet, lat: Float, doy: Int, f: Double) =
        Astro.sunGeo(planet, lat, doy, f, anchored = false)

    /** 扫描 + 二分求「太阳高度角由负转正」的时刻（fraction of day）。 */
    private fun sunriseFraction(planet: Planet, lat: Float, doy: Int): Double? {
        var prevF = 0.0
        var prev = geo(planet, lat, doy, 0.0).sunAltDeg
        if (prev >= 0f) return null // 极昼或已经在地平线上
        var i = 1
        val n = 2000
        while (i <= n) {
            val f = i.toDouble() / n
            val alt = geo(planet, lat, doy, f).sunAltDeg
            if (alt >= 0f && prev < 0f) {
                var lo = prevF
                var hi = f
                repeat(45) {
                    val mid = (lo + hi) / 2
                    if (geo(planet, lat, doy, mid).sunAltDeg < 0f) lo = mid else hi = mid
                }
                return (lo + hi) / 2
            }
            prevF = f
            prev = alt
            i++
        }
        return null
    }

    private fun sunriseAzimuth(planet: Planet, lat: Float, doy: Int): Float {
        val f = sunriseFraction(planet, lat, doy) ?: error("没有日出：${planet.id}")
        return geo(planet, lat, doy, f).sunAzDeg
    }

    private fun azDelta(a: Float, b: Float): Float {
        var d = b - a
        while (d > 180f) d -= 360f
        while (d < -180f) d += 360f
        return d
    }

    // ---------- 一、太阳日与年 ----------

    /** 地球：恒星日 23.9345 h + 公转 → 太阳日 24.0000 h，一年 365.25 次日出。 */
    @Test
    fun earthSolarDayComesFromSiderealDayAndOrbit() {
        val e = p("earth")
        assertEquals(24.0, e.derivedSolarDayHours, 0.001)
        assertEquals(24.0, e.solarDaySeconds / 3600.0, 0.001)
        assertEquals(365.25, e.sunrisesPerYear, 0.1)
        assertEquals(false, e.retrogradeSpin)
        assertEquals(23.44, e.obliquityDeg, 0.001)
    }

    /** 金星：逆行恒星日 + 225 天公转 → 太阳日 ≈116.75 地球日，一年约 1.93 次日出。 */
    @Test
    fun venusHasRetrogradeSpinAndTwoSunrisesPerYear() {
        val v = p("venus")
        assertTrue("金星必须是逆行", v.retrogradeSpin)
        assertEquals(-2802.0, v.derivedSolarDayHours, 25.0)
        assertEquals(116.75, v.solarDaySeconds / 86400.0, 0.3)
        assertEquals(1.93, v.sunrisesPerYear, 0.05)
        // 一天内不会出现两次日出：太阳日比一年的一半更长
        assertTrue("金星一个太阳日应超过半年", v.solarDaySeconds / 86400.0 > v.orbitalPeriodDays / 2.0)
    }

    /** 天王星：97.8° 倾角折叠成 82.2° 有效倾角 + 逆行自转，赤道自转 17.24 h。 */
    @Test
    fun uranusIsNearlyOnItsSide() {
        val u = p("uranus")
        assertEquals(82.23, u.obliquityDeg, 0.05)
        assertTrue("倾角 > 90° 必须判定为逆行", u.retrogradeSpin)
        assertEquals(17.24, abs(u.siderealDayHours), 0.01)
        assertEquals(17.24, u.solarDaySeconds / 3600.0, 0.05)
        assertEquals(82.2, maxDeclination(u), 0.6)
    }

    /** 月球：太阳日 29.53 地球日（比恒星月 27.32 天长，因为还要追上地球的公转）。 */
    @Test
    fun moonSolarDayIsOneSynodicMonth() {
        val m = p("moon")
        assertEquals(29.53, m.solarDaySeconds / 86400.0, 0.02)
        assertEquals(29.53, m.derivedSolarDayHours / 24.0, 0.05)
        assertEquals(365.256 / 29.53, m.sunrisesPerYear, 0.02)
    }

    /** 火星：太阳日 24 h 39.6 min。 */
    @Test
    fun marsSolarDayIsTwentyFourHoursThirtyNineMinutes() {
        val m = p("mars")
        assertEquals(24.6597, m.solarDaySeconds / 3600.0, 0.002)
        assertEquals(24.6597, m.derivedSolarDayHours, 0.01)
        assertEquals(668.6, m.sunrisesPerYear, 1.0)
    }

    // ---------- 二、日轨迹：方位、正午高度、极昼极夜 ----------

    /** 二分点：日出正东、日落正西（40°N）。 */
    @Test
    fun equinoxSunRisesDueEast() {
        val e = p("earth")
        val az = sunriseAzimuth(e, 40f, 80)
        assertEquals(90.0, az.toDouble(), 2.0)  // 方程中心使春分日出方位偏 ~1°
        val g = geo(e, 40f, 80, 0.5)
        assertEquals(180.0, g.sunAzDeg.toDouble(), 2.5)   // 正午在南（v0.7.0 加入方程中心后最多偏 2°）
        assertEquals(50.0, g.sunAltDeg.toDouble(), 1.2)   // 90 − 40
    }

    /** 夏至：40°N 日出北偏东，方位角与闭式解 cos A = sinδ/cosφ 一致（≈58.6°）。 */
    @Test
    fun solsticeSunriseMatchesTheClosedForm() {
        val e = p("earth")
        val doy = 172
        val g = geo(e, 40f, doy, 0.5)
        val decl = Math.toRadians(g.declinationDeg.toDouble())
        val phi = Math.toRadians(40.0)
        val expected = Math.toDegrees(acos(sin(decl) / cos(phi)))
        val measured = sunriseAzimuth(e, 40f, doy)
        assertEquals(expected, measured.toDouble(), 1.0)
        // 天雨记的 55° 是约 35°N 的值；40°N 的正确值就在 58–59° 之间
        assertTrue("40°N 夏至日出应北偏东 56–61°，实测 $measured", measured in 56f..61f)
        assertEquals(73.44, g.noonAltDeg.toDouble(), 0.6)  // 90 − 40 + 23.44
    }

    /** 正午高度角在任意时刻都满足闭式解，说明轨迹是解析算出来的，不是预设斜圆。 */
    @Test
    fun altitudeMatchesTheSphericalFormulaAllDay() {
        val e = p("earth")
        val phi = 40.0
        for (doy in listOf(15, 80, 172, 266, 355)) {
            for (f in listOf(0.0, 0.12, 0.25, 0.37, 0.5, 0.63, 0.75, 0.88)) {
                val g = geo(e, phi.toFloat(), doy, f)
                val decl = Math.toRadians(g.declinationDeg.toDouble())
                val h = Math.toRadians(g.hourAngleDeg.toDouble())
                val expected = Math.toDegrees(
                    Math.asin(
                        sin(Math.toRadians(phi)) * sin(decl) +
                            cos(Math.toRadians(phi)) * cos(decl) * cos(h)
                    )
                )
                assertEquals("doy=$doy f=$f", expected, g.sunAltDeg.toDouble(), 0.01)
            }
        }
    }

    /** 极昼极夜：地球极点夏至极昼、冬至极夜，且由纬度与赤纬自然推出。 */
    @Test
    fun earthPolesAlternatePolarDayAndNight() {
        val e = p("earth")
        assertTrue(geo(e, 90f, 172, 0.0).polarDay)
        assertTrue(geo(e, 90f, 355, 0.0).polarNight)
        assertTrue(geo(e, -90f, 172, 0.0).polarNight)
        assertTrue(geo(e, -90f, 355, 0.0).polarDay)
        // 极圈内 70°N 也有极昼，赤道永远没有
        assertTrue(geo(e, 70f, 172, 0.0).polarDay)
        assertTrue(!geo(e, 0f, 172, 0.0).polarDay && !geo(e, 0f, 172, 0.0).polarNight)
    }

    /** 天王星极区：极昼要连续出现约 42 年（半个 84 年公转周期）。 */
    @Test
    fun uranusPolarDayLastsForDecades() {
        val u = p("uranus")
        var polarDays = 0
        val yearDays = u.localDaysPerYear
        for (d in 1..yearDays) if (geo(u, 85f, d, 0.5).polarDay) polarDays++
        val polarYears = polarDays.toDouble() / yearDays * u.orbitalPeriodDays / 365.25
        assertEquals(42.0, polarYears, 4.0)
        // 换到南极则正好相反
        var southPolar = 0
        for (d in 1..yearDays) if (geo(u, -85f, d, 0.5).polarDay) southPolar++
        // 85° 处「极昼」要求 |δ| > 5°，一年里有一小段两边都不是极昼/极夜，和应该略小于 1
        val sum = (polarDays + southPolar).toDouble() / yearDays
        assertTrue("两极极昼之和应在 0.7–1.0 之间，实测 $sum", sum in 0.7..1.0001)
    }

    /** 天王星赤道：一天仍是 17.24 h（自转没有因为躺着转就停），只是太阳高度角一年内几乎不变。 */
    @Test
    fun uranusEquatorKeepsItsFastRotation() {
        val u = p("uranus")
        val g = geo(u, 0f, 100, 0.5)
        val dayFraction = g.dayLengthHours / (u.solarDaySeconds / 3600.0)
        assertEquals(0.5, dayFraction.toDouble(), 0.02)
        // 赤道上太阳一天之内扫过整个天空：正午与午夜高度差约等于 2×赤纬
        val noon = geo(u, 0f, 100, 0.5).sunAltDeg
        val midnight = geo(u, 0f, 100, 0.0).sunAltDeg
        assertTrue("赤道正午应高于午夜", noon > midnight + 20f)
    }

    // ---------- 三、逆行：日出在西方 ----------

    /** 金星日出在西方（与地球相反），一天内方位角反向移动。 */
    @Test
    fun venusSunRisesInTheWest() {
        val v = p("venus")
        val az = sunriseAzimuth(v, 0f, 60)
        assertTrue("金星日出应在西方（270° 附近），实测 $az", az > 250f && az < 290f)
        // 地球同纬度是东方
        val earthAz = sunriseAzimuth(p("earth"), 0f, 60)
        assertTrue("地球日出应在东方，实测 $earthAz", earthAz > 70f && earthAz < 110f)
    }

    /** 时角符号：同一时刻金星时角的符号与地球相反。 */
    @Test
    fun hourAngleIsSignedBySpinDirection() {
        val e = geo(p("earth"), 0f, 80, 0.30)
        val v = geo(p("venus"), 0f, 60, 0.30)
        assertEquals(-72.0, e.hourAngleDeg.toDouble(), 4.0)  // 地球 e=0.0167，方程中心最多带 2° 偏差
        assertEquals(72.0, v.hourAngleDeg.toDouble(), 0.01)
        // 方位角的移动方向也相反
        val eMove = azDelta(geo(p("earth"), 20f, 80, 0.42).sunAzDeg, geo(p("earth"), 20f, 80, 0.58).sunAzDeg)
        val vMove = azDelta(geo(p("venus"), 20f, 60, 0.42).sunAzDeg, geo(p("venus"), 20f, 60, 0.58).sunAzDeg)
        assertTrue("地球下午方位角增大（$eMove）", eMove > 0f)
        assertTrue("金星应反向（$vMove）", vMove < 0f)
    }

    /** 潮汐锁定天体：永昼面太阳固定在天顶附近，永夜面永远地平线下。 */
    @Test
    fun tidallyLockedWorldHasNoSunrise() {
        val t = Planets.all.firstOrNull { it.tidallyLocked && !it.isStation }
        if (t == null) return
        val day = Astro.sunGeo(t, 0f, 10, 0.5, anchored = false, tidalNightSide = false)
        val night = Astro.sunGeo(t, 0f, 10, 0.5, anchored = false, tidalNightSide = true)
        assertTrue("永昼面太阳应在地平线上", day.sunAltDeg > 0f)
        assertTrue("永夜面太阳应在地平线下", night.sunAltDeg < 0f)
    }

    // ---------- 四、参数化：新增天体只填表 ----------

    private fun earthLike(id: String, block: Planet.() -> Planet = { this }): Planet {
        val e = Planets.byId("earth")
        return e.copy(id = id, nameZh = "测试天体", nameEn = "Test", gregorianCalendar = false).block()
    }

    /** 同一个物理量、不同 id → 完全相同的天空（证明没有 `when (planet.id)` 分支）。 */
    @Test
    fun skyDependsOnDataNotOnTheId() {
        val a = earthLike("alpha-test")
        val b = earthLike("beta-test")
        val skyA = Twilight.radiance(a, 4f, 90f, -6f, 90f, 0.65f)
        val skyB = Twilight.radiance(b, 4f, 90f, -6f, 90f, 0.65f)
        assertEquals(skyA.r, skyB.r, 1e-6f)
        assertEquals(skyA.g, skyB.g, 1e-6f)
        assertEquals(skyA.b, skyB.b, 1e-6f)
    }

    /** 只改一个物理量（臭氧柱量），晨昏分层必须跟着变——说明它真的由数据驱动。 */
    @Test
    fun twilightRespondsToPhysicalParameters() {
        val base = earthLike("ozone-0") { copy(ozoneColumn = 0f) }
        val rich = earthLike("ozone-1") { copy(ozoneColumn = 2f) }
        val s0 = Twilight.radiance(base, 3f, 270f, -4f, 90f, 0.65f)
        val s1 = Twilight.radiance(rich, 3f, 270f, -4f, 90f, 0.65f)
        assertNotEquals(s0.g.toDouble(), s1.g.toDouble(), 1e-4)
        // 气溶胶光学厚度同理
        val dusty = earthLike("dust") { copy(aerosolTau = 1.2, aerosolTint = Rgb(0.95f, 0.7f, 0.45f)) }
        val clear = earthLike("clear") { copy(aerosolTau = 0.01) }
        assertNotEquals(
            Twilight.radiance(dusty, 5f, 90f, 6f, 90f, 0.65f).r.toDouble(),
            Twilight.radiance(clear, 5f, 90f, 6f, 90f, 0.65f).r.toDouble(),
            1e-4
        )
    }

    /** 虚构天体只填表也能算出完整太阳轨迹与晨昏分层。 */
    @Test
    fun fictionalPlanetWorksWithOnlyATableRow() {
        val z = earthLike("zeta-test") {
            copy(
                radiusKm = 7100.0, siderealPeriodOverrideSeconds = 72000.0,
                orbitalPeriodDays = 540.0, axialTiltDeg = 31.0,
                rayTau = 0.09, aerosolTau = 0.08, ozoneColumn = 0.6f,
                pressureAtm = 0.9, scaleHeightKm = 9.0
            )
        }
        val g = geo(z, 35f, 40, 0.5)
        assertTrue(g.sunAltDeg.isFinite() && g.sunAzDeg.isFinite())
        assertTrue("正午太阳应在地平线上", g.sunAltDeg > 0f)
        assertEquals(180.0, g.sunAzDeg.toDouble(), 1.5)
        // 太阳日 20.031 h（由恒星日 20 h + 540 天公转推出）→ 一年 647 次日出
        assertEquals(647.0, z.sunrisesPerYear, 1.0)
        assertEquals(20.0309, z.derivedSolarDayHours, 0.01)
        val sky = Twilight.radiance(z, 8f, 90f, -3f, 270f, 0.65f)
        assertTrue("虚构天体也要有非零天空亮度", sky.r + sky.g + sky.b > 0f)
        val noonNadir = Twilight.radiance(z, 8f, 90f, 40f, 270f, 0.65f)
        assertNotEquals(sky.r.toDouble(), noonNadir.r.toDouble(), 1e-4)
    }

    // ---------- 辅助 ----------

    private fun maxDeclination(planet: Planet): Double {
        var max = 0.0
        for (d in 1..planet.localDaysPerYear step maxOf(1, planet.localDaysPerYear / 400)) {
            val decl = abs(geo(planet, 0f, d, 0.5).declinationDeg.toDouble())
            if (decl > max) max = decl
        }
        return max
    }

    private fun SkyGeo.asText() = "alt=$sunAltDeg az=$sunAzDeg"
}
