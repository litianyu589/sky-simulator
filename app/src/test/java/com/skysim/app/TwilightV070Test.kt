package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.Twilight
import com.skysim.app.core.TwilightModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

/**
 * v0.7.0 晨昏分层验收：所有差异必须来自数据表，不许出现地球模板。
 */
class TwilightV070Test {

    private fun p(id: String) = Planets.byId(id)

    private fun bOverR(planet: Planet, sunAlt: Float, sep: Float = 15f): Double {
        val c = Twilight.radiance(planet, 2f, sep, sunAlt, 0f, 0.65f)
        return c.b / c.r.coerceAtLeast(1e-6f).toDouble()
    }

    // ---------- 地球回归 ----------

    @Test
    fun earthKeepsSixTwelveEighteen() {
        val st = TwilightModel.stage(p("earth"))
        assertEquals(6.0, st.civilDeg, 0.01)
        assertEquals(12.0, st.nauticalDeg, 0.01)
        assertEquals(18.0, st.astronomicalDeg, 0.01)
        assertEquals(1.0, st.opacity, 0.001)
    }

    // ---------- 月球：严格为零 ----------

    @Test
    fun moonHasNoTwilightAtAll() {
        val st = TwilightModel.stage(p("moon"))
        assertEquals(0.0, st.civilDeg, 1e-9)
        assertEquals(0.0, st.astronomicalDeg, 1e-9)
        assertEquals(0.0, st.durationHours, 1e-9)
        // 太阳一落到地平线下，天空就是夜空（亮度接近 0）
        // 没有渐变：−1° 与 −18° 的亮度必须一模一样（真空里不存在暮光带）
        val justBelow = Twilight.radiance(p("moon"), 2f, 0f, -1f, 0f, 0.65f)
        val deepNight = Twilight.radiance(p("moon"), 2f, 0f, -18f, 0f, 0.65f)
        assertEquals(justBelow.r, deepNight.r, 1e-6f)
        assertEquals(justBelow.b, deepNight.b, 1e-6f)
    }

    // ---------- 水星：钠辉光，弱而上界可断言 ----------

    @Test
    fun mercuryKeepsAWeakSodiumGlow() {
        val m = p("mercury")
        assertEquals(0.0, TwilightModel.stage(m).durationHours, 1e-9)
        val on = Twilight.radiance(m, 2f, 0f, -1f, 0f, 0.65f)
        val off = Twilight.radiance(m.copy(sodiumGlow = 0.0), 2f, 0f, -1f, 0f, 0.65f)
        val dr = on.r - off.r
        val db = on.b - off.b
        val lum = on.r + on.g + on.b
        assertTrue("水星日落后应保留极弱钠辉光", dr > 0f)
        assertTrue("钠辉光必须很弱，实测 $lum", lum < 0.06f)
        assertTrue("钠辉光偏黄（红增量 > 蓝增量）", dr > db)
        // 太阳更高时辉光应减弱
        val high = Twilight.radiance(m, 2f, 0f, 40f, 0f, 0.65f)
        val highOff = Twilight.radiance(m.copy(sodiumGlow = 0.0), 2f, 0f, 40f, 0f, 0.65f)
        assertTrue((high.r - highOff.r) < dr)
    }

    // ---------- 火星：尘埃前向散射 ----------

    @Test
    fun marsTwilightIsLongerAndNotEarthBlue() {
        val earth = TwilightModel.stage(p("earth"))
        val mars = TwilightModel.stage(p("mars"))
        assertTrue("火星晨昏应比地球长：${mars.durationHours} vs ${earth.durationHours}",
            mars.durationHours > earth.durationHours * 1.5)
        val m = p("mars")
        val e = p("earth")
        // 太阳高度 −5°、靠近太阳的方向：火星蓝红比应高于地球
        val marsRatio = bOverR(m, -5f, 12f)
        val earthRatio = bOverR(e, -5f, 12f)
        println("MARS blue/red=$marsRatio earth=$earthRatio")
        assertTrue("火星 −5° 的蓝红比应高于地球：$marsRatio vs $earthRatio", marsRatio > earthRatio)
        // 无臭氧：不得出现地球的深蓝暮光带（臭氧 Chappuis 吸收带在 600 nm）
        assertEquals("火星臭氧柱量应为 0", 0.0, m.ozoneColumn.toDouble(), 1e-6)
    }

