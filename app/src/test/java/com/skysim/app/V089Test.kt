package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Cal
import com.skysim.app.core.PhaseSource
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * v0.8.9：纪元自转相位 + 火星恒星日标准值 + 渲染层改按物理日相位。
 *
 * 三项断言口径：
 *  1. 纪元瞬间（UCT 0001-01-01T00:00:00Z）各天体的本地钟点必须等于《自转相位核对表》给出的 f，
 *     而不是全体归零——这是「本地 00:00 才是真午夜」的直接后果。
 *  2. 本地午夜（相位 0）必须严格对应太阳时角 180°，且这个对应关系与 UCT 的换算可逆
 *     （同一个 UCT 主变量，不是每颗星各存一个时间）。
 *  3. 火星数据行改用标准恒星日 88 642.6630 s 后，太阳日、年长与读数按同一套公式整体移动。
 */
class V089Test {

    private val epochSeconds: Long = TimeSystem.EPOCH_EPOCH_DAY * 86_400L

    private fun p(id: String): Planet = Planets.byId(id)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0): Long =
        Cal.toEpochDay(y, m, d) * 86_400L + h * 3600L

    /** 秒数差（绕 24 h 取最小） */
    private fun clockGap(a: Int, b: Int): Int {
        val d = abs(a - b) % 86_400
        return if (d > 43_200) 86_400 - d else d
    }

    // ---------------------------------------------------------------- 相位常数

    /** 该天体纪元瞬间应显示的钟点秒数：24 等分档 = f × 86400，SI 秒档 = f × 本地日长。 */
    private fun expectedEpochClockSeconds(planet: Planet): Double {
        val d = TimeSystem.localDayLengthSeconds(planet)
        val perDay = if (com.skysim.app.core.LocalTimeBase.scheme(planet) ==
            com.skysim.app.core.DayScheme.EQUAL_24
        ) 86_400.0 else d
        return planet.epochPhaseFraction * perDay
    }

    @Test
    fun epochClockEqualsThePhaseTableForEveryBodyWithElements() {
        // 24 等分档：核对表第 7 节直接给 24 小时制读数，火星应读 02:13:39
        val mars = TimeSystem.localStamp(p("mars"), epochSeconds)
        assertTrue(
            "火星纪元钟点应为 02:13:39 附近，实际 ${mars.clockText()}",
            clockGap(mars.hour * 3600 + mars.minute * 60 + mars.second, 2 * 3600 + 13 * 60 + 39) <= 3
        )
        // SI 秒档与其余天体：读数 = f × 本地日长（核对表给的是 24 小时制的等价钟点）
        val bodies = listOf(
            "jupiter", "saturn", "uranus", "neptune", "moon", "io",
            "europa", "ganymede", "callisto", "titan", "triton"
        )
        for (id in bodies) {
            val planet = p(id)
            val s = TimeSystem.localStamp(planet, epochSeconds)
            val got = s.hour * 3600.0 + s.minute * 60.0 + s.second
            val want = expectedEpochClockSeconds(planet)
            assertTrue("$id 纪元钟点应为 $want s，实际 $got s（${s.clockText()}）", abs(got - want) <= 2.0)
        }
        // 水星、金星、冥王星走十进制粗分，核对表的 09:12:07 / 14:53:16 / 11:14:46 等价于小数日 f
        for ((id, f) in listOf("mercury" to 0.38342, "venus" to 0.62033, "pluto" to 0.46859)) {
            assertEquals("$id 小数目", f, TimeSystem.localStamp(p(id), epochSeconds).dayFraction, 2e-4)
        }
    }

    @Test
    fun coarseDecimalBodiesReadThePhaseAsDecimalDay() {
        // 十进制粗分档不印时分秒，只印 0–1 小数日，核对表同样按 f 给值
        for ((id, f) in listOf("mercury" to 0.38342, "venus" to 0.62033, "pluto" to 0.46859)) {
            val s = TimeSystem.localStamp(p(id), epochSeconds)
            assertEquals("$id 纪元瞬间的小数日必须等于 f", f, s.dayFraction, 2e-4)
            assertTrue("$id 主行应印小数日", s.timePartText().endsWith("日"))
        }
    }

    @Test
    fun earthIsTheOnlyBodyWithoutAnEpochOffset() {
        assertEquals(0.0, p("earth").epochPhaseFraction, 0.0)
        assertEquals(PhaseSource.MEASURED_ANCHOR, p("earth").epochPhaseSource)
        val earth = TimeSystem.localStamp(p("earth"), epochSeconds)
        assertEquals(0, earth.hour)
        assertEquals(0, earth.minute)
        assertEquals(0, earth.second)
        // 火星必须带平移，否则就退回「全体归零」的旧口径
        assertTrue(p("mars").epochPhaseFraction > 0.09)
    }

    @Test
    fun marsEpochReadingIsTwoHoursThirteenMinutes() {
        val s = TimeSystem.localStamp(p("mars"), at(1, 1, 1))
        assertEquals(1, s.year)
        assertEquals(1, s.month)
        assertEquals(1, s.day)
        assertTrue("火星纪元应读 02:13:38 附近，实际 ${s.clockText()}", clockGap(s.hour * 3600 + s.minute * 60 + s.second, 2 * 3600 + 13 * 60 + 39) <= 3)
    }

    @Test
    fun localMidnightIsExactlyHourAngle180() {
        // 五个天体 × 两个时刻：本地午夜瞬间必须同时满足「钟读到 00:00:00」与「时角 = 180°」。
        val bodies = listOf("earth", "mars", "venus", "mercury", "jupiter")
        for (id in bodies) {
            val planet = p(id)
            for (t in listOf(at(2025, 12, 1), at(1999, 3, 17, 7))) {
                val midnight = TimeSystem.uctSecondsForLocalPhase(planet, t, 0.0)
                val s = TimeSystem.localStamp(planet, midnight)
                assertEquals("$id 本地午夜相位应为 0", 0.0, s.dayFraction, 1.0e-4)
                assertTrue("$id 本地午夜时角应 180°，实际 ${s.solarHourAngleDeg}", abs(s.solarHourAngleDeg - 180.0) < 0.01)
                assertEquals("$id 本地午夜钟点应为 00:00:00，实际 ${s.clockText()}", 0, s.hour * 3600 + s.minute * 60 + s.second)
            }
        }
    }

    @Test
    fun localPhaseRoundTripsWithoutChangingTheInstant() {
        val planet = p("mars")
        val t = at(2025, 12, 1)
        for (phase in listOf(0.0, 0.25, 0.5, 0.75, 0.999)) {
            val uct = TimeSystem.uctSecondsForLocalPhase(planet, t, phase)
            assertEquals(phase, TimeSystem.localPhaseOf(planet, uct), 2.0e-5)
            // 同一个本地日：换算前后日序号不变（滑条不能跨日漂移）
            assertEquals(
                TimeSystem.localStamp(planet, t).localDayNumber,
                TimeSystem.localStamp(planet, uct).localDayNumber
            )
        }
    }

    @Test
    fun undeterminedBodiesKeepZeroPhaseButSaySo() {
        val b = p("kepler22b")
        assertEquals(0.0, b.epochPhaseFraction, 0.0)
        assertEquals(PhaseSource.UNDETERMINED, b.epochPhaseSource)
        val s = TimeSystem.localStamp(b, at(2025, 12, 1))
        assertTrue(s.schemeText().contains("未定"))
    }

    // ---------------------------------------------------------------- 火星数据行

    @Test
    fun marsSiderealDayUsesTheStandardValue() {
        val mars = p("mars")
        val d = mars.solarDaySeconds
        // 1/D_sun = 1/D_sid − 1/D_orbit，数据行 88 642.6630 s、公转 686.98 d
        val expect = 1.0 / (1.0 / 88_642.6630 - 1.0 / (686.98 * 86_400.0))
        assertEquals(expect, d, 1.0e-6)
        assertEquals(88_775.242637, d, 1.0e-5)
    }

    @Test
    fun marsReadingMovesForwardAndKeepsItsDate() {
        // 参照瞬间：UCT 2025-12-01T00:00:00Z
        val s = TimeSystem.localStamp(p("mars"), at(2025, 12, 1))
        assertEquals(1077, s.year)
        assertEquals(12, s.month)
        assertEquals(22, s.day)
        assertEquals(23, s.hour)
        assertEquals(42, s.minute)
        assertTrue("火星读数应落在 23:42:2x，实际 ${s.clockText()}", abs(s.second - 23) <= 2)
    }

    @Test
    fun marsYearLengthFollowsTheNewSolarDay() {
        // 686.98 × 86400 ÷ 88775.2426 = 668.5993 本地日（旧值 668.5994）
        val yld = com.skysim.app.core.LocalCalendars.of(p("mars")).yearLocalDays
        assertEquals(668.5993, yld, 1.0e-4)
    }

    // ---------------------------------------------------------------- 显示前缀与约定相位

    @Test
    fun onlyEarthIsPrintedWithoutTheModelPrefix() {
        val earthLine = TimeSystem.localStamp(p("earth"), at(2025, 12, 1)).localLineText()
        assertFalse(earthLine.contains("模型推测"))
        for (id in listOf("mars", "venus", "mercury", "jupiter", "moon", "kepler22b", "trappist1e")) {
            val line = TimeSystem.localStamp(p(id), at(2025, 12, 1)).localLineText()
            assertTrue("$id 的读数应带「（模型推测）」前缀，实际「$line」", line.startsWith("（模型推测）"))
        }
    }

    @Test
    fun gasGiantsAreMarkedAsConventionValues() {
        for (id in listOf("jupiter", "saturn", "uranus", "neptune")) {
            val planet = p(id)
            assertEquals("$id 应是约定相位", PhaseSource.CONVENTION, planet.epochPhaseSource)
            val s = TimeSystem.localStamp(planet, at(2025, 12, 1))
            assertTrue("$id 读数后应标约定相位", s.localLineText().endsWith("（约定相位）"))
            assertTrue("$id 说明行应写清非实测", s.schemeText().contains("约定值"))
            // 四颗保留完整年月日时分秒
            assertTrue("$id 应保留年星日链（完整年月日时分秒）", s.chain == com.skysim.app.core.CalendarChain.YEAR_STAR_DAY)
        }
        // 实测锚定的天体不能被标成约定值
        assertFalse(TimeSystem.localStamp(p("mars"), at(2025, 12, 1)).localLineText().contains("约定相位"))
    }

    // ---------------------------------------------------------------- 日出日落与滑条

    @Test
    fun sunrisePhaseIsWhereTheSunCrossesTheHorizon() {
        // 与画面上太阳共用同一套时角：日出相位处高度角 ≈ 0，日落相位处也 ≈ 0。
        val planet = p("mars")
        val doy = 200
        val lat = 30f
        val risePhase = Astro.phaseOfSunAltitude(planet, lat, doy, 0.0, true, false)
        val setPhase = Astro.phaseOfSunAltitude(planet, lat, doy, 0.0, false, false)
        if (!risePhase.isNaN() && !setPhase.isNaN()) {
            val rise = Astro.sunGeo(planet, lat, doy, risePhase, false, false)
            val set = Astro.sunGeo(planet, lat, doy, setPhase, false, false)
            assertTrue("日出时太阳高度应约 0°，实际 ${rise.sunAltDeg}", abs(rise.sunAltDeg) < 0.5)
            assertTrue("日落时太阳高度应约 0°，实际 ${set.sunAltDeg}", abs(set.sunAltDeg) < 0.5)
            // 正午相位 0.5 = 时角 0°
            // 正午相位 0.5：本地平太阳时 12:00；画面太阳是真太阳，时角相差一个均时差（火星可达 ±12°）
            val noon = Astro.sunGeo(planet, lat, doy, 0.5, false, false)
            assertTrue("正午时角应在均时差范围内，实际 ${noon.hourAngleDeg}", abs(noon.hourAngleDeg) < 12.0)
        }
    }

    @Test
    fun sunFollowsThePhysicalDayPhaseNotTheUctPhase() {
        // 同一 UCT 瞬间，火星的太阳时角必须由火星自己的日相位决定。
        val planet = p("mars")
        val t = at(2025, 12, 1)
        val phase = TimeSystem.localPhaseOf(planet, t)
        val localDoy = TimeSystem.localStamp(planet, t).dayOfYearLocal
        val geo = Astro.sunGeo(planet, 0f, localDoy, phase, false, false)
        // 以本地相位算出的时角与「按 UCT 日进度算」的时角必须明显不同（火星差 2.7% 的日长）
        val uctPhase = TimeSystem.uctFractionOfDay(t)
        val geoUct = Astro.sunGeo(planet, 0f, localDoy, uctPhase, false, false)
        assertTrue(abs(phase - uctPhase) > 0.001)
        assertTrue(
            "太阳必须跟本地相位走：本地 ${geo.hourAngleDeg}° vs UCT ${geoUct.hourAngleDeg}°",
            abs(geo.hourAngleDeg - geoUct.hourAngleDeg) > 1.0
        )
        // 本地午夜（相位 0）时角 180°、正午（相位 0.5）时角约 0°（差额是均时差）
        val mid = Astro.sunGeo(planet, 0f, localDoy, 0.0, false, false)
        // 画面太阳是真太阳：本地午夜时角 = 180° − 均时差
        val midNorm = ((mid.hourAngleDeg % 360.0) + 360.0) % 360.0
        assertTrue("本地午夜画面时角应在 180°±12°，实际 $midNorm", abs(midNorm - 180.0) < 12.0)
        // 而显示层的平太阳时角严格是 180°
        assertEquals(180.0, TimeSystem.localStamp(planet, TimeSystem.uctSecondsForLocalPhase(planet, t, 0.0)).solarHourAngleDeg, 0.01)
    }

    @Test
    fun sliderStepIsOneLocalHourInUctSeconds() {
        val planet = p("mars")
        val t = at(2025, 12, 1)
        val a = TimeSystem.uctSecondsForLocalPhase(planet, t, 0.0)
        val b = TimeSystem.uctSecondsForLocalPhase(planet, t, 1.0 / 24.0)
        val step = (b - a).toDouble()
        assertEquals(TimeSystem.localDayLengthSeconds(planet) / 24.0, step, 2.0)
    }
}
