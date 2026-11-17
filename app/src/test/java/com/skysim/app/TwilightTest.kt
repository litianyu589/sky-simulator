package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.Twilight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v0.6.4：晨昏分层色带、维纳斯带、地影、日出日落对称性。 */
class TwilightTest {

    private val earth = Planets.byId("earth")
    private val on = Twilight.Setting(true, 0.65f)

    private fun sky(planetId: String, sunAlt: Float, viewAlt: Float, viewAz: Float, sunAz: Float = 0f): Rgb =
        SkyModel.toSrgb(SkyModel.softClip(SkyModel.radiance(Planets.byId(planetId), viewAlt, sunAlt, viewAz, sunAz, on)))

    private fun lum(c: Rgb) = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b

    /** 日出日落对称：同一方程只看太阳高度角，把视线/太阳方位整体镜像后结果必须一致。 */
    @Test
    fun sunriseAndSunsetAreSymmetric() {
        for (alt in listOf(-0.5f, -2f, -4f, -6f)) {
            val a = sky("earth", alt, 4f, 30f, 0f)
            val b = sky("earth", alt, 4f, 210f, 180f)
            assertEquals(a.r, b.r, 1e-5f)
            assertEquals(a.g, b.g, 1e-5f)
            assertEquals(a.b, b.b, 1e-5f)
        }
    }

    /** 分层：地平线最暖，往上逐渐变冷；天顶必须仍是蓝的。 */
    @Test
    fun layersGoFromOrangeHorizonToBlueZenith() {
        val sun = 0f
        val horizon = sky("earth", sun, 1f, 0f)
        val mid = sky("earth", sun, 8f, 0f)
        val high = sky("earth", sun, 30f, 0f)
        val zenith = sky("earth", sun, 80f, 0f)
        // 红蓝差（暖度）随高度单调递减
        assertTrue("地平线应比中层暖", (horizon.r - horizon.b) > (mid.r - mid.b) + 0.05f)
        assertTrue("中层应比高层暖", (mid.r - mid.b) > (high.r - high.b) + 0.02f)
        assertTrue("天顶必须偏蓝", zenith.b > zenith.r + 0.08f)
        // 地平线亮度高于天顶（分层与亮度同时成立）
        assertTrue(lum(horizon) > lum(zenith))
        // 中间必须经过粉橘/淡紫过渡：绿通道夹在红蓝之间
        assertTrue(mid.r > mid.g && mid.g > mid.b)
    }

    /** 蓝调时刻：日落后反日侧的天顶比贴地平线更亮。 */
    @Test
    fun blueHourZenithStaysBrighterThanHorizon() {
        val anti = 180f
        val nearHorizon = sky("earth", -4f, 1f, anti, 0f)
        val zenith = sky("earth", -4f, 80f, anti, 0f)
        assertTrue("天顶应比地平线亮", lum(zenith) > lum(nearHorizon))
        assertTrue("此时天顶应偏蓝", zenith.b > zenith.r)
    }

    /** 维纳斯带：太阳高度 0～-6° 出现，位于地影之上，且只在反日侧。 */
    @Test
    fun venusBeltAppearsBetweenZeroAndMinusSix() {
        assertEquals(0f, Twilight.beltFraction(0.05f, 1f, 6f), 1e-4f)
        assertTrue(Twilight.beltFraction(1.5f, 1f, 6f) > 0.15f)
        assertTrue(Twilight.beltFraction(3f, 1f, 8f) > 0.15f)
        assertTrue(Twilight.beltFraction(6f, 1f, 12f) > 0.05f)
        assertEquals("朝太阳一侧没有维纳斯带", 0f, Twilight.beltFraction(3f, 0f, 8f), 1e-4f)
        // 带子上边界之上不应再有带子
        val top = Twilight.beltTopDeg(3f)
        assertEquals(0f, Twilight.beltFraction(3f, 1f, top + 1f), 1e-4f)
    }

    /** 地影：随太阳下沉而升高，贴地平线时遮挡最强。 */
    @Test
    fun earthShadowRisesAsSunSinks() {
        var last = -1f
        for (dep in listOf(1f, 2f, 3f, 5f, 8f)) {
            val top = Twilight.shadowTopDeg(dep)
            assertTrue("地影顶端应随俯角单调升高", top > last)
            last = top
        }
        val shallow = Twilight.shadowFraction(2f, 1f, 1f)
        val deep = Twilight.shadowFraction(6f, 1f, 3f)
        assertTrue("俯角更大时同一高度仍在影内", deep > 0.5f)
        assertTrue(shallow > 0.5f)
        assertEquals("太阳在地平线上时没有地影", 0f, Twilight.shadowFraction(0f, 1f, 1f), 1e-4f)
    }

