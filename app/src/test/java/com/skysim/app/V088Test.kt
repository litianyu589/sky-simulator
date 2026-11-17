package com.skysim.app

import com.skysim.app.core.Cal
import com.skysim.app.core.CalendarChain
import com.skysim.app.core.DayScheme
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * v0.8.8：本地时间重新变成「本地」的护栏。
 *
 * 0.8.7 的缺陷（用户真机截图）：每个天体读数的时分秒都等于 UCT 当天的日进度，
 * 本地日计数也按「1 UCT 日 = 1 本地日」推进，于是
 *  - 金星显示 384285 年（把地球日数当金星本地日用，放大了 116.75 倍），
 *  - 火星显示 1107 年（放大了 1.0275 倍 = 火星日/地球日），
 *  - 所有天体的时钟都指向同一个 UT 时刻。
 *
 * 这一批断言锁的是「物理」而不是某次运行的数字：
 *  1. 本地日计数 = 从统一纪元（UCT 0001-01-01T00:00:00Z）起算的 SI 秒 ÷ 该天体自己的太阳日；
 *  2. 本地时分秒、太阳时角、相位全部由本地日相位推出，与 UCT 当天的日进度无关；
 *  3. 进制标签与渲染形态一一对应；
 *  4. 地球与 UCT 完全同构（未受本次改动影响）。
 */
class V088Test {

    private val epochSeconds: Long = TimeSystem.EPOCH_EPOCH_DAY * 86_400L

    private fun p(id: String): Planet = Planets.byId(id)

    private fun at(y: Int, m: Int, d: Int, h: Int = 0): Long =
        Cal.toEpochDay(y, m, d) * 86_400L + h * 3600L

    /** 构造「某个本地日相位」对应的 UCT 秒（相位 0 = 本地午夜，0.5 = 本地正午）。 */
    private fun phaseInstant(planet: Planet, cycle: Long, phase: Double): Long {
        // 地球走公历，一个本地日恒为 86400 s（数据行里的 86399.991 是恒星日推导的近似值）
        val day = when {
            planet.gregorianCalendar -> 86_400.0
            // 潮汐锁定天体没有有限太阳日，本地日 = 公转周期
            !planet.solarDaySeconds.isFinite() -> planet.orbitalPeriodDays * 86_400.0
            else -> planet.solarDaySeconds
        }
        // v0.8.9：纪元自转相位 f 把本地 00:00 从纪元挪开了 f 个本地日，换算回 UCT 时要减掉
        return epochSeconds + ((cycle + phase - planet.epochPhaseFraction) * day).roundToLong()
    }

    /** 环形角差（0° 与 359.99° 相邻）。 */
    private fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    // ---------- 一、时角是真的本地太阳时角 ----------

    /** 同一 UCT 瞬间，地球与金星的太阳时角必须不同（0.8.7 里它们同为 UCT 日进度的函数）。 */
    @Test
    fun earthAndVenusHourAnglesDifferAtTheSameInstant() {
        val t = at(2026, 11, 8, 12)
        val earth = TimeSystem.localStamp(p("earth"), t)
        val venus = TimeSystem.localStamp(p("venus"), t)
        assertTrue("地球正午时角应接近 0°（实际 ${earth.solarHourAngleDeg}）",
            angleDiff(earth.solarHourAngleDeg, 0.0) < 0.01)
        assertTrue("金星与地球在同一 UCT 时刻的时角不得相同（地球 ${earth.solarHourAngleDeg}°，金星 ${venus.solarHourAngleDeg}°）",
            angleDiff(earth.solarHourAngleDeg, venus.solarHourAngleDeg) > 1.0)
    }

