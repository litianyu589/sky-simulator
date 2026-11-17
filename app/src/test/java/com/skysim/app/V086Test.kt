package com.skysim.app

import com.skysim.app.core.Cal
import com.skysim.app.core.CalendarChain
import com.skysim.app.core.LocalCalendars
import com.skysim.app.core.Planets
import com.skysim.app.core.DayScheme
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.8.6：统一本地时间与日期体系。
 *
 * 这一批断言锁的是「规范」而不是「某次运行的数字」：
 *  1. UCT 与地球本地历同构，必须是完整六字段（年 月 日 时 分 秒）且各带单位标签；
 *  2. 所有天体的本地 1 年 1 月 1 日 00:00:00 = UCT 0001-01-01T00:00:00Z；
 *  3. 单位链按「年 ÷ 本地日」的比值分档，闰日由累积余数法产生；
 *  4. 不做 ΔT / 闰秒 / UT1 修正：地球本地读数就是 UTC 零时区公历读数。
 */
class V086Test {

    private fun stampAt(seconds: Long) = TimeSystem.uctStamp(seconds)

    private val epochStart: Long = TimeSystem.EPOCH_EPOCH_DAY * 86_400L

    // ---------- 一、UCT 的六字段形态 ----------

    /** UCT 必须与地球本地历同一套六字段形态：年、月、日、时、分、秒一个都不能少，各带单位标签。 */
    @Test
    fun uctUsesFullSixFieldCivilForm() {
        val seconds = Cal.toEpochDay(2026, 11, 8) * 86_400L + 17 * 3600L + 38 * 60L
        val text = TimeSystem.uctText(seconds)
        val pattern = Regex("^公历（UCT）\\d+年\\d{2}月\\d{2}日 \\d{2}时\\d{2}分\\d{2}秒$")
        assertTrue("UCT 必须是六字段带单位标签：$text", pattern.matches(text))
        assertEquals("公历（UCT）2026年11月08日 17时38分00秒", text)
        // 逐字段核对：六个单位标签全在，且没有出现简写形式（如 . 或 : 分隔）
        for (unit in listOf("年", "月", "日", "时", "分", "秒")) {
            assertTrue("缺少单位标签 $unit：$text", text.contains(unit))
        }
        assertFalse("不得退回点分简写：$text", text.contains("."))
        assertFalse("不得退回冒号简写：$text", text.contains(":"))
    }

    /** UCT 读数必须与地球本地读数完全一致（同一套历法、同一套进位），只差历法名。 */
    @Test
    fun uctAndEarthLocalAreIdenticalReadings() {
        val earth = Planets.byId("earth")
        for (seconds in listOf(
            Cal.toEpochDay(1900, 2, 28) * 86_400L + 3_600L,
            Cal.toEpochDay(2024, 2, 29) * 86_400L + 45_296L,
            Cal.toEpochDay(2026, 11, 8) * 86_400L,
            0L
        )) {
            val uct = TimeSystem.uctStamp(seconds)
            val local = TimeSystem.localStamp(earth, seconds)
            assertEquals("年必须一致", uct.year, local.year)
            assertEquals("月必须一致", uct.month, local.month)
            assertEquals("日必须一致", uct.day, local.day)
            assertEquals("时必须一致", uct.hour, local.hour)
            assertEquals("分必须一致", uct.minute, local.minute)
            assertEquals("秒必须一致", uct.second, local.second)
            assertEquals("六字段文本必须同构", uct.calendarText(), local.calendarText())
            assertEquals("地球历法名", "地球历", local.calendarNameZh)
        }
    }

    /** 公元前没有公元 0 年：年号 0 显示为公元前 1 年。 */
    @Test
    fun bceYearsHaveNoYearZero() {
        val yearZero = Cal.toEpochDay(0, 1, 1) * 86_400L
        assertEquals("公元前1", TimeSystem.uctStamp(yearZero).yearText())
        assertEquals(
            "公元前2",
            TimeSystem.uctStamp(Cal.toEpochDay(-1, 6, 1) * 86_400L).yearText()
        )
        assertTrue(TimeSystem.uctText(yearZero).startsWith("公历（UCT）公元前1年01月01日"))
    }