    /** 朝日侧比背日侧更亮更暖。 */
    @Test
    fun sunSideIsBrighterAndWarmerThanAntiSolarSide() {
        val sunSide = sky("earth", 0f, 3f, 0f, 0f)
        val antiSide = sky("earth", 0f, 3f, 180f, 0f)
        assertTrue(lum(sunSide) > lum(antiSide))
        assertTrue((sunSide.r - sunSide.b) > (antiSide.r - antiSide.b))
    }

    /** 气溶胶主导的火星日落在太阳一侧偏冷偏蓝，地球则偏红。 */
    @Test
    fun aerosolDominatedSunsetIsCooler() {
        val mars = sky("mars", 0f, 2f, 0f, 0f)
        val earthSunset = sky("earth", 0f, 2f, 0f, 0f)
        assertTrue("火星日落地平线应偏蓝", mars.b > mars.r)
        assertTrue("地球日落地平线应偏红", earthSunset.r > earthSunset.b)
        assertTrue("火星应比地球更冷", (mars.b - mars.r) > 0f)
    }

    /** 厚云天体（金星）保持米黄，不能被分层模型弄成蓝色。 */
    @Test
    fun thickCloudPlanetStaysBeige() {
        val venus = sky("venus", 0f, 3f, 0f, 0f)
        assertTrue(venus.r > venus.g && venus.g > venus.b)
    }

    /** 强度 0 必须严格等于 v0.6.3 的渲染路径（「对比」按钮与回退路径的保证）。 */
    @Test
    fun zeroStrengthFallsBackToLegacyModel() {
        for (alt in listOf(30f, 2f, -3f, -8f)) {
            val legacy = SkyModel.radiance(earth, 6f, alt, 30f, 0f)
            val zero = SkyModel.radiance(earth, 6f, alt, 30f, 0f, Twilight.Setting(true, 0f))
            assertEquals(legacy.r, zero.r, 1e-6f)
            assertEquals(legacy.g, zero.g, 1e-6f)
            assertEquals(legacy.b, zero.b, 1e-6f)
            val off = SkyModel.radiance(earth, 6f, alt, 30f, 0f, Twilight.Setting(false, 0.65f))
            assertEquals(legacy.r, off.r, 1e-6f)
        }
    }

    /** 臭氧 Chappuis 带：峰值在 600 nm 附近，500–700 nm 宽带，短波端接近 0。 */
    @Test
    fun ozoneAbsorptionPeaksNear600nm() {
        val peak = Twilight.ozoneRelative(600.0)
        assertTrue(peak > Twilight.ozoneRelative(550.0))
        assertTrue(peak > Twilight.ozoneRelative(680.0))
        assertTrue(peak > Twilight.ozoneRelative(500.0))
        assertTrue(Twilight.ozoneRelative(460.0) < 0.05)
        assertTrue(Twilight.ozoneRelative(760.0) < 0.05)
        assertEquals(0.0, Twilight.ozoneRelative(400.0), 1e-6)
    }

    /** 太阳高度连续变化时颜色不能跳变（避免出现硬边色带）。 */
    @Test
    fun colourIsContinuousInSunAltitude() {
        var prev = sky("earth", -20f, 2f, 0f, 0f)
        var alt = -20f
        while (alt <= 20f) {
            val c = sky("earth", alt, 2f, 0f, 0f)
            assertTrue("在 $alt° 处出现跳变", abs(c.r - prev.r) < 0.09f && abs(c.g - prev.g) < 0.09f && abs(c.b - prev.b) < 0.09f)
            prev = c
            alt += 0.25f
        }
    }

    /** 分层模型对所有大气天体都必须给出有限、非负、在 0..1 内的颜色。 */
    @Test
    fun allBodiesProduceValidColours() {
        for (p in Planets.all) {
            for (sun in listOf(45f, 3f, -2f, -7f)) {
                for (v in listOf(0.5f, 6f, 45f, 88f)) {
                    val c = SkyModel.toSrgb(SkyModel.softClip(SkyModel.radiance(p, v, sun, 90f, 0f, on)))
                    for (x in floatArrayOf(c.r, c.g, c.b)) {
                        assertTrue("${p.id} sun=$sun v=$v 颜色越界: $x", x.isFinite() && x >= 0f && x <= 1f)
                    }
                }
            }
        }
    }
}
