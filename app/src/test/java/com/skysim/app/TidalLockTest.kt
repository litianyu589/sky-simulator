package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.SpinState
import com.skysim.app.core.StarSpectrum
import com.skysim.app.core.TimeSystem
import com.skysim.app.core.Twilight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.6.10 潮汐锁定：三态判定（同步 / 3:2 共振 / 自由自转）、锁定态的时间退化、恒星色温决定天空。
 */
class TidalLockTest {

    private val trappist = listOf("trappist1b", "trappist1c", "trappist1d", "trappist1e",
        "trappist1f", "trappist1g", "trappist1h")

    private fun p(id: String): Planet = Planets.byId(id)

    /** 七颗行星的公转周期与公认值一致（1% 内）。 */
    @Test
    fun trappistPeriodsMatchThePublishedEphemeris() {
        val expect = listOf(1.51, 2.42, 4.05, 6.10, 9.21, 12.35, 18.77)
        assertEquals(7, trappist.size)
        for ((i, id) in trappist.withIndex()) {
            val period = p(id).orbitalPeriodDays
            assertEquals("$id 公转周期", expect[i], period, expect[i] * 0.01)
        }
    }

    /** 七颗行星全部按同步锁定处理，比值恰为 1。 */
    @Test
    fun trappistPlanetsAreAllSynchronouslyLocked() {
        for (id in trappist) {
            val planet = p(id)
            assertEquals("$id 比值", 1.0, planet.spinOrbitRatioEffective, 1e-9)
            assertEquals("$id 三态", SpinState.SYNCHRONOUS, planet.spinState)
            assertTrue("$id 标记为锁定", planet.tidallyLocked)
            // 恒星日由 公转周期 × 比值 推出，表里不再存第二份数字
            assertEquals(planet.orbitalPeriodDays * 86400.0, kotlin.math.abs(planet.siderealRotationPeriodSeconds), 1.0)
        }
    }

    /** 同步锁定：太阳日数学上发散，且不是 NaN。 */
    @Test
    fun synchronousPlanetsHaveNoFiniteSolarDay() {
        for (id in trappist) {
            val planet = p(id)
            assertTrue("$id 太阳日必须发散", planet.solarDaySignedSeconds.isInfinite())
            assertFalse("$id 太阳日不能是 NaN", planet.solarDaySignedSeconds.isNaN())
            // 没有本地日之后，历法按轨道周期计日
            assertEquals(Math.round(planet.orbitalPeriodDays).toInt(), planet.localDaysPerYear)
        }
    }

    /** 锁定态的本地时间退化为轨道相位：没有时分秒。 */
    @Test
    fun lockedLocalTimeIsOrbitPhaseOnly() {
        val e = p("trappist1e")
        val stamp = TimeSystem.localStamp(e, 13L * 3600L)
        assertTrue(stamp.phaseOnly)
        assertEquals("—", stamp.timeText())
        assertTrue(stamp.format().contains("轨道相位"))
        // v0.8.8：相位按公转周期从统一纪元真实推进（旧值 13/24×360 = 195° 是「把 UCT 当天日进度当相位」的遗留）
        val period = e.orbitalPeriodDays * 86_400.0
        val elapsed = 13L * 3600.0 - TimeSystem.EPOCH_EPOCH_DAY * 86_400L
        assertEquals((elapsed % period) / period * 360.0, stamp.orbitPhaseDeg, 0.01)
        assertTrue(stamp.schemeText().contains("潮汐锁定"))
    }

    /** 锁定态太阳固定在天空：一天内高度角与方位角都不变，晨昏线固定。 */
    @Test
    fun lockedSunNeverMoves() {
        val e = p("trappist1e")
        val a = Astro.sunGeo(e, 0f, 3, 0.10, anchored = false, tidalNightSide = false)
        val b = Astro.sunGeo(e, 0f, 3, 0.90, anchored = false, tidalNightSide = false)
        assertEquals(a.sunAltDeg, b.sunAltDeg, 1e-4f)
        assertEquals(a.sunAzDeg, b.sunAzDeg, 1e-4f)
        val night = Astro.sunGeo(e, 0f, 3, 0.50, anchored = false, tidalNightSide = true)
        assertTrue(night.sunAltDeg < 0f)
        assertTrue(a.sunAltDeg > 0f)
    }

    /** 验收：把比值从 1 改成 0.5，昼夜、时分秒、白昼长度全部自动恢复，不需要改代码。 */
    @Test
    fun changingTheRatioBringsBackDayAndNight() {
        val locked = p("trappist1e")
        val free = locked.copy(spinOrbitRatio = 0.5, tidallyLocked = false)
        assertEquals(SpinState.FREE, free.spinState)
        assertTrue(free.solarDaySignedSeconds.isFinite())
        assertTrue(free.solarDaySeconds > 0.0)
        val stamp = TimeSystem.localStamp(free, 13L * 3600L)
        assertFalse(stamp.phaseOnly)
        assertNotEquals("—", stamp.timeText())
        val geo = Astro.sunGeo(free, 0f, 3, 0.30, anchored = false)
        val geo2 = Astro.sunGeo(free, 0f, 3, 0.80, anchored = false)
        assertNotEquals("太阳必须重新动起来", geo.sunAltDeg.toDouble(), geo2.sunAltDeg.toDouble(), 1.0)
    }