    // ---------- 二、统一纪元 ----------

    /** 任何天体在 UCT 0001-01-01T00:00:00Z 都是本地 1 年 1 月 1 日 00:00:00。 */
    @Test
    fun everyCalendarStartsAtTheSameInstant() {
        for (planet in Planets.all) {
            val s = TimeSystem.localStamp(planet, epochStart)
            assertEquals("${planet.nameZh} 纪元年必须为 1", 1, s.year)
            assertEquals("${planet.nameZh} 纪元年首中间单位必须为 1", 1, s.month)
            assertEquals("${planet.nameZh} 纪元年首日必须为 1", 1, s.day)
            assertEquals("${planet.nameZh} 纪元年内日序必须为 1", 1, s.dayOfYearLocal)
            if (!s.phaseOnly) {
                // v0.8.9 改写：统一纪元是同一个瞬间，但不再是「各天体本地午夜」。
                // 纪元瞬间的钟点 = 该天体的纪元自转相位 f × 24 h（地球 f = 0，仍读 00:00:00）。
                val dayLen = TimeSystem.localDayLengthSeconds(planet)
                val wantSeconds = planet.epochPhaseFraction *
                    (if (s.scheme == DayScheme.EQUAL_24) 86_400.0 else dayLen)
                val gotSeconds = s.hour * 3600.0 + s.minute * 60.0 + s.second.toDouble()
                if (s.scheme == DayScheme.COARSE_DECIMAL) {
                    assertEquals(
                        "${planet.nameZh} 纪元瞬间的小数日必须等于 f",
                        planet.epochPhaseFraction, s.dayFraction, 2e-4
                    )
                } else {
                    assertTrue(
                        "${planet.nameZh} 纪元钟点必须等于 f×24h（期望 $wantSeconds s，实际 $gotSeconds s）",
                        kotlin.math.abs(gotSeconds - wantSeconds) <= 2.0
                    )
                }
            }
            if (s.chain == CalendarChain.DAY_YEAR) {
                assertEquals("${planet.nameZh} 日.年链的年内年序从 0 起", 0, s.yearInDay)
            }
        }
    }

    /** 纪元前一刻必须落回上一年的最后一天，不能出现「0 年」或负数日。 */
    @Test
    fun instantBeforeEpochIsLastDayOfPreviousYear() {
        val mars = Planets.byId("mars")
        val s = TimeSystem.localStamp(mars, epochStart - 86_400L)
        assertEquals("年号必须是公元前 1 年（内部年号 0）", 0, s.year)
        assertTrue("日必须为正", s.day >= 1)
        assertTrue("年内日序必须为正", s.dayOfYearLocal >= 1)
    }

    // ---------- 三、单位链按比值分档 ----------

    @Test
    fun chainsFollowYearToDayRatio() {
        fun chain(id: String) = LocalCalendars.of(Planets.byId(id)).chain
        assertEquals(CalendarChain.YEAR_MONTH_DAY, chain("earth"))
        assertEquals(CalendarChain.YEAR_STAR_DAY, chain("mars"))
        assertEquals(CalendarChain.YEAR_STAR_DAY, chain("jupiter"))
        assertEquals(CalendarChain.YEAR_STAR_DAY, chain("saturn"))
        assertEquals(CalendarChain.YEAR_DAY, chain("venus"))
        assertEquals(CalendarChain.DAY_YEAR, chain("mercury"))
        assertEquals(CalendarChain.PHASE, chain("trappist1e"))
        assertEquals(CalendarChain.YEAR_DAY, chain("moon"))
    }

