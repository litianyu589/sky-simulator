package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class CoreTest {

    @Test
    fun catalogIntegrity() {
        assertEquals("v0.8.3：太阳系 26 + 系外行星 50（v0.8.3 新增 8 颗）", 76, Planets.all.size)
        for (p in Planets.all) {
            assertEquals("${p.nameZh} 月数", p.monthsPerYear, p.monthLengths.size)
            assertEquals("${p.nameZh} 月长合计", p.localDaysPerYear, p.monthLengths.sum())
            assertTrue("${p.nameZh} 年至少一天", p.localDaysPerYear >= 1)
        }
    }

    @Test
    fun dayYearRatios() {
        assertEquals(669, Planets.byId("mars").localDaysPerYear)
        assertEquals(2, Planets.byId("venus").localDaysPerYear)
        assertEquals(12, Planets.byId("moon").localDaysPerYear)
        assertTrue(abs(Planets.byId("mars").localSecondLengthSeconds - 1.0275) < 0.001)
    }

    @Test
    fun noonIsHighest() {
        val earth = Planets.byId("earth")
        val g = Astro.sunGeo(earth, 0f, 172, 0.5, false)
        assertEquals(g.noonAltDeg.toDouble(), g.sunAltDeg.toDouble(), 0.01)
        assertTrue("赤道正午太阳应接近天顶", g.sunAltDeg > 65f)
    }

    @Test
    fun polarDayAndNight() {
        val earth = Planets.byId("earth")
        val summer = Astro.sunGeo(earth, 90f, 172, 0.0, false)
        assertTrue("北极夏至极昼", summer.polarDay)
        assertTrue(summer.sunAltDeg > 0f)
        val winter = Astro.sunGeo(earth, 90f, 355, 0.5, false)
        assertTrue("北极冬至极夜", winter.polarNight)
        assertTrue(winter.sunAltDeg < 0f)
    }

    @Test
    fun anchoredSunriseAtSix() {
        val earth = Planets.byId("earth")
        val g = Astro.sunGeo(earth, 45f, 172, 0.25, true)
        assertEquals("锚定模式 06:00 应为日出处", 0.0, g.sunAltDeg.toDouble(), 0.6)
        val sunset = Astro.sunGeo(earth, 45f, 172, 0.75, true)
        assertEquals("锚定模式 18:00 应为日落处", 0.0, sunset.sunAltDeg.toDouble(), 0.6)
        val noon = Astro.sunGeo(earth, 45f, 172, 0.5, true)
        assertTrue(noon.sunAltDeg > 60f)
    }

    @Test
    fun physicalDayLengthVaries() {
        val earth = Planets.byId("earth")
        val summer = Astro.sunGeo(earth, 50f, 172, 0.5, false)
        val winter = Astro.sunGeo(earth, 50f, 355, 0.5, false)
        assertTrue("北纬 50° 夏至昼长应超过 15 小时", summer.dayLengthHours > 15f)
        assertTrue("北纬 50° 冬至昼长应少于 9 小时", winter.dayLengthHours < 9f)
    }

    @Test
    fun localCalendarStaysInRange() {
        val t = TimeSystem.nowUctSeconds()
        for (p in Planets.all) {
            val s = TimeSystem.localStamp(p, t)
            // v0.8.6：中间单位由单位链决定（月 / 星 / 无），不再借用 Planet.monthsPerYear。
            assertTrue("${p.nameZh} 中间单位越界 ${s.month}", s.month in 1..s.middleCount.coerceAtLeast(1))
            if (s.chain == com.skysim.app.core.CalendarChain.DAY_YEAR) {
                // 「日.年」链的日字段是本地日序号（水星 0.5 本地日/年，一日之内跨两个年）
                assertTrue("${p.nameZh} 本地日序号不允许为 0", s.day != 0)
                // v0.8.8：水星一年 0.4999 本地日 → 一个本地日里会跨过 2 个年号边界，年序最大 2
                assertTrue("${p.nameZh} 日里的年序必须落在 0..2（实际 ${s.yearInDay}）", s.yearInDay in 0..2)
            } else {
                assertTrue("${p.nameZh} 日越界 ${s.day}", s.day in 1..s.daysInMiddleUnit)
            }
            // v0.8.8：日.年链下一个本地日可跨 2 个年号边界，日内年序 0..2，日序 1..3
            if (s.chain == com.skysim.app.core.CalendarChain.DAY_YEAR) {
                assertTrue("${p.nameZh} 日内年序越界 ${s.dayOfYearLocal}", s.dayOfYearLocal in 1..3)
            } else {
                assertTrue("${p.nameZh} 年内日序越界 ${s.dayOfYearLocal}", s.dayOfYearLocal in 1..s.daysInYear)
            }
            // v0.8.8：时钟按该天体自己的进制进位——24 等分 0..23、十进制 0..9、SI 秒档的小时不封顶
            // 潮汐锁定天体没有本地昼夜，时钟冻结在永昼面 12 时 / 永夜面 0 时，不参与进制检查
            if (s.phaseOnly) {
                assertTrue("${p.nameZh} 锁定态时刻必须是 0 或 12 时", s.hour == 0 || s.hour == 12)
                assertTrue("${p.nameZh} 锁定态分秒必须为 0", s.minute == 0 && s.second == 0)
                continue
            }
            val maxHour = when (s.scheme) {
                com.skysim.app.core.DayScheme.COARSE_DECIMAL -> 9
                com.skysim.app.core.DayScheme.SI_SECONDS -> (p.solarDaySeconds / 3600.0).toInt()
                com.skysim.app.core.DayScheme.EQUAL_24 -> 23
            }
            assertTrue("${p.nameZh} 时越界 ${s.hour}（进制 ${s.scheme}）", s.hour in 0..maxHour)
            assertTrue("${p.nameZh} 分越界 ${s.minute}", s.minute in 0..59)
            assertTrue("${p.nameZh} 秒越界 ${s.second}", s.second in 0..59)
        }
        val earth = TimeSystem.localStamp(Planets.byId("earth"), t)
        val uct = TimeSystem.uctStamp(t)
        assertEquals(uct.year, earth.year)
        assertEquals(uct.month, earth.month)
        assertEquals(uct.day, earth.day)
    }

    @Test
    fun skyColourMatchesExpectation() {
        val earth = SkyModel.display(Planets.byId("earth"), 60f, 60f)
        assertTrue("地球天顶应偏蓝", earth.b > earth.g && earth.g > earth.r)
        val mars = SkyModel.display(Planets.byId("mars"), 60f, 55f)
        assertTrue("火星天空应偏黄褐", mars.r > mars.g && mars.g > mars.b)
        val venus = SkyModel.display(Planets.byId("venus"), 45f, 30f)
        assertTrue("金星天空应偏橙黄", venus.r >= venus.b && venus.g >= venus.b)
        val uranus = SkyModel.display(Planets.byId("uranus"), 45f, 20f)
        assertTrue("天王星天空应偏青", uranus.b >= uranus.r && uranus.g > uranus.r)
        val moon = SkyModel.display(Planets.byId("moon"), 30f, 30f)
        assertTrue("月面无大气应接近全黑", moon.r < 0.05f && moon.b < 0.05f)
    }

    @Test
    fun uctFractionWraps() {
        val t = TimeSystem.nowUctSeconds()
        val f = TimeSystem.uctFractionOfDay(t)
        assertTrue(f >= 0.0 && f < 1.0)
        val s0 = TimeSystem.uctStamp(0L)
        assertEquals(1970, s0.year)
        assertEquals(1, s0.month)
        assertEquals(1, s0.day)
    }
    @Test
    fun skyModelCoversAllMajorPlanetsWithFiniteColours() {
        val ids = listOf("mercury", "venus", "earth", "mars", "jupiter", "saturn", "uranus", "neptune")
        for (id in ids) {
            val p = Planets.byId(id)
            val c = SkyModel.display(p, 45f, 45f)
            assertTrue("$id red finite", c.r.isFinite() && c.r in 0f..1f)
            assertTrue("$id green finite", c.g.isFinite() && c.g in 0f..1f)
            assertTrue("$id blue finite", c.b.isFinite() && c.b in 0f..1f)
        }
        val venus = SkyModel.display(Planets.byId("venus"), 45f, 45f)
        val titan = SkyModel.display(Planets.byId("titan"), 45f, 45f)
        assertTrue("金星厚云层仍应有可见漫射天光", venus.r > 0.2f && venus.g > venus.b)
        assertTrue("土卫六橙色雾霾应保持暖色", titan.r > titan.g && titan.g > titan.b)
    }

    @Test
    fun solarAngularDiameterAndTransmissionAreDistanceAware() {
        val mercury = Planets.byId("mercury")
        val earth = Planets.byId("earth")
        val neptune = Planets.byId("neptune")
        assertTrue("水星太阳视直径应大于地球", mercury.sunAngularDiameterDeg > earth.sunAngularDiameterDeg * 2f)
        assertTrue("海王星太阳视直径应小于地球", neptune.sunAngularDiameterDeg < earth.sunAngularDiameterDeg * 0.04f)
        assertTrue("金星厚云显著削弱直射太阳", SkyModel.sunTransmission(Planets.byId("venus"), 45f) < 0.01f)
        assertTrue("土卫六雾霾削弱直射太阳", SkyModel.sunTransmission(Planets.byId("titan"), 45f) < 0.1f)
    }

    @Test
    fun linearRgbUsesCorrectSrgbTransfer() {
        val encoded = SkyModel.toSrgb(Rgb(0.18f, 0.18f, 0.18f))
        assertEquals(0.461f, encoded.r, 0.002f)
        assertEquals(encoded.r, encoded.g, 0.0001f)
        assertEquals(encoded.g, encoded.b, 0.0001f)
    }

    @Test
    fun marsSunsetForwardScatteringAddsBlueNearSun() {
        val mars = Planets.byId("mars")
        val near = SkyModel.radiance(mars, 3f, 0f, 0f, 0f)
        val far = SkyModel.radiance(mars, 3f, 0f, 90f, 0f)
        assertTrue("火星日落太阳附近应比远离太阳方向更蓝", near.b > far.b)
    }

    @Test
    fun exoplanetSunDiscUsesHostStarColour() {
        val redDwarfPlanet = Planets.byId("trappist1b")
        val disc = SkyModel.sunDisc(redDwarfPlanet, 45f)
        assertTrue("M 型红矮星太阳盘应偏红", disc.r > disc.b)
        val sunLike = SkyModel.sunDisc(Planets.byId("earth"), 45f)
        assertTrue("太阳盘颜色应接近中性白", sunLike.r >= sunLike.g && sunLike.g >= sunLike.b)
    }

}