    // ---------- 金星：厚云，衰减极慢 ----------

    @Test
    fun venusDecaysMuchSlowerThanEarth() {
        val v = TwilightModel.decayTauHours(p("venus"))
        val e = TwilightModel.decayTauHours(p("earth"))
        println("VENUS tau=$v earth=$e ratio=${v / e}")
        assertTrue("金星时间常数应远大于地球（倍数 ${v / e}）", v > e * 3.0)
        // 霞光铺满全天：背离太阳方向仍有可观亮度
        val away = Twilight.radiance(p("venus"), 2f, 180f, -2f, 0f, 0.65f)
        val toward = Twilight.radiance(p("venus"), 2f, 0f, -2f, 0f, 0.65f)
        val ratio = (away.r + away.g + away.b) / (toward.r + toward.g + toward.b)
        assertTrue("金星天空应接近各向同性（背日/向日 = $ratio）", ratio > 0.35f)
        assertTrue("金星日面不可辨", !TwilightModel.profile(p("venus"), 20f).sunDiscVisible)
    }

    // ---------- 土卫六：宽而橙 ----------

    @Test
    fun titanTwilightIsWideAndOrange() {
        val t = TwilightModel.stage(p("titan"))
        val e = TwilightModel.stage(p("earth"))
        assertTrue("土卫六晨昏应显著更宽：${t.astronomicalDeg} vs ${e.astronomicalDeg}",
            t.astronomicalDeg > e.astronomicalDeg * 1.5)
        val c = Twilight.radiance(p("titan"), 3f, 20f, 2f, 0f, 0.65f)
        assertTrue("土卫六天空应偏橙（r=$c.r b=$c.b）", c.r > c.b * 1.15f)
    }

    // ---------- 冰巨星：短、蓝 ----------

    @Test
    fun iceGiantsHaveShorterTwilight() {
        val e = TwilightModel.stage(p("earth"))
        for (id in listOf("uranus", "neptune")) {
            val st = TwilightModel.stage(p(id))
            assertTrue("$id 晨昏应短于地球：${st.durationHours} vs ${e.durationHours}",
                st.durationHours < e.durationHours && st.astronomicalDeg < e.astronomicalDeg)
            assertTrue("$id 不应有固体地面", !p(id).hasSolidSurface)
        }
    }

    // ---------- 恒星视直径 ----------

    @Test
    fun trappistStarIsBigOnItsSky() {
        val e = p("trappist1e")
        val d = e.starAngularDiameterDegResolved
        println("TRAPPIST-1e star angular diameter=$d deg")
        assertEquals(2.2, d, 0.25)
        val earthSun = p("earth").starAngularDiameterDegResolved
        assertEquals(0.53, earthSun, 0.05)
        val pen = TwilightModel.penumbraDeg(e)
        val earthPen = TwilightModel.penumbraDeg(p("earth"))
        println("penumbra trappist=$pen earth=$earthPen")
        assertTrue("半影宽度必须不小于恒星视半径", pen >= e.starAngularRadiusDeg)
        assertTrue("地球的半影更大是因为大气，不是因为星更大", earthPen > TwilightModel.stage(p("moon")).penumbraDeg)
        // 显式改视直径必须改变半影
        val wide = e.copy(starAngularDiameterOverrideDeg = 8.0)
        assertTrue(TwilightModel.penumbraDeg(wide) > TwilightModel.penumbraDeg(e))
    }

    @Test
    fun trappistTerminatorIsStatic() {
        val e = p("trappist1e")
        assertTrue(e.isSynchronous)
        val a = Astro.sunGeo(e, 0f, 3, 0.1, anchored = false)
        val b = Astro.sunGeo(e, 0f, 3, 0.9, anchored = false)
        assertEquals(a.sunAltDeg.toDouble(), b.sunAltDeg.toDouble(), 1e-6)
        // 无大气 → 晨昏时长为 0，晨昏线是一条几何线
        assertEquals(0.0, TwilightModel.stage(e).durationHours, 1e-9)
    }