    @Test
    fun ratioBandsMatchTheAgreedNumbers() {
        val venus = LocalCalendars.of(Planets.byId("venus"))
        assertEquals("金星一年 1.925 本地日", 1.925, venus.yearLocalDays, 0.01)
        assertEquals("金星无中间单位", 0, venus.middleCount)

        val mercury = LocalCalendars.of(Planets.byId("mercury"))
        assertEquals("水星一年 0.5 本地日", 0.5, mercury.yearLocalDays, 0.01)
        assertEquals("水星无中间单位", 0, mercury.middleCount)

        val mars = LocalCalendars.of(Planets.byId("mars"))
        assertEquals("火星一年 668.5991 本地日", 668.5991, mars.yearLocalDays, 0.5)
        assertEquals("火星 20 星", 20, mars.middleCount)
        assertEquals("火星每星 33 日", 33, mars.middleLengthDays)
        assertEquals("火星固定 660 日 + 年尾闰日", 660, mars.fixedDaysPerYear)
    }

    /** 月制判据：有卫星 + 周期不短于 1 本地日 + 一年排得出 8–40 个。季节不参与判断。 */
    @Test
    fun monthSystemIsDecidedBySatellitesOnly() {
        assertNotNull("地球是唯一月制", LocalCalendars.pickMonth(Planets.byId("earth"), 365.2425))
        assertNull("火卫二一年排 529 个，超上限", LocalCalendars.pickMonth(Planets.byId("mars"), 668.5991))
        assertNull("木卫三一年排 1464 个，超上限", LocalCalendars.pickMonth(Planets.byId("jupiter"), 10476.0))
        assertNull("土卫六一年排 1535 个，超上限", LocalCalendars.pickMonth(Planets.byId("saturn"), 24475.0))
        assertNull("水星无卫星", LocalCalendars.pickMonth(Planets.byId("mercury"), 0.5))
        assertNull("金星无卫星", LocalCalendars.pickMonth(Planets.byId("venus"), 1.925))
        assertTrue("地球必须是月制", LocalCalendars.of(Planets.byId("earth")).monthSystem)
        assertFalse("火星必须星制", LocalCalendars.of(Planets.byId("mars")).monthSystem)
    }

    // ---------- 四、闰日 = 累积余数法 ----------

    /**
     * 地球走公历规则：年首日序必须逐字等于 Cal 的公历换算。
     * 与「累积余数法 floor((y−1)×365.2425)」只在 400 年整倍数上重合（公历是四百年 146097 日的整循环），
     * 单年上两者会差一天，所以地球不能套用别的天体的累积余数公式。
     */
    @Test
    fun earthCalendarFollowsGregorianRules() {
        val spec = LocalCalendars.of(Planets.byId("earth"))
        val base = Cal.toEpochDay(1, 1, 1)
        for (y in listOf(1, 2, 3, 4, 5, 99, 100, 101, 400, 401, 1600, 1900, 2000, 2024, 2025, 2026)) {
            assertEquals(
                "第 $y 年首日序必须与公历一致",
                Cal.toEpochDay(y, 1, 1) - base,
                LocalCalendars.yearStartDay(y.toLong(), spec)
            )
        }
        for (y in listOf(400, 800, 2000, 2400)) {
            assertEquals(
                "第 $y 年是 400 年整循环，累积余数公式必须与公历重合",
                Math.floorDiv((y - 1L) * spec.yearNumerator, spec.yearDenominator),
                LocalCalendars.yearStartDay(y.toLong(), spec)
            )
        }
    }

    /** 闰年只由累积余数产生：连续 400 年，每年天数必须是 365 或 366，且四年一闰、百年不闰、四百年再闰。 */
    @Test
    fun earthLeapYearsEmergeFromAccumulation() {
        val spec = LocalCalendars.of(Planets.byId("earth"))
        var total = 0
        for (y in 1L..400L) {
            val len = LocalCalendars.daysInYear(y, spec)
            assertTrue("第 $y 年天数非法：$len", len == 365 || len == 366)
            total += len
        }
        assertEquals("400 年必须正好 146097 日", 146097, total)
        assertEquals("1900 不是闰年", 365, LocalCalendars.daysInYear(1900, spec))
        assertEquals("2000 是闰年", 366, LocalCalendars.daysInYear(2000, spec))
        assertEquals("2024 是闰年", 366, LocalCalendars.daysInYear(2024, spec))
    }