    /** 四个进制分支都要满足「时角 0° = 本地正午，180° = 本地午夜」的反算。 */
    @Test
    fun hourAngleRoundTripsOnEverySchemeBranch() {
        val cases = listOf(
            "earth" to DayScheme.EQUAL_24,
            "mars" to DayScheme.EQUAL_24,
            "moon" to DayScheme.SI_SECONDS,
            "venus" to DayScheme.COARSE_DECIMAL,
            "mercury" to DayScheme.COARSE_DECIMAL
        )
        for ((id, expectedScheme) in cases) {
            val planet = p(id)
            assertEquals("$id 的进制必须符合数据行判据", expectedScheme, com.skysim.app.core.LocalTimeBase.scheme(planet))
            val noon = TimeSystem.localStamp(planet, phaseInstant(planet, 9000L, 0.5))
            assertTrue("$id 本地正午的时角必须是 0°（实际 ${noon.solarHourAngleDeg}）",
                angleDiff(noon.solarHourAngleDeg, 0.0) < 0.05)
            assertEquals("$id 正午的日相位必须是 0.5", 0.5, noon.dayFraction, 1e-4)
            val midnight = TimeSystem.localStamp(planet, phaseInstant(planet, 9000L, 0.0))
            assertTrue("$id 本地午夜的时角必须是 180°（实际 ${midnight.solarHourAngleDeg}）",
                angleDiff(midnight.solarHourAngleDeg, 180.0) < 0.05)
            // 整秒取整会让「午夜」落在 0 或 0.999999…，两者都对（环状相位）
            val midnightPhase = midnight.dayFraction
            assertTrue("$id 午夜的日相位必须贴近 0 或 1（实际 $midnightPhase）",
                minOf(midnightPhase, 1.0 - midnightPhase) < 1e-4)
            // 正午后 1/8 日的时角是 45°
            val eighth = TimeSystem.localStamp(planet, phaseInstant(planet, 9000L, 0.625))
            assertTrue("$id 正午后 1/8 日时角应约 45°（实际 ${eighth.solarHourAngleDeg}）",
                angleDiff(eighth.solarHourAngleDeg, 45.0) < 0.1)
        }
        // 相位链（潮汐锁定）：时钟冻结在永昼面 12 时，只显示相位
        val locked = p("trappist1e")
        val day = TimeSystem.localStamp(locked, at(2026, 11, 8, 3), tidalNightSide = false)
        val night = TimeSystem.localStamp(locked, at(2026, 11, 8, 3), tidalNightSide = true)
        assertTrue(day.phaseOnly)
        assertEquals("永昼面冻结在 12 时", 12, day.hour)
        assertEquals("永夜面冻结在 0 时", 0, night.hour)
        val periodSeconds = (locked.orbitalPeriodDays * 86_400.0).roundToLong()
        val later = TimeSystem.localStamp(locked, at(2026, 11, 8, 3) + periodSeconds)
        assertEquals("跨一个公转周期相位必须回到原处", day.orbitPhaseDeg, later.orbitPhaseDeg, 0.05)
    }

    // ---------- 二、本地日按真实物理日长走 ----------

    /** 相同 UCT 增量下，不同太阳日的天体本地日增量必须不同，且等于 Δ ÷ 太阳日。 */
    @Test
    fun localDayRateFollowsEachPlanetsOwnSolarDay() {
        val t0 = at(2026, 11, 8)
        val deltaDays = 29
        val t1 = t0 + deltaDays * 86_400L
        val rates = mutableMapOf<String, Double>()
        for (id in listOf("earth", "mars", "jupiter", "venus", "mercury")) {
            val planet = p(id)
            val a = TimeSystem.localStamp(planet, t0).localDayNumber
            val b = TimeSystem.localStamp(planet, t1).localDayNumber
            val delta = b - a
            val expected = deltaDays * 86_400.0 / planet.solarDaySeconds
            assertTrue("$id 的本地日增量必须等于 Δ÷太阳日（期望 $expected，实际 $delta）",
                abs(delta - expected) <= 1.0)
            rates[id] = delta.toDouble()
        }
        assertEquals("地球 29 UCT 日 = 29 本地日", 29.0, rates.getValue("earth"), 0.001)
        assertTrue("金星的本地日增量必须远小于地球（${rates["venus"]} vs ${rates["earth"]}）",
            rates.getValue("venus") < rates.getValue("earth") / 100.0)
        assertTrue("木星的本地日增量必须远大于地球（${rates["jupiter"]} vs ${rates["earth"]}）",
            rates.getValue("jupiter") > rates.getValue("earth") * 2.0)
        assertFalse("五个天体的本地日增量不可能相同", rates.values.toSet().size == 1)
    }