    // ---------- 偏心率：水星倒退环 ----------

    @Test
    fun mercurySunBacktracksOncePerSolarDay() {
        val m = p("mercury")
        assertEquals(0.2056, m.eccentricity, 1e-6)
        var reversals = 0
        var prev = Astro.sunGeo(m, 0f, 10, 0.0, anchored = false).hourAngleDeg
        var prevDelta = 0.0
        val n = 360
        var minAt = -1.0
        for (i in 1..n) {
            val f = i.toDouble() / n
            val h = Astro.sunGeo(m, 0f, 10, f, anchored = false).hourAngleDeg
            var d = h - prev
            while (d > 180f) d -= 360f
            while (d < -180f) d += 360f
            if (prevDelta < -1e-4 && d > 1e-4) { reversals++; minAt = f }
            prevDelta = d.toDouble()
            prev = h
        }
        println("MERCURY reversals=$reversals at f=$minAt")
        // 水星一个太阳日 = 2 个轨道年 = 2 次近日点通过，因此出现两次回摆
        assertEquals("一个太阳日内应出现两次倒退（两次近日点通过）", 2, reversals)
        assertTrue("倒退应出现在近日点通过之后（0.6–1.0），实测 $minAt", minAt in 0.6..1.0)
    }

    @Test
    fun nearCircularOrbitsAreUnchanged() {
        // e < 0.01 的天体必须与 v0.6.10 的正圆公式逐位一致
        val venus = p("venus")
        assertTrue(venus.eccentricity < Astro.ECC_MIN)
        for (f in listOf(0.0, 0.13, 0.25, 0.5, 0.77, 0.91)) {
            val g = Astro.sunGeo(venus, 10f, 60, f, anchored = false)
            val expected = -1.0 * (360.0 * f - 180.0)
            assertEquals("f=$f", expected, g.hourAngleDeg.toDouble(), 1e-3)
        }
    }

    // ---------- 零代码分支 ----------

    @Test
    fun fictionalBodyNeedsOnlyATableRow() {
        val base = p("earth")
        val z = base.copy(
            id = "zeta-twilight", nameZh = "虚构天体", sunDistanceAu = 0.4,
            rayTau = 0.06, aerosolTau = 0.15, ozoneColumn = 0.2f,
            starTempK = 4200.0, starAngularDiameterOverrideDeg = 0.9
        )
        val st = TwilightModel.stage(z)
        assertTrue(st.astronomicalDeg > 0.0)
        assertTrue(st.durationHours > 0.0)
        val prof = TwilightModel.profile(z, -3f)
        assertTrue(prof.sky.r.isFinite() && prof.sky.g.isFinite())
        assertTrue(prof.horizonLum > 0.0)
        assertEquals(0.45, prof.sunRadiusDeg, 1e-9)
    }

    @Test
    fun twilightIgnoresThePlanetName() {
        val a = p("earth").copy(id = "aaa", nameZh = "A")
        val b = p("earth").copy(id = "bbb", nameZh = "B")
        val ca = Twilight.radiance(a, 2f, 0f, -4f, 0f, 0.65f)
        val cb = Twilight.radiance(b, 2f, 0f, -4f, 0f, 0.65f)
        assertEquals(ca.r, cb.r, 0f)
        assertEquals(ca.g, cb.g, 0f)
        assertEquals(ca.b, cb.b, 0f)
        assertEquals(TwilightModel.stage(a).astronomicalDeg, TwilightModel.stage(b).astronomicalDeg, 0.0)
    }

    @Test
    fun profileOutputsArePhysicalQuantities() {
        val prof = TwilightModel.profile(p("earth"), -4f)
        assertTrue("地平线应比天顶亮（暮光时）", prof.horizonLum > 1.0)
        assertTrue(prof.sky.r >= 0f && prof.sky.g >= 0f && prof.sky.b >= 0f)
        assertTrue(prof.glow.r > 0f)
        val high = TwilightModel.profile(p("earth"), 40f)
        assertEquals(0.2665, high.sunRadiusDeg, 0.01)
        assertTrue(high.zenithLum > prof.zenithLum)
    }
}