    /** 火星 668.5991 日/年：年长在 668 与 669 之间交替，1000 年平均必须逼近常数。 */
    @Test
    fun marsLeapDayAbsorbsTheFraction() {
        val spec = LocalCalendars.of(Planets.byId("mars"))
        var total = 0
        for (y in 1L..1000L) {
            val len = LocalCalendars.daysInYear(y, spec)
            assertTrue("火星年长非法：$len", len == 668 || len == 669)
            total += len
        }
        assertEquals("1000 年累计日数应等于 668599.1 ± 1", 668599.1, total.toDouble(), 1.0)
        assertEquals("火星年首日序与累积公式一致", LocalCalendars.yearStartDay(1001, spec), total.toLong())
    }

    /** 十万年也不漂：逐日累加的年长必须与闭式公式给出完全相同的年首日序。 */
    @Test
    fun leapDaysDoNotDriftOverHundredThousandYears() {
        for (id in listOf("mars", "venus", "jupiter")) {
            val spec = LocalCalendars.of(Planets.byId(id))
            var sum = 0L
            for (y in 1L..100_000L) sum += LocalCalendars.daysInYear(y, spec)
            assertEquals(
                "$id 十万年累计日数必须与闭式年首日序一致",
                LocalCalendars.yearStartDay(100_001L, spec),
                sum
            )
        }
    }

    // ---------- 五、不做 ΔT / 闰秒修正 ----------

    /** 地球本地日恒为 86400 SI 秒：连续走 86400 秒必然只前进一天，没有闰秒跳变。 */
    @Test
    fun earthLocalDayIsAlways86400SiSeconds() {
        val earth = Planets.byId("earth")
        var t = Cal.toEpochDay(2016, 12, 31) * 86_400L // 2016-12-31 00:00:00 UTC（当年有闰秒）
        var previous = TimeSystem.localStamp(earth, t)
        for (i in 0 until 5) {
            t += 86_400L
            val next = TimeSystem.localStamp(earth, t)
            assertTrue("第 $i 天必须只前进一天", next.year != previous.year || next.day != previous.day || next.month != previous.month)
            assertEquals("时刻必须保持不变", previous.hour, next.hour)
            assertEquals("分钟必须保持不变", previous.minute, next.minute)
            assertEquals("秒必须保持不变", previous.second, next.second)
            previous = next
        }
        // 极端年份同样不做修正：地球本地读数与 UCT 公历读数逐字段相同
        for (year in listOf(-100_000, -1, 0, 1, 100_000)) {
            val s = Cal.toEpochDay(year, 6, 15) * 86_400L + 43_200L
            val uct = TimeSystem.uctStamp(s)
            val local = TimeSystem.localStamp(earth, s)
            assertEquals("第 $year 年必须与 UCT 完全一致", uct.calendarText(), local.calendarText())
        }
    }

    // ---------- 六、显示：字段带单位标签 ----------