    /** 用户给的量化判据：金星年数不得超过地球年数的十倍。 */
    @Test
    fun venusYearsStayWithinTenTimesEarths() {
        for (t in listOf(at(2025, 12, 9, 23), at(2026, 11, 8), at(2000, 1, 1))) {
            val earth = TimeSystem.localStamp(p("earth"), t).year
            val venus = TimeSystem.localStamp(p("venus"), t).year
            assertTrue("金星年数 $venus 不得超过地球年数 $earth 的十倍", venus <= earth * 10)
            assertTrue("金星年数应与地球同量级（实际 $venus）", venus > earth / 2)
        }
    }

    /** 用户给的量化判据：火星年数应落在「地球年数 × 0.50–0.56」区间。 */
    @Test
    fun marsYearsAreHalfToFiftySixPercentOfEarths() {
        for (t in listOf(at(2025, 12, 9, 23), at(2026, 11, 8), at(2000, 1, 1))) {
            val earth = TimeSystem.localStamp(p("earth"), t).year
            val mars = TimeSystem.localStamp(p("mars"), t).year
            val ratio = mars.toDouble() / earth.toDouble()
            assertTrue("火星年数/地球年数 = $ratio，应落在 0.50–0.56", ratio in 0.50..0.56)
        }
    }

    /** 0.8.7 的根因量化：金星本地日数约等于地球日数除以 116.75，而不是等于地球日数。 */
    @Test
    fun venusDayCountIsNotTheEarthDayCount() {
        val t = at(2026, 11, 8)
        val earthDays = TimeSystem.localStamp(p("earth"), t).localDayNumber.toDouble()
        val venusDays = TimeSystem.localStamp(p("venus"), t).localDayNumber.toDouble()
        val ratio = earthDays / venusDays
        val expected = p("venus").solarDaySeconds / 86_400.0
        assertEquals("地球日 / 金星本地日 必须等于金星太阳日的天数（$expected）", expected, ratio, expected * 0.01)
        assertTrue("金星本地日数必须远小于地球日数", venusDays < earthDays / 100.0)
    }

    // ---------- 三、进制标签与渲染形态一一对应 ----------

    /** 每种进制的主行渲染形态必须与标签说的一致，不许再出现「标十进制却显示时:分:秒」。 */
    @Test
    fun schemeLabelMatchesRenderedForm() {
        val t = at(2026, 11, 8, 17)
        val clock = Regex("\\d{2}时\\d{2}分\\d{2}秒")
        val decimal = Regex("\\d\\.\\d{4} 日")
        for (planet in Planets.all) {
            val s = TimeSystem.localStamp(planet, t)
            val text = s.calendarText()
            when {
                s.phaseOnly -> {
                    assertTrue("${planet.nameZh} 相位形态不对：$text",
                        Regex("^\\d{1,3}°\\d{2}′\\d{2}″$").matches(text))
                    assertTrue("${planet.nameZh} 的说明必须点明潮汐锁定", s.schemeText().contains("潮汐锁定"))
                }
                s.scheme == DayScheme.COARSE_DECIMAL -> {
                    assertTrue("${planet.nameZh} 十进制粗分必须渲染成小数日：$text", decimal.containsMatchIn(text))
                    assertFalse("${planet.nameZh} 十进制粗分不得出现「时」：$text", text.contains("时"))
                    assertFalse("${planet.nameZh} 十进制粗分不得出现「分」：$text", text.contains("分"))
                    assertFalse("${planet.nameZh} 十进制粗分不得出现「秒」：$text", text.contains("秒"))
                    assertTrue("${planet.nameZh} 十进制粗分必须并列给出太阳时角：${s.schemeText()}",
                        s.schemeText().contains("太阳时角"))
                    assertTrue("${planet.nameZh} 十进制字段必须落在 0–9", s.hour in 0..9 && s.minute in 0..9 && s.second in 0..9)
                }
                else -> {
                    assertTrue("${planet.nameZh} 时:分:秒形态不对：$text", clock.containsMatchIn(text))
                    val maxHour = if (s.scheme == DayScheme.EQUAL_24) 23 else (planet.solarDaySeconds / 3600.0).toInt()
                    assertTrue("${planet.nameZh} 的小时越界 ${s.hour}（${s.scheme}）", s.hour in 0..maxHour)
                    assertTrue("${planet.nameZh} 的分秒越界", s.minute in 0..59 && s.second in 0..59)
                }
            }
        }
    }

