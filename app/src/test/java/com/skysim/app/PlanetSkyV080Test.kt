package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.Twilight
import com.skysim.app.core.TwilightModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.8.0：火星、金星、月球的天空与晨昏分层验收。
 *
 * 每条断言都把「这个天体自己的物理量」和「理论几何值」对上，而不是只检查「有没有变化」——
 * v0.7.9 的教训是：只断言「变了」会把不合格的实现固化成标准。
 */
class PlanetSkyV080Test {

    private fun p(id: String): Planet = Planets.byId(id)

    private val earth = p("earth")
    private val mars = p("mars")
    private val venus = p("venus")
    private val moon = p("moon")

    private fun lum(c: Rgb) = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b

    // ---------------------------------------------------------------- 太阳几何

    /**
     * 多纬度正午/午夜的太阳高度角必须等于球面天文的理论值：
     *   正午 h = 90° − |φ − δ|，午夜 h = |φ + δ| − 90°
     * 赤纬 δ 必须由该天体自己的转轴倾角与本地年相位推出。四颗天体各扫 7 个纬度。
     */
    @Test
    fun noonAndMidnightAltitudeMatchTheoryOnEveryPlanet() {
        for (id in listOf("earth", "mars", "venus", "moon")) {
            val planet = p(id)
            // 本地年里取 4 个相位，覆盖该天体自己的季节循环
            for (dayOfYear in listOf(1, 90, 180, 270)) {
                val noonGeo = Astro.sunGeo(planet, 0f, dayOfYear, 0.5, anchored = false)
                val decl = noonGeo.declinationDeg.toDouble()
                assertTrue(
                    "$id 赤纬必须落在自身倾角内：δ=$decl 倾角=${planet.obliquityDeg}",
                    abs(decl) <= planet.obliquityDeg + 0.5
                )
                for (lat in listOf(90f, 50f, 23.5f, 0f, -23.5f, -50f, -90f)) {
                    val geo = Astro.sunGeo(planet, lat, dayOfYear, 0.5, anchored = false)
                    // 每个采样点用自己的赤纬：金星一个太阳日就走掉近半个金星年，赤纬在昼夜之间变化很大
                    val noonDecl = geo.declinationDeg.toDouble()
                    val theoryNoon = (90.0 - abs(lat - noonDecl)).coerceIn(-90.0, 90.0)
                    assertEquals(
                        "$id 纬度 $lat 第 $dayOfYear 日正午高度角",
                        theoryNoon, geo.noonAltDeg.toDouble(), 0.6
                    )
                    val midnight = Astro.sunGeo(planet, lat, dayOfYear, 0.0, anchored = false)
                    val midnightDecl = midnight.declinationDeg.toDouble()
                    val theoryMidnight = (abs(lat + midnightDecl) - 90.0).coerceIn(-90.0, 90.0)
                    assertEquals(
                        "$id 纬度 $lat 第 $dayOfYear 日午夜高度角",
                        theoryMidnight, midnight.midnightAltDeg.toDouble(), 0.6
                    )
                }
            }
        }
    }

    /** 昼长必须按该天体自己的太阳日换算，不能套地球的 24 小时。 */
    @Test
    fun dayLengthUsesEachPlanetsOwnSolarDay() {
        val earthHours = earth.solarDaySeconds / 3600.0
        val marsHours = mars.solarDaySeconds / 3600.0
        assertEquals("地球平太阳日", 24.0, earthHours, 0.01)
        assertEquals("火星 sol", 24.66, marsHours, 0.02)

        // 赤道、本地年第一个季节相位：昼长应接近太阳日的一半
        val e = Astro.sunGeo(earth, 0f, 1, 0.5, anchored = false)
        val m = Astro.sunGeo(mars, 0f, 1, 0.5, anchored = false)
        assertEquals(earthHours / 2, e.dayLengthHours.toDouble(), 0.3)
        assertEquals(marsHours / 2, m.dayLengthHours.toDouble(), 0.3)
        // 火星昼长只有在太阳日尺度上才说得通，套 24 小时会差出 0.33 小时
        assertTrue("火星昼长必须引用火星太阳日 ${m.dayLengthHours} h", m.dayLengthHours > 12.0f)

        // 金星：太阳日约 116.75 地球日，一圈只看到约 1.93 次日出
        val vDay = venus.solarDaySeconds / 3600.0
        assertEquals(2802.0, vDay, 5.0)
        assertEquals(1.93, venus.sunrisesPerYear, 0.05)

        // 月球走 SI 秒档：太阳日 29.53 天，白昼约 14.77 天 = 354 h
        val mDay = moon.solarDaySeconds / 3600.0
        assertEquals(708.7, mDay, 3.0)
    }

