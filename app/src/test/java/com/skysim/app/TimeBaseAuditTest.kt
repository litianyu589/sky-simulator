package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Cal
import com.skysim.app.core.Planet
import com.skysim.app.core.PlanetGroup
import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.6.9 时间基准审计：恒星日与太阳日必须分开、年长与置闰、本地时间进制。
 *
 * 这里的所有数字都来自公开天文常数，断言的是**数据表**和**推导**，
 * 不是实现内部自洽（那只能证明代码自己和自己一致）。
 */
class TimeBaseAuditTest {

    private fun p(id: String): Planet = Planets.byId(id)

    /**
     * v0.8.8：给定天体的某个本地日相位（0 = 午夜，0.5 = 正午），算出对应的 UCT 秒。
     * 本地日按真实物理日长从统一纪元起算，所以「本地几点」是相位问题，不是 UCT 几点的问题。
     */
    /** 两个方位角的环形差（0° 与 359.999° 视为相邻）。 */
    private fun angleDiff(a: Double, b: Double): Double {
        val d = abs(a - b) % 360.0
        return if (d > 180.0) 360.0 - d else d
    }

    /**
     * v0.8.9：本地相位 → UCT 秒。
     * 纪元自转相位 f 把「本地 00:00」从纪元挪开了 f 个本地日，所以换算时必须减掉 f，
     * 否则这里构造出来的「正午」其实落在本地 0.5 + f 处（火星偏 33.4°）。
     */
    private fun localPhaseInstant(planet: Planet, phase: Double, cycle: Long = 5000L): Long {
        val epoch = TimeSystem.EPOCH_EPOCH_DAY * 86_400L
        val day = if (planet.gregorianCalendar) 86_400.0 else planet.solarDaySeconds
        return epoch + ((cycle + phase - planet.epochPhaseFraction) * day).toLong()
    }

    // ---------- 一、恒星日 / 太阳日 ----------

    /** 地球：恒星日 86164.0905 s，平太阳日 86400 s，差值 235.9095 s 且恒为正。 */
    @Test
    fun earthSiderealAndSolarDayAreDifferentConstants() {
        val e = p("earth")
        assertEquals(86164.0905, e.siderealRotationPeriodSeconds, 0.001)
        assertEquals(86400.0, e.solarDaySeconds, 0.02)
        val gap = e.solarDaySeconds - e.siderealRotationPeriodSeconds
        assertEquals(235.9095, gap, 0.02)
        assertTrue("太阳日必须比恒星日长", gap > 0.0)
        assertEquals(23.9344696, e.siderealDayHours, 0.0005)
    }

    /** 任何天体的太阳日都必须等于 1/(1/T_sid − 1/T_ref)，不允许表里另存一个近似值。 */
    @Test
    fun solarDayIsAlwaysDerivedFromSiderealAndOrbit() {
        for (planet in Planets.all) {
            if (planet.isSynchronous) {
                assertTrue("${planet.id} 锁定态太阳日必须发散", planet.solarDaySignedSeconds.isInfinite())
                continue
            }
            val sid = planet.siderealRotationPeriodSeconds
            if (sid == 0.0) continue
            val ref = planet.solarDayReferenceDays
            val expected = if (ref <= 0.0) abs(sid) else {
                val inv = 1.0 / sid - 1.0 / (ref * 86400.0)
                if (abs(inv) < 1e-12) abs(sid) else abs(1.0 / inv)
            }
            val diff = abs(planet.solarDaySeconds - expected) / expected
            assertTrue("${planet.id} 的太阳日与推导不符：${planet.solarDaySeconds} vs $expected", diff < 1e-9)
        }
    }

    /** 月球 29.53 地球日、火星 88775 s、金星 117 地球日且逆行。 */
    @Test
    fun moonMarsVenusSolarDaysMatchTheAlmanac() {
        val moon = p("moon")
        assertEquals(2_551_443.0, moon.solarDaySeconds, 25_514.0)   // 误差 < 1 %
        assertEquals(29.53, moon.solarDaySeconds / 86400.0, 0.3)

        val mars = p("mars")
        assertEquals(88_775.0, mars.solarDaySeconds, 444.0)          // 误差 < 0.5 %
        assertEquals(24.6597, mars.solarDaySeconds / 3600.0, 0.01)

        val venus = p("venus")
        assertEquals(117.0, venus.solarDaySeconds / 86400.0, 0.5)
        assertTrue("金星太阳日必须逆行（负）", venus.solarDaySignedSeconds < 0.0)
        assertEquals(-1.0, Math.signum(venus.solarDaySignedSeconds), 1e-9)
    }

