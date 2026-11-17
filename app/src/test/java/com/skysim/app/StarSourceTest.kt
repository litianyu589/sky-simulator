package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.BodyCatalog
import com.skysim.app.core.DataConfidence
import com.skysim.app.core.Planet
import com.skysim.app.core.PlanetGroup
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.StarCatalog
import com.skysim.app.core.StarData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 0.6.2 外部改动的针对性测试：多恒星光源、Kepler-16 双星示例、厚雾太阳可见性。
 * 这些能力之前没有测试，审阅时补上，避免「改了但没人钉住」。
 */
class StarSourceTest {

    private val kepler16: Planet = Planets.byId("kepler16b")

    @Test
    fun binarySystemExposesTwoLightSources() {
        val star = StarData.byId("kepler16")
        assertTrue("Kepler-16 应标为双星", star.isBinary)
        val sources = StarCatalog.visualSources("kepler16", kepler16.sunDistanceAu)
        assertEquals(2, sources.size)
        assertEquals("Kepler-16 A", sources[0].nameZh)
        assertEquals("Kepler-16 B", sources[1].nameZh)
        assertEquals("主星方位偏移应为 0", 0.0, sources[0].azimuthOffsetDeg, 1e-9)
        assertTrue("伴星应有示意角偏移", sources[1].azimuthOffsetDeg > 0.0)
        assertTrue("主星视直径应大于伴星",
            sources[0].angularDiameterDeg > sources[1].angularDiameterDeg)
        assertTrue("伴星也要有可见视直径", sources[1].angularDiameterDeg > 0.0)
        val total = sources.sumOf { it.irradianceSolar }
        assertTrue("双星总辐照度应大于单主星", total > sources[0].irradianceSolar)
        assertTrue("总辐照度应为两者之和",
            Math.abs(total - (sources[0].irradianceSolar + sources[1].irradianceSolar)) < 1e-12)
        // 伴星更冷更暗，颜色应比主星更红
        assertTrue(sources[1].color.r / sources[1].color.b >= sources[0].color.r / sources[0].color.b)
    }

    @Test
    fun kepler16bIsReachableAndHonestlyLabelled() {
        assertEquals("kepler16", kepler16.hostStarId)
        assertEquals(PlanetGroup.EXOPLANET, kepler16.group)
        assertEquals(DataConfidence.MODELED, kepler16.confidence)
        assertTrue(kepler16.skyBasisZh.isNotEmpty())
        assertTrue("环双星行星不应被当成空间站", !kepler16.isStation)
        // 两级导航：恒星 → 天体，且能被当前天体解析入口找到
        assertEquals(1, StarCatalog.bodies("kepler16").size)
        assertEquals("kepler16b", BodyCatalog.resolve("kepler16b").id)
        assertEquals("kepler16", BodyCatalog.resolve("kepler16b").hostStarId)
        // 有行星的恒星不该再生成空间站
        assertEquals(null, StarCatalog.stationFor(StarData.byId("kepler16")))
    }

    @Test
    fun singleStarsKeepTheirOwnColour() {
        for (star in StarCatalog.stars) {
            if (star.isBinary) continue
            val sources = StarCatalog.visualSources(star.id, 1.0)
            assertEquals("${star.nameZh} 应是单光源", 1, sources.size)
        }
        val earth = Planets.byId("earth")
        assertEquals(earth.starColor.r, earth.skyIlluminationColor.r, 1e-6f)
        assertEquals(earth.starColor.b, earth.skyIlluminationColor.b, 1e-6f)
    }

    @Test
    fun mixedStarColourStaysWithinBothSources() {
        val c1 = StarData.byId("kepler16").color
        val c2 = StarData.visualSources("kepler16", kepler16.sunDistanceAu)[1].color
        val mixed: Rgb = kepler16.skyIlluminationColor
        for ((a, b, m) in listOf(
            Triple(c1.r, c2.r, mixed.r), Triple(c1.g, c2.g, mixed.g), Triple(c1.b, c2.b, mixed.b)
        )) {
            assertTrue("混合色应在两光源之间", m >= minOf(a, b) - 1e-6f && m <= maxOf(a, b) + 1e-6f)
        }
    }