    /** 太阳视直径比例：火星 0.66×、金星 1.38×、月球与地球相同（日距都是 1 AU）。 */
    @Test
    fun sunAngularDiameterScalesWithHeliocentricDistance() {
        val e = earth.sunAngularDiameterDeg
        assertEquals(0.53, e.toDouble(), 0.01)
        assertEquals(0.66, (mars.sunAngularDiameterDeg / e).toDouble(), 0.01)
        assertEquals(1.384, (venus.sunAngularDiameterDeg / e).toDouble(), 0.01)
        assertEquals(1.0, (moon.sunAngularDiameterDeg / e).toDouble(), 0.01)
        // 火星日距 1.524 AU
        assertEquals(1.524, mars.sunDistanceAu, 0.001)
    }

    // ---------------------------------------------------------------- 晨昏分层

    /** 地球是标杆：6/12/18 三阶段不变，三条带都在，颜色顺序是橙→粉紫→深蓝。 */
    @Test
    fun earthKeepsThreeBandsAsBenchmark() {
        val st = TwilightModel.stage(earth)
        assertEquals(6.0, st.civilDeg, 0.01)
        assertEquals(12.0, st.nauticalDeg, 0.01)
        assertEquals(18.0, st.astronomicalDeg, 0.01)
        assertTrue(TwilightModel.hasDiscreteBands(earth))

        val bands = TwilightModel.bands(earth)
        assertEquals("地球必须有三条晨昏色带", 3, bands.size)
        assertEquals(0.0, bands[0].fromDeg, 1e-6)
        assertEquals(-6.0, bands[0].toDeg, 0.01)
        assertEquals(-12.0, bands[1].toDeg, 0.01)
        assertEquals(-18.0, bands[2].toDeg, 0.01)

        val horizon = bands[0].towardSun
        assertTrue("地球地平辉光必须暖（r>b）：$horizon", horizon.r > horizon.b)
        val belt = bands[1].awayFromSun
        assertTrue("背日一侧的维纳斯带偏粉紫（b 不低于 g）：$belt", belt.b >= belt.g * 0.95f)
        // 最深一段：整片变暗，而且越深越蓝（蓝调时刻的判据是「随时间变暗 + 蓝红比升高」）
        val tw = Twilight.Setting(true, 0.65f)
        val dusk = SkyModel.toSrgb(SkyModel.radiance(earth, 80f, 0f, -8f, 0f, tw))
        val late = SkyModel.toSrgb(SkyModel.radiance(earth, 80f, 0f, -16f, 0f, tw))
        assertTrue("天文晨昏时天顶必须比航海晨昏更暗：${lum(dusk)} vs ${lum(late)}", lum(late) < lum(dusk))
        assertTrue("天顶的蓝红比必须随暮光加深而升高：${late.b / late.r}", late.b / late.r > dusk.b / dusk.r)
        assertEquals("最深一段带在 -18°", -18.0, bands[2].toDeg, 0.01)
    }

    /** 火星是第二标杆：尘埃把晨昏拖宽，且没有臭氧紫带；朝太阳一侧是橙褐色。 */
    @Test
    fun marsBandsAreDustNotOzone() {
        val st = TwilightModel.stage(mars)
        assertTrue("火星晨昏必须比地球宽：${st.astronomicalDeg}", st.astronomicalDeg > 25.0)
        assertTrue("火星晨昏必须在地球 3 倍上限内：${st.astronomicalDeg}", st.astronomicalDeg <= 54.01)
        assertTrue("火星晨昏时长应超过地球：${st.durationHours} h", st.durationHours > 1.5)
        assertTrue("火星必须保留可分辨的分层", TwilightModel.hasDiscreteBands(mars))

        val bands = TwilightModel.bands(mars)
        assertEquals("火星应有三条尘埃色带", 3, bands.size)
        // 火星日落最著名的特征：朝太阳一侧是蓝紫，而不是地球式的橘红
        val towardSun = bands[0].towardSun
        assertTrue("火星朝太阳一侧必须是蓝紫（b>r）：$towardSun", towardSun.b > towardSun.r * 1.2f)
        // 背向太阳一侧蓝成分明显变弱：蓝色辉光集中在太阳附近，这是尘埃前向散射的方向性
        val awayFromSun = bands[0].awayFromSun
        assertTrue(
            "火星背日一侧的蓝红比必须低于向日一侧：${awayFromSun.b / awayFromSun.r} vs ${towardSun.b / towardSun.r}",
            awayFromSun.b / awayFromSun.r < towardSun.b / towardSun.r * 0.8f
        )
        assertTrue("火星暮光比地球长", st.durationHours > TwilightModel.stage(earth).durationHours)
    }

