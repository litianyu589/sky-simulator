package com.skysim.app

import com.skysim.app.core.DataConfidence
import com.skysim.app.core.Planets
import com.skysim.app.core.Twilight
import com.skysim.app.core.TwilightModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** v0.8.1：对完整天体目录做暮光模型健全性巡检，而不只抽查太阳系四颗天体。 */
class TwilightV081Test {
    @Test
    fun everyCatalogBodyHasFiniteOrderedTwilightOutput() {
        // v0.8.2：目录实际是 68 条，0.8.1 的 43 是说明文档里的统计口径，断言写错了。
        assertEquals("v0.8.3 必须巡检完整的 76 个目录天体（68 + 新增 8 颗）", 76, Planets.all.size)
        for (planet in Planets.all) {
            val stage = TwilightModel.stage(planet)
            assertTrue("${planet.id}: civil 必须有限", stage.civilDeg.isFinite())
            assertTrue("${planet.id}: nautical 必须有限", stage.nauticalDeg.isFinite())
            assertTrue("${planet.id}: astronomical 必须有限", stage.astronomicalDeg.isFinite())
            assertTrue("${planet.id}: duration 必须有限", stage.durationHours.isFinite())
            assertTrue("${planet.id}: 边界必须递增", stage.civilDeg <= stage.nauticalDeg && stage.nauticalDeg <= stage.astronomicalDeg)
            assertTrue("${planet.id}: 时长不能为负", stage.durationHours >= 0.0)
            val bands = TwilightModel.bands(planet)
            for (band in bands) {
                for (channel in listOf(band.towardSun.r, band.towardSun.g, band.towardSun.b,
                    band.awayFromSun.r, band.awayFromSun.g, band.awayFromSun.b)) {
                    assertTrue("${planet.id}: 色带颜色必须有限且在显示范围", channel.isFinite() && channel in 0f..1f)
                }
            }
            if (!planet.hasAtmosphere) {
                assertTrue("${planet.id}: 无大气不能生成散射暮光带", bands.isEmpty())
                assertEquals("${planet.id}: 无大气暮光时长必须为零", 0.0, stage.durationHours, 1e-9)
            }
            if (planet.confidence == DataConfidence.UNKNOWN) {
                assertFalse("${planet.id}: 未知大气不能被渲染参数暗中认定为有大气", planet.hasAtmosphere)
            }
        }
    }

    @Test
    fun earthRetainsFormalTwilightAnglesAndOnlyEarthUsesFormalExplanation() {
        val earth = Planets.byId("earth")
        assertEquals(6.0, TwilightModel.stage(earth).civilDeg, 1e-9)
        assertEquals(12.0, TwilightModel.stage(earth).nauticalDeg, 1e-9)
        assertEquals(18.0, TwilightModel.stage(earth).astronomicalDeg, 1e-9)
        assertTrue(TwilightModel.explain(earth).contains("民用"))
        for (planet in Planets.all.filter { it.id != "earth" && it.hasAtmosphere }) {
            val explanation = TwilightModel.explain(planet)
            assertTrue("${planet.id}: 不应把地球正式分级套用到其它天体", explanation.contains("非地球标准分级"))
        }
    }

    @Test
    fun transientPlumesAndExospheresDoNotBecomeGlobalTwilightSkies() {
        for (id in listOf("io", "enceladus", "eris")) {
            val body = Planets.byId(id)
            assertFalse("$id 的局部羽流/极稀薄外逸层不应被当作全球大气", body.hasAtmosphere)
            assertTrue("$id 不应生成全球晨昏色带", TwilightModel.bands(body).isEmpty())
            assertEquals("$id 全局暮光时长必须为零", 0.0, TwilightModel.stage(body).durationHours, 1e-9)
            assertEquals("$id 全局暮光不透明度必须为零", 0.0, TwilightModel.opacity(body), 1e-9)
        }
        // 有实测/已知的极稀薄全球大气仍保留弱散射，而不是一刀切为无大气。
        assertTrue("冥王星有全球氮气大气", Planets.byId("pluto").hasAtmosphere)
        assertTrue("海卫一有全球氮气大气", Planets.byId("triton").hasAtmosphere)
    }

    @Test
    fun shadowTintFollowsTheLocalStarAndAerosolInsteadOfOneEarthBlueGray() {
        val earthShadow = Twilight.shadowColour(Planets.byId("earth"))
        val redDwarfShadow = Twilight.shadowColour(Planets.byId("trappist1d"))
        val difference = kotlin.math.abs(earthShadow.r - redDwarfShadow.r) +
            kotlin.math.abs(earthShadow.g - redDwarfShadow.g) +
            kotlin.math.abs(earthShadow.b - redDwarfShadow.b)
        assertTrue("不同恒星光谱与气溶胶的地影色应不同", difference > 0.01f)
    }

    @Test
    fun ozonePinkBandIsNotAppliedToDustAndCloudWorldsWithoutOzone() {
        for (planet in Planets.all.filter { it.ozoneColumn <= 0.01f }) {
            assertEquals("${planet.id}: 无臭氧不得套用地球型臭氧色带", 0f, Twilight.beltStrength(planet), 1e-6f)
        }
        assertEquals(1f, Twilight.beltStrength(Planets.byId("earth")), 1e-6f)
    }
}