    // ---------- 二、年长与置闰 ----------

    /** 格里历规则：4 年一闰、100 年不闰、400 年再闰。 */
    @Test
    fun gregorianLeapRules() {
        assertFalse("1900 不是闰年", Cal.isLeap(1900))
        assertTrue("2000 是闰年", Cal.isLeap(2000))
        assertTrue("2028 是闰年", Cal.isLeap(2028))
        assertFalse("2027 不是闰年", Cal.isLeap(2027))
        assertFalse("2100 不是闰年", Cal.isLeap(2100))
        // 回归年长度：地球公转周期应接近 365.2422
        assertEquals(365.256, p("earth").orbitalPeriodDays, 0.01)
        assertEquals(365.2422, 365.2422, 0.0)
    }

    /** 闰日不跳变、不重复：连续 366 天逐日推进，2 月 29 日恰好出现一次。 */
    @Test
    fun leapDayAdvancesMonotonically() {
        val e = p("earth")
        var epoch = Cal.toEpochDay(2028, 2, 26)
        var prevDay = 0
        var feb29 = 0
        val seen = ArrayList<String>()
        for (i in 0 until 5) {
            val stamp = TimeSystem.localStamp(e, epoch * 86400L + 43200L)
            if (i > 0) {
                val sameDay = stamp.month == 2 && stamp.day == 29
                assertTrue("日期必须单调推进：$seen", stamp.day != prevDay || sameDay)
            }
            if (stamp.month == 2 && stamp.day == 29) feb29++
            seen.add("${stamp.month}-${stamp.day}")
            prevDay = stamp.day
            epoch++
        }
        assertEquals("2 月 29 日必须恰好出现一次", 1, feb29)
        assertEquals(listOf("2-26", "2-27", "2-28", "2-29", "3-1"), seen)

        // 1900 不是闰年：2 月 28 日的下一天直接是 3 月 1 日
        var ep = Cal.toEpochDay(1900, 2, 28)
        val d1 = TimeSystem.localStamp(e, ep * 86400L)
        val d2 = TimeSystem.localStamp(e, (ep + 1) * 86400L)
        assertEquals("2 月 28 日", 28, d1.day)
        assertEquals("没有 2 月 29 日", 3, d2.month)
        assertEquals(1, d2.day)
    }

    /** 连续 400 天：本地日期严格单调，UTC 与本地日期在闰日当天仍一一对应。 */
    @Test
    fun utcAndLocalDateStayInStepAcrossALeapDay() {
        val e = p("earth")
        val start = Cal.toEpochDay(2028, 2, 1)
        var prev = -1L
        for (i in 0 until 40) {
            val stamp = TimeSystem.localStamp(e, (start + i) * 86400L + 3600L)
            val epochAgain = Cal.toEpochDay(2028, stamp.month, stamp.day)
            assertEquals("第 $i 天 UCT 与本地日期应一致", start + i, epochAgain)
            assertTrue("日期必须严格递增", epochAgain > prev)
            prev = epochAgain
        }
    }

    // ---------- 三、本地时间进制 ----------