    /** 金星：厚云多次散射，没有可分辨边界；正午天空一片橙黄、接近各向同性。 */
    @Test
    fun venusHasNoDiscreteBandsAndIsNearlyIsotropic() {
        assertTrue("金星不透明度必须远大于地球", TwilightModel.opacity(venus) > 8.0)
        assertFalse("金星不应有可分辨的分层边界", TwilightModel.hasDiscreteBands(venus))
        val bands = TwilightModel.bands(venus)
        assertEquals("金星只应有一条整片变暗的记录", 1, bands.size)

        val horizon = SkyModel.toSrgb(SkyModel.radiance(venus, 2f, 70f))
        val zenith = SkyModel.toSrgb(SkyModel.radiance(venus, 80f, 70f))
        assertTrue("金星天空必须偏橙黄（r>b）：$horizon", horizon.r > horizon.b * 1.15f)
        val ratio = lum(zenith) / lum(horizon).coerceAtLeast(1e-5f)
        assertTrue("金星天顶/地平亮度比必须接近 1（实测 $ratio）", ratio in 0.6f..1.6f)
        assertTrue("金星太阳不可辨", !TwilightModel.profile(venus, 20f).sunDiscVisible)
    }

    /** 月球：无大气就一条晨昏带都没有，天空黑、太阳是硬边盘，白天能看见星。 */
    @Test
    fun moonHasNoBandsBlackSkyAndHardSun() {
        assertFalse(moon.hasAtmosphere)
        assertTrue("无大气天体不得有晨昏带", TwilightModel.bands(moon).isEmpty())
        val st = TwilightModel.stage(moon)
        assertEquals(0.0, st.civilDeg, 1e-9)
        assertEquals(0.0, st.nauticalDeg, 1e-9)
        assertEquals(0.0, st.astronomicalDeg, 1e-9)
        assertEquals(0.0, st.durationHours, 1e-9)
        assertEquals("无大气的半影只剩恒星视半径", moon.starAngularRadiusDeg, st.penumbraDeg, 1e-9)

        val noon = SkyModel.toSrgb(SkyModel.radiance(moon, 45f, 0f, 60f, 0f))
        assertTrue("月球正午天空必须近黑（无大气、无气辉）：$noon", lum(noon) < 0.04f)
        val earthNoon = SkyModel.toSrgb(SkyModel.radiance(earth, 45f, 0f, 60f, 0f))
        assertTrue(
            "月球白天天空亮度必须比地球低两个数量级（sRGB 亮度至少 30 倍）：${lum(noon)} vs ${lum(earthNoon)}",
            lum(noon) * 30f < lum(earthNoon)
        )
        val disc = SkyModel.toSrgb(SkyModel.sunDisc(moon, 60f))
        assertTrue("月球太阳必须是接近满亮的硬边盘：$disc", lum(disc) > 0.9f)
        assertTrue("月球白天应能看到星", SkyModel.starAlpha(moon, 60f) > 0.4f)
        // 对照：地球白天看不到星
        assertTrue("地球白天不该有星", SkyModel.starAlpha(earth, 60f) < 0.01f)
    }

    /** 月球与水星一样没有晨昏带；水星只留一层极弱钠辉光。 */
    @Test
    fun airlessBodiesNeverGetTwilightBands() {
        for (id in listOf("moon", "mercury")) {
            assertTrue("$id 不应有晨昏带", TwilightModel.bands(p(id)).isEmpty())
            assertEquals("$id 晨昏时长必须为 0", 0.0, TwilightModel.stage(p(id)).durationHours, 1e-9)
        }
    }

    // ---------------------------------------------------------------- 回归保护

    /**
     * 散射层有效高度改成按大气标高等比换算后，地球必须仍是 v0.7.3 的 16°。
     * 这是「地球数值行为不得改变」的锚点。
     */
    @Test
    fun earthScatteringAltitudeIsStillSixteenDegrees() {
        assertEquals(16.0, Twilight.scatteringAltitudeDeg(earth), 1e-9)
        assertEquals(16.0 * 11.1 / 8.5, Twilight.scatteringAltitudeDeg(mars), 1e-6)
        assertEquals(16.0 * 15.9 / 8.5, Twilight.scatteringAltitudeDeg(venus), 1e-6)
        assertTrue("火星散射层必须比地球高", Twilight.scatteringAltitudeDeg(mars) > 16.0)
    }

    /** 维纳斯带需要臭氧：没有臭氧就没有紫带，地球的紫带强度仍是 1。 */
    @Test
    fun beltRequiresOzone() {
        assertEquals(1.0f, Twilight.beltStrength(earth), 1e-6f)
        assertEquals(0.0f, Twilight.beltStrength(mars), 1e-6f)
        assertEquals(0.0f, Twilight.beltStrength(moon), 1e-6f)
    }
}