    /** 自由自转的天体：太阳日必须有限、为正、不是 NaN（除零 / 溢出审查）。 */
    @Test
    fun freeRotatorsAlwaysHaveAFiniteSolarDay() {
        for (planet in Planets.all) {
            if (planet.spinState == SpinState.SYNCHRONOUS) continue
            val day = planet.solarDaySeconds
            assertTrue("${planet.id} 太阳日非法：$day", day.isFinite() && day > 1.0 && !day.isNaN())
            val stamp = TimeSystem.localStamp(planet, 43200L)
            assertTrue("${planet.id} 时刻越界", stamp.hour in 0..23)
        }
    }

    /** 水星式 3:2 共振：周期比 2/3，太阳日 = 2 个轨道年。 */
    @Test
    fun mercuryIsAThreeToTwoResonatorWithATwoYearSolarDay() {
        val m = p("mercury")
        assertEquals(SpinState.RESONANT_3_2, m.spinState)
        assertEquals(2.0 / 3.0, m.spinOrbitRatioEffective, 0.01)
        assertEquals(175.94, m.solarDaySeconds / 86400.0, 0.15)
        assertEquals(2.0, m.solarDaySeconds / (m.orbitalPeriodDays * 86400.0), 0.01)
        // 一个太阳日里太阳回到同一位置（周期正确）
        val a = Astro.sunGeo(m, 0f, 10, 0.0, anchored = false)
        val b = Astro.sunGeo(m, 0f, 10, 1.0, anchored = false)
        assertEquals(a.hourAngleDeg.toDouble(), b.hourAngleDeg.toDouble(), 1e-6)
    }

    /** 恒星色温决定天空色：同一天体，只改恒星温度，天空必须变；地球（5772 K）不受影响。 */
    @Test
    fun skyColourFollowsTheHostStarTemperature() {
        val earth = p("earth")
        val sun = StarSpectrum.rgbWeight(5772.0)
        assertEquals(1.0, sun[0], 0.02)
        assertEquals(1.0, sun[1], 0.02)
        assertEquals(1.0, sun[2], 0.02)

        val mDwarf = earth.copy(starTempK = 2566.0, isStation = false)
        val earthSky = Twilight.radiance(earth, 30f, 180f, 20f, 200f, 0.65f)
        val mSky = Twilight.radiance(mDwarf, 30f, 180f, 20f, 200f, 0.65f)
        val earthBlueOverRed = earthSky.b / earthSky.r.coerceAtLeast(1e-6f)
        val mBlueOverRed = mSky.b / mSky.r.coerceAtLeast(1e-6f)
        assertTrue("M 型星的天空必须明显更红：$mBlueOverRed vs $earthBlueOverRed",
            mBlueOverRed < earthBlueOverRed * 0.5)
        val w = StarSpectrum.rgbWeight(2566.0)
        assertTrue("2566 K 的蓝光权重应远小于红光", w[2] < w[0] * 0.2)
    }

    /** TRAPPIST-1 的天空不是地球蓝：直接拿数据表里的行星比。 */
    @Test
    fun trappistSkyIsNotEarthBlue() {
        val earth = p("earth")
        // 给 TRAPPIST-1 e 一个示意大气（真实大气未知），比较「同一大气、不同恒星」的天空
        val e = p("trappist1e").copy(rayTau = 0.10, aerosolTau = 0.03, aerosolTint = earth.aerosolTint)
        val earthSky = Twilight.radiance(earth, 30f, 180f, 25f, 200f, 0.65f)
        val trappistSky = Twilight.radiance(e, 30f, 180f, 25f, 200f, 0.65f)
        assertTrue("TRAPPIST-1 的星星温度应远低于太阳", e.starTempK < 3000f)
        assertTrue("应偏红而不是偏蓝", trappistSky.b < trappistSky.r)
        assertTrue("蓝红比必须低于地球", (trappistSky.b / trappistSky.r) < (earthSky.b / earthSky.r) * 0.6)
    }

    /** 数据表不允许再出现「照搬公转周期」的恒星日：锁定态一律由比值推出。 */
    @Test
    fun noLockedRowStoresACopiedRotationPeriod() {
        for (planet in Planets.all) {
            if (!planet.isSynchronous) continue
            assertEquals("${planet.id} 仍在表里另存恒星日", 0.0, planet.siderealPeriodOverrideSeconds, 1e-9)
        }
    }
}