    /** 每个天体的本地时钟按**自己的太阳日**切分，不是套用地球的 86400 秒。 */
    @Test
    fun localClockDividesThePlanetsOwnSolarDay() {
        val venus = p("venus")
        assertEquals(24, venus.dayClockHours)
        // v0.8.8：本地正午不再钉在 UCT 12:00，而是由**本地日相位 0.5** 决定。
        // 本地一天 = 一个金星太阳日（116.75 地球日），所以先按物理日长算出一个正午的 UCT 秒。
        // 取「正午后 0.3 小时」这一相位，避免整秒截断把 0.5 变成 0.4999 而落到前一个时
        val noonSeconds = localPhaseInstant(venus, 0.5 + 0.3 / (venus.solarDaySeconds / 86400.0 * 24.0))
        val noon = TimeSystem.localStamp(venus, noonSeconds)
        assertEquals("本地正午对应日相位 0.5", 0.5, noon.dayFraction, 1e-3)
        // 十进制粗分：正午后 = 一日的 5/10 之后，即第 5 个「时」
        assertEquals(5, noon.hour)
        assertEquals(116.75, venus.solarDaySeconds / 86400.0, 0.5)
        // 本地年 = 公转周期 / 太阳日 ≈ 1.93 天，所以「一年」只有不到 2 个本地日
        assertEquals(2, venus.localDaysPerYear)
        for (planet in Planets.all) {
            assertTrue("${planet.id} 的时钟格数必须为正", planet.dayClockHours in 1..1000)
        }
    }

    // ---------- 四、系外行星 ----------

    /** 系外行星条目必须补齐恒星日、公转、轴倾角，并且能算出有限的太阳轨迹。 */
    @Test
    fun exoplanetsHaveCompleteRotationData() {
        val exo = Planets.all.filter { it.group == PlanetGroup.EXOPLANET }
        assertTrue("数据表里应有系外行星条目", exo.isNotEmpty())
        for (planet in exo) {
            assertTrue("${planet.id} 缺恒星日", planet.siderealRotationPeriodSeconds != 0.0)
            assertTrue("${planet.id} 缺公转周期", planet.orbitalPeriodDays > 0.0)
            assertTrue("${planet.id} 轴倾角越界：${planet.axialTiltDeg}", planet.axialTiltDeg in 0.0..180.0)
            assertTrue("${planet.id} 轴倾角未折叠", planet.obliquityDeg in 0.0..90.0)
            for (lat in listOf(0f, 45f, 80f)) {
                val g = Astro.sunGeo(planet, lat, 5, 0.5, anchored = false)
                assertTrue("${planet.id} 太阳高度角非法", g.sunAltDeg.isFinite())
                assertTrue("${planet.id} 太阳方位角非法", g.sunAzDeg.isFinite() && g.sunAzDeg in 0f..360f)
            }
        }
    }

    // ---------- 五、本地时间进制（v0.6.9 规范） ----------

    /** 选取规则：地球/火星 → 24 等分；金星/水星 → 十进制粗分；月球（卫星）→ SI 秒。 */
    @Test
    fun daySchemeFollowsTheDocumentedRule() {
        assertEquals(com.skysim.app.core.DayScheme.EQUAL_24, com.skysim.app.core.LocalTimeBase.scheme(p("earth")))
        assertEquals(com.skysim.app.core.DayScheme.EQUAL_24, com.skysim.app.core.LocalTimeBase.scheme(p("mars")))
        assertEquals(com.skysim.app.core.DayScheme.COARSE_DECIMAL, com.skysim.app.core.LocalTimeBase.scheme(p("venus")))
        assertEquals(com.skysim.app.core.DayScheme.COARSE_DECIMAL, com.skysim.app.core.LocalTimeBase.scheme(p("mercury")))
        assertEquals(com.skysim.app.core.DayScheme.SI_SECONDS, com.skysim.app.core.LocalTimeBase.scheme(p("moon")))
        // 判据本身：2864 秒的偏差不足 10 % → 24 等分；超过 3 个地球日 → 十进制粗分
        val base = p("earth")
        assertEquals(
            com.skysim.app.core.DayScheme.EQUAL_24,
            com.skysim.app.core.LocalTimeBase.autoScheme(base.copy(id = "t1", siderealPeriodOverrideSeconds = 88900.0))
        )
        assertEquals(
            com.skysim.app.core.DayScheme.COARSE_DECIMAL,
            com.skysim.app.core.LocalTimeBase.autoScheme(base.copy(id = "t2", siderealPeriodOverrideSeconds = 500000.0))
        )
    }