    @Test
    fun everyChainCarriesUnitLabels() {
        val cases = mapOf(
            "earth" to listOf("年", "月", "日", "时", "分", "秒"),
            "mars" to listOf("年", "星", "日", "时", "分", "秒")
        )
        val t = Cal.toEpochDay(2026, 11, 8) * 86_400L + 16 * 3600L + 28 * 60L
        for ((id, units) in cases) {
            val text = TimeSystem.localStamp(Planets.byId(id), t).calendarText()
            for (u in units) assertTrue("$id 的读数缺少单位 $u：$text", text.contains(u))
            assertTrue("$id 的读数不得出现点分简写：$text", !text.contains("."))
        }
        // v0.8.8：十进制粗分天体不再显示时分秒，只给 0–1 的十进制日相位（标签与渲染必须一致）
        for (id in listOf("venus", "mercury")) {
            val s = TimeSystem.localStamp(Planets.byId(id), t)
            assertEquals("$id 必须走十进制粗分", com.skysim.app.core.DayScheme.COARSE_DECIMAL, s.scheme)
            val text = s.calendarText()
            assertFalse("$id 的十进制读数不得出现「时」：$text", text.contains("时"))
            assertFalse("$id 的十进制读数不得出现「分」：$text", text.contains("分"))
            assertFalse("$id 的十进制读数不得出现「秒」：$text", text.contains("秒"))
            assertTrue("$id 必须是「0.x 日」形态：$text", Regex("^.*\\d\\.\\d{4} 日$").matches(text))
        }
        assertEquals(
            "地球示例",
            "2026年11月08日 16时28分00秒",
            TimeSystem.localStamp(Planets.byId("earth"), t).calendarText()
        )
    }

    /** 潮汐锁定天体只给整数度分秒，不叠加小数度。 */
    @Test
    fun tidalLockedBodiesShowIntegerArcUnits() {
        val e = Planets.byId("trappist1e")
        val s = TimeSystem.localStamp(e, 13L * 3600L)
        assertEquals(CalendarChain.PHASE, s.chain)
        val text = s.calendarText()
        assertTrue("相位必须是整数度分秒：$text", Regex("^\\d+°\\d{2}′\\d{2}″$").matches(text))
        assertFalse("不得出现小数度：$text", text.contains("."))
        // v0.8.8：相位按公转周期从统一纪元真实推进（旧值 195° 是把 UCT 当天的日进度当相位）。
        // 13 h 只是 TRAPPIST-1e 公转周期（约 6.10 地球日）的极小一段，所以相位应远小于 195°。
        val periodSeconds = e.orbitalPeriodDays * 86_400.0
        val elapsed = 13L * 3600.0 - TimeSystem.EPOCH_EPOCH_DAY * 86_400L
        val expectedDeg = (elapsed % periodSeconds) / periodSeconds * 360.0
        val shownDeg = text.substringBefore("°").toInt()
        assertTrue("相位度数必须与公转周期一致（期望约 $expectedDeg°，实际 $shownDeg°）",
            kotlin.math.abs(shownDeg - expectedDeg) <= 1.0)
        assertTrue("相位必须落在 0..360", s.orbitPhaseDeg in 0.0..360.0)
    }

    /** 主显示行里不得出现历法名（历法名放标题行），标题与主行是两行分工。 */
    @Test
    fun calendarNameStaysOutOfTheMainLine() {
        val earth = Planets.byId("earth")
        val t = Cal.toEpochDay(2026, 11, 8) * 86_400L
        val s = TimeSystem.localStamp(earth, t)
        assertFalse("主行不得带历法名", s.calendarText().contains("历"))
        assertEquals("历法名在地球标题行", "地球历", s.calendarNameZh)
        assertEquals("公历在 UCT 侧", "公历", TimeSystem.uctStamp(t).calendarNameZh)
    }

    // ---------- 七、全目录巡检 ----------