    @Test
    fun hazySunStaysVisibleOnVenusAndTitan() {
        for (id in listOf("venus", "titan")) {
            val p = Planets.byId(id)
            assertTrue("$id 应走厚雾分支", p.aerosolTau >= 1.0)
            // 直射透射率确实极低——这就是为什么不能直接用它画太阳
            assertTrue("$id 直射透射率应很低", SkyModel.sunTransmission(p, 30f) < 0.1f)
            val spot = SkyModel.hazySunSpot(p, 30f)
            val lum = 0.2126f * spot.r + 0.7152f * spot.g + 0.0722f * spot.b
            assertTrue("$id 的太阳亮斑必须有可见亮度，实测 $lum", lum > 0.3f)
            // 夜间不该有太阳亮斑
            val night = SkyModel.hazySunSpot(p, -12f)
            val nightLum = 0.2126f * night.r + 0.7152f * night.g + 0.0722f * night.b
            assertTrue("$id 夜间亮斑应变暗", nightLum < lum)
        }
    }

    @Test
    fun clearSkySunTransmissionStaysReasonable() {
        val earth = Planets.byId("earth")
        assertTrue("地球白天透射率应较高", SkyModel.sunTransmission(earth, 45f) > 0.5f)
        assertEquals("无大气天体不衰减直射光", 1f, SkyModel.sunTransmission(Planets.byId("moon"), 45f), 1e-6f)
        // 太阳圆盘颜色在无大气天体上应等于母星颜色（不额外染色）
        val moonDisc = SkyModel.sunDisc(Planets.byId("moon"), 45f)
        val star = Planets.byId("moon").starColor
        assertEquals(star.r, moonDisc.r, 1e-6f)
    }

    @Test
    fun srgbEncodingIsWellFormed() {
        assertEquals(0f, SkyModel.toSrgb(Rgb(0f, 0f, 0f)).r, 1e-6f)
        assertEquals(1f, SkyModel.toSrgb(Rgb(1f, 1f, 1f)).r, 1e-6f)
        val mid = SkyModel.toSrgb(Rgb(0.5f, 0.5f, 0.5f)).r
        assertTrue("sRGB 编码应抬高中灰", mid > 0.5f && mid < 1f)
        // 负值不应产生 NaN
        val neg = SkyModel.toSrgb(Rgb(-1f, -1f, -1f))
        assertEquals(0f, neg.r, 1e-6f)
    }

    @Test
    fun airlessBodiesStillHaveNoWeatherAfterSkyChanges() {
        for (id in listOf("mercury", "moon", "ceres", "toi700b", "kepler90b")) {
            val p = Planets.byId(id)
            assertNotNull(p)
            val clear = SkyModel.display(p, 45f, 40f)
            assertEquals("$id 无大气不应被天气压暗", clear, SkyModel.applyWeather(clear, p, 1f, 1f, 40f))
            assertTrue("$id 天空应接近黑", clear.r < 0.02f && clear.b < 0.02f)
            assertFalse("$id 不该有云", com.skysim.app.core.FxRules.cloudCapability(p) > 0f)
            assertFalse("$id 不该有虹", com.skysim.app.core.FxRules.rainbowVisible(p, 20f, 1f))
            assertFalse("$id 不该有晕", com.skysim.app.core.FxRules.haloVisible(p, 30f, 0.3f))
            assertFalse("$id 不该有极光", com.skysim.app.core.FxRules.auroraVisible(p, 90f, -30f))
        }
    }

    @Test
    fun earthRainbowStillRespectsTheFortyTwoDegreeLimit() {
        val earth = Planets.byId("earth")
        assertTrue(com.skysim.app.core.FxRules.rainbowVisible(earth, 20f, 0.5f))
        assertTrue(com.skysim.app.core.FxRules.rainbowVisible(earth, 42f, 0.5f))
        assertFalse("高于 42° 不该有地面虹", com.skysim.app.core.FxRules.rainbowVisible(earth, 42.5f, 0.5f))
        assertFalse(com.skysim.app.core.FxRules.rainbowVisible(earth, 1.5f, 0.5f))
        // 晕只在地球：木星云顶不套用地球冰晶晕
        assertFalse(com.skysim.app.core.FxRules.haloVisible(Planets.byId("jupiter"), 30f, 0.3f))
        assertTrue(com.skysim.app.core.FxRules.haloVisible(earth, 30f, 0.3f))
        // 极光仍受磁场与纬度门控
        assertTrue(com.skysim.app.core.FxRules.auroraVisible(earth, 70f, -20f))
        assertFalse(com.skysim.app.core.FxRules.auroraVisible(earth, 20f, -20f))
        assertFalse(com.skysim.app.core.FxRules.auroraVisible(Planets.byId("mars"), 80f, -20f))
    }

    @Test
    fun daytimeFactorMatchesSunAltitude() {
        assertEquals(0f, Astro.daylightFactor(-30f), 1e-6f)
        assertTrue(Astro.daylightFactor(30f) > 0.9f)
    }
}