    /** 非地球天体的时钟不得再等于 UCT 时钟（0.8.7 里它们逐字相同）。 */
    @Test
    fun localClockIsNoLongerTheUctClock() {
        var different = 0
        var total = 0
        for (hour in 0 until 24) {
            val t = at(2026, 11, 8, hour)
            val uct = TimeSystem.uctStamp(t)
            for (id in listOf("venus", "jupiter", "saturn", "mercury")) {
                val s = TimeSystem.localStamp(p(id), t)
                total++
                if (s.hour != uct.hour || s.minute != uct.minute) different++
            }
        }
        assertTrue("非地球天体的时刻必须与 UCT 时刻不同（相同 $different/$total 次）", different > total * 0.9)
    }

    // ---------- 四、地球与 UCT 仍然完全同构 ----------

    /** 地球走公历：任何时刻（含闰日、纪元当天、极端年份）读数必须逐字段等于 UCT。 */
    @Test
    fun earthStillReadsExactlyAsTheGregorianCalendar() {
        val samples = listOf(
            TimeSystem.EPOCH_EPOCH_DAY * 86_400L,
            at(2024, 2, 29, 23),
            at(2000, 1, 1, 12),
            at(2025, 12, 9, 23),
            at(2026, 11, 8, 17),
            Cal.toEpochDay(100_000, 12, 31) * 86_400L + 86_399L,
            Cal.toEpochDay(-100_000, 1, 1) * 86_400L
        )
        for (t in samples) {
            val uct = TimeSystem.uctStamp(t)
            val earth = TimeSystem.localStamp(p("earth"), t)
            assertEquals("年", uct.year, earth.year)
            assertEquals("月", uct.month, earth.month)
            assertEquals("日", uct.day, earth.day)
            assertEquals("时", uct.hour, earth.hour)
            assertEquals("分", uct.minute, earth.minute)
            assertEquals("秒", uct.second, earth.second)
            assertEquals("地球主行必须与 UCT 行同构", uct.calendarText(), earth.calendarText())
            assertEquals("地球本地日序号 = UCT 日序号", uct.localDayNumber, earth.localDayNumber)
            assertNotEquals("地球是唯一月制天体", CalendarChain.PHASE, earth.chain)
        }
    }

    /** 所有天体仍以 UCT 0001-01-01T00:00:00Z 为共同原点（不设 J2000 分支、不做每星各自纪元）。 */
    @Test
    fun everyBodySharesTheAdOneEpoch() {
        // v0.8.9 改写：统一纪元仍是同一个物理瞬间（日序号 0、年月日都从 1 起），
        // 但不再是「各天体都是本地午夜」——纪元落在各自真实午夜之后 f 个本地日。
        // 旧断言「日相位必须为 0」正是被本版修掉的错误口径，改为「日相位 = 该天体自己的 f」。
        val t = TimeSystem.EPOCH_EPOCH_DAY * 86_400L
        for (planet in Planets.all) {
            val s = TimeSystem.localStamp(planet, t)
            assertEquals("${planet.nameZh} 在纪元瞬间必须是第 0 个本地日", 0L, s.localDayNumber)
            assertEquals(
                "${planet.nameZh} 纪元瞬间的日相位必须是它自己的纪元自转相位",
                planet.epochPhaseFraction, s.dayFraction, 1e-9
            )
            val before = TimeSystem.localStamp(planet, t - 1L)
            if (planet.epochPhaseFraction == 0.0) {
                assertEquals("${planet.nameZh} 纪元前一刻是第 −1 个本地日", -1L, before.localDayNumber)
            } else {
                assertEquals("${planet.nameZh} 纪元前一刻仍在第 0 个本地日（相位 > 0）", 0L, before.localDayNumber)
            }
        }
        assertEquals("统一纪元必须是公元 1 年 1 月 1 日", 1L, TimeSystem.EPOCH_EPOCH_DAY - Cal.toEpochDay(1, 1, 1) + 1L)
    }
}