    /** 68 个天体都要有历法名、合法的链型与单调日期，极端年份也不例外。 */
    @Test
    fun wholeCatalogIsWellFormedAcrossTheAdjustableRange() {
        val samples = listOf(
            Cal.toEpochDay(-100_000, 1, 1) * 86_400L,
            Cal.toEpochDay(1, 1, 1) * 86_400L,
            Cal.toEpochDay(2026, 11, 8) * 86_400L,
            Cal.toEpochDay(100_000, 12, 31) * 86_400L
        )
        for (planet in Planets.all) {
            val spec = LocalCalendars.of(planet)
            assertTrue("${planet.nameZh} 历法名不能为空", spec.calendarNameZh.isNotEmpty())
            assertTrue("${planet.nameZh} 年长必须为正", spec.yearLocalDays > 0.0)
            for (t in samples) {
                val s = TimeSystem.localStamp(planet, t)
                assertTrue("${planet.nameZh} 年号溢出", s.year in -500_000_000..500_000_000)
                assertTrue("${planet.nameZh} 中间单位越界", s.month in 1..s.middleCount.coerceAtLeast(1))
                if (s.chain == CalendarChain.DAY_YEAR) {
                    // 日.年链的日字段是本地日序号（从纪元起算），不是「月内日」
                    // 纪元之前的本地日序号为负（没有第 0 日），这里只禁止 0
                    assertTrue("${planet.nameZh} 本地日序号不允许为 0", s.day != 0)
                    // v0.8.8：一年 0.4999 本地日的水星，一个本地日里会跨 2 次年号，年序最大 2
                    assertTrue("${planet.nameZh} 日里的年序必须落在 0..2（实际 ${s.yearInDay}）", s.yearInDay in 0..2)
                } else {
                    assertTrue("${planet.nameZh} 日越界 ${s.day}", s.day in 1..s.daysInMiddleUnit)
                }
                if (s.chain == CalendarChain.DAY_YEAR) {
                    // 一年 0.4999 本地日的水星，一个本地日里跨 2 次年号 → 日内年序 1..3
                    assertTrue("${planet.nameZh} 日内年序越界", s.dayOfYearLocal in 1..3)
                } else {
                    assertTrue("${planet.nameZh} 年内日序越界", s.dayOfYearLocal in 1..s.daysInYear)
                }
                if (s.phaseOnly) {
                    // 潮汐锁定：时钟冻结（永昼面 12 时 / 永夜面 0 时），主行只显示相位
                    assertTrue("${planet.nameZh} 锁定态时刻必须冻结", s.hour == 0 || s.hour == 12)
                    continue
                }
                // v0.8.8：时钟进位随该天体的进制变化，SI 秒档的小时不封顶
                val maxHour = when (s.scheme) {
                    com.skysim.app.core.DayScheme.COARSE_DECIMAL -> 9
                    com.skysim.app.core.DayScheme.SI_SECONDS -> (planet.solarDaySeconds / 3600.0).toInt()
                    com.skysim.app.core.DayScheme.EQUAL_24 -> 23
                }
                assertTrue("${planet.nameZh} 时刻越界 ${s.hour}:${s.minute}:${s.second}（${s.scheme}）",
                    s.hour in 0..maxHour && s.minute in 0..59 && s.second in 0..59)
                assertTrue("${planet.nameZh} 读数不能为空", s.calendarText().isNotEmpty())
            }
        }
    }

    /** 同一 UCT 瞬间，两个天体不再出现「火星第 15 年 / 水星第 9809 年」那种不可比的纪元。 */
    @Test
    fun neighboringPlanetsShareTheSameEpochOrder() {
        val t = Cal.toEpochDay(2026, 11, 8) * 86_400L
        val earth = TimeSystem.localStamp(Planets.byId("earth"), t).year
        val mars = TimeSystem.localStamp(Planets.byId("mars"), t).year
        val venus = TimeSystem.localStamp(Planets.byId("venus"), t).year
        assertTrue("地球年号应等于公元年份：$earth", earth in 2025..2027)
        // v0.8.8：本地日按真实物理日长推进后，火星年号 ≈ 地球年 × 365.2425/686.98 ≈ 1077（放大 1.0275 的旧读数已消失）
        assertTrue("火星年号应与地球同量级：$mars", mars in 1050..1100)
        // 金星一年 224.7 地球日 → 同一瞬间约 3290 年（旧读数 38 万是把地球日当金星本地日用，放大 116.75 倍）
        assertTrue("金星年号应由同一纪元与物理日长推出：$venus", venus in 3200..3400)
        assertTrue("火星年号应小于地球（年更长）", mars < earth)
    }
}