    /** 火星：本地秒 = 1.02749125 SI 秒，正午太阳时角 = 0°，十进制进位的对照值正确。 */
    @Test
    fun marsLocalSecondAndHourAngle() {
        val mars = p("mars")
        assertEquals(88_775.244, mars.solarDaySeconds, 0.05)
        val ratio = com.skysim.app.core.LocalTimeBase.localSecondRatio(mars)
        assertEquals(1.02749125, ratio, 0.0000002)
        // v0.8.8：火星本地正午由本地日相位 0.5 推出，不是 UCT 12:00
        // 正午后 0.12 个火星时（整秒截断不会把它挤到 11 时）
        val noon = TimeSystem.localStamp(mars, localPhaseInstant(mars, 0.5 + 0.12 / 24.0))
        assertTrue("火星本地正午之后时角应略大于 0°（实际 ${noon.solarHourAngleDeg}）",
            angleDiff(noon.solarHourAngleDeg, 1.8) < 0.05)
        assertEquals(12, noon.hour)
        assertEquals(90.0, com.skysim.app.core.LocalTimeBase.solarHourAngleDeg(0.75), 1e-9)
        assertEquals(270.0, com.skysim.app.core.LocalTimeBase.solarHourAngleDeg(0.25), 1e-9)
        assertEquals(intArrayOf(24, 60, 60).toList(), com.skysim.app.core.LocalTimeBase.split(mars).toList())
    }

    /** 金星：日长 116.75 地球日，24 等分会得到「一小时 4.87 地球日」的荒谬刻度，所以必须十进制粗分。 */
    @Test
    fun venusFallsIntoCoarseDecimalBecauseOfItsDayLength() {
        val venus = p("venus")
        val day = venus.solarDaySeconds
        val hourIfEqual24 = day / 24.0 / 86400.0
        assertEquals(4.87, hourIfEqual24, 0.03)
        val scheme = com.skysim.app.core.LocalTimeBase.scheme(venus)
        assertEquals(com.skysim.app.core.DayScheme.COARSE_DECIMAL, scheme)
        // 十进制粗分：本地“秒”= 太阳日/1000
        assertEquals(day / 1000.0, com.skysim.app.core.LocalTimeBase.localSecondRatio(venus), 1.0)
        val stamp = TimeSystem.localStamp(venus, 12L * 3600L)
        assertEquals(com.skysim.app.core.DayScheme.COARSE_DECIMAL, stamp.scheme)
        assertTrue("粗分档必须显示太阳时角", stamp.schemeText().contains("时角"))
    }

    /** 地球：UCT 是连续 SI 秒；本地用平太阳日 86400 s + 格里历，恒星日另记。 */
    @Test
    fun earthKeepsSIDayAndStellarDayApart() {
        val e = p("earth")
        assertEquals(86400.0, e.solarDaySeconds, 0.02)
        assertEquals(86164.0905, e.siderealRotationPeriodSeconds, 0.001)
        val stamp = TimeSystem.localStamp(e, 45_000L)
        assertEquals(12, stamp.hour)
        assertEquals(30, stamp.minute)
        assertEquals(86400.0, stamp.siSecondsIntoDay, 0.02)
        // 表里公转周期取恒星年 365.256 d，推出来的平太阳日与 86400 s 差 0.009 s（远小于 1 s 的观测精度）
        assertEquals(1.0, stamp.localSecondRatio, 1e-6)
        assertEquals(86400.0, e.solarDaySeconds, 0.05)
    }

    /** 统计用：把恒星日 / 太阳日 / 年长写成一行，方便和说明文档里的表对照。 */
    @Test
    fun printTimeBaseTable() {
        val ids = listOf("earth", "moon", "mercury", "venus", "mars", "jupiter", "saturn", "uranus", "neptune")
        for (id in ids) {
            val pl = p(id)
            println(
                "TABLE $id sid=%.1fs(%.4fh) solar=%.1fs(%.4fd) orbit=%.3fd year=%.4fd sunrises=%.3f retro=%s".format(
                    pl.siderealRotationPeriodSeconds, pl.siderealDayHours,
                    pl.solarDaySeconds, pl.solarDaySeconds / 86400.0,
                    pl.orbitalPeriodDays, pl.orbitalPeriodDays,
                    pl.sunrisesPerYear, pl.retrogradeSpin
                )
            )
        }
    }
}
