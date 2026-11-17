package com.skysim.app

import com.skysim.app.core.EffectId
import com.skysim.app.core.EffectState
import com.skysim.app.core.FxRules
import com.skysim.app.core.FxSetting
import com.skysim.app.core.Planets
import com.skysim.app.core.SkyModel
import com.skysim.app.core.WeatherState
import com.skysim.app.ui.pointOnCone
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

class EffectsTest {

    private val earth get() = Planets.byId("earth")
    private val mars get() = Planets.byId("mars")
    private val moon get() = Planets.byId("moon")
    private val venus get() = Planets.byId("venus")
    private val titan get() = Planets.byId("titan")

    @Test
    fun capabilityTable() {
        assertTrue("地球有云", FxRules.cloudCapability(earth) > 0.5f)
        assertEquals("月球没有大气也就没有云", 0f, FxRules.cloudCapability(moon), 0.0001f)
        assertTrue("地球会下雨", FxRules.rainCapability(earth) > 0.5f)
        assertEquals("火星没有降雨（只有尘暴）", 0f, FxRules.rainCapability(mars), 0.0001f)
        assertTrue("土卫六有甲烷雨", FxRules.rainCapability(titan) > 0f)
        assertTrue("金星有高空硫酸雨", FxRules.rainCapability(venus) > 0f)
        assertTrue("火星尘霾能力强", FxRules.hazeCapability(mars) > 0.5f)
    }

    @Test
    fun rainbowOnlyBelow42Degrees() {
        assertTrue("20° 太阳 + 降水应能看到虹", FxRules.rainbowVisible(earth, 20f, 0.6f))
        assertFalse("太阳高于 42° 时虹顶落到地平线以下", FxRules.rainbowVisible(earth, 43f, 0.6f))
        assertFalse("太阳太低也不明显", FxRules.rainbowVisible(earth, 1f, 0.6f))
        assertFalse("没有降水就没有虹", FxRules.rainbowVisible(earth, 20f, 0f))
        assertFalse("火星不下雨，就没有虹", FxRules.rainbowVisible(mars, 20f, 1f))
        assertFalse("金星高空硫酸雨不画地面普通彩虹", FxRules.rainbowVisible(venus, 20f, 1f))
        assertTrue("土卫六甲烷雨采用不同的示意角度", FxRules.rainbowVisible(titan, 45f, 0.6f))
    }

    @Test
    fun haloNeedsCirrusAndDaylight() {
        assertTrue("薄云 + 30° 太阳", FxRules.haloVisible(earth, 30f, 0.3f))
        assertFalse("阴天看不到晕", FxRules.haloVisible(earth, 30f, 0.95f))
        assertFalse("太阳在地平线下没有晕", FxRules.haloVisible(earth, -2f, 0.3f))
        assertFalse("没有大气就没有晕", FxRules.haloVisible(moon, 30f, 0.3f))
        assertFalse("木星云顶不套用地球冰晶日晕", FxRules.haloVisible(Planets.byId("jupiter"), 30f, 0.3f))
    }

    @Test
    fun auroraNeedsFieldLatitudeAndNight() {
        assertTrue("地球 70°N 夜间", FxRules.auroraVisible(earth, 70f, -12f))
        assertTrue("地球 70°S 夜间", FxRules.auroraVisible(earth, -70f, -12f))
        assertFalse("低纬度没有极光", FxRules.auroraVisible(earth, 20f, -12f))
        assertFalse("白天看不到极光", FxRules.auroraVisible(earth, 70f, 5f))
        assertFalse("月球没有全球磁场", FxRules.auroraVisible(moon, 90f, -30f))
        assertFalse("金星没有全球磁场", FxRules.auroraVisible(venus, 80f, -30f))
        assertTrue("木星有极光", FxRules.auroraVisible(Planets.byId("jupiter"), 55f, -20f))
    }

    @Test
    fun lightningOnlyInStorms() {
        assertTrue("地球暴雨有闪电", FxRules.lightningVisible(earth, 3))
        assertFalse("中雨不打雷", FxRules.lightningVisible(earth, 2))
        assertFalse("火星尘暴不算降水闪电", FxRules.lightningVisible(mars, 3))
    }

    @Test
    fun autoWeatherIsDeterministicAndBounded() {
        val a = FxRules.autoWeather(1_700_000_000.0)
        val b = FxRules.autoWeather(1_700_000_000.0)
        assertEquals(a, b)
        assertTrue(a.cloudLevel in 0..3)
        assertTrue(a.precipLevel in 0..3)
        if (a.cloudLevel < 2) assertEquals("云量不足时不应降水", 0, a.precipLevel)
        // 12 小时内天气应当发生变化
        val levels = (0..12).map { FxRules.autoWeather(1_700_000_000.0 + it * 3600.0).cloudLevel }.toSet()
        assertTrue("自动天气应当随时间变化，实际只有 $levels", levels.size >= 2)
        // 手动模式应保持原样
        val manual = WeatherState(cloudLevel = 3, precipLevel = 2, auto = false)
        assertEquals(manual, FxRules.resolveWeather(manual, 1.0))
    }

    /** 反日点几何：以反日点为中心、角半径 42° 的点，到反日点的角距离必须是 42°。 */
    @Test
    fun coneGeometryKeepsAngularRadius() {
        val centerAz = 210.0
        val centerAlt = -15.0
        for (r in listOf(22.0, 42.0, 51.0)) {
            var theta = 0.0
            while (theta < 360.0) {
                val p = pointOnCone(centerAz, centerAlt, r, theta)
                val d = angularDistance(centerAz, centerAlt, p[0], p[1])
                assertTrue("半径 $r° 在 θ=$theta 处偏成了 $d°", abs(d - r) < 0.01)
                theta += 15.0
            }
        }
        // 彩虹中心就是太阳正对面：方位差 180°
        val center = pointOnCone(210.0, -15.0, 0.0, 0.0)
        assertEquals("半径 0 就是中心本身", 210.0, center[0], 0.01)
        assertEquals(-15.0, center[1], 0.01)
    }

    @Test
    fun weatherDarkensSky() {
        val clear = SkyModel.display(earth, 45f, 30f)
        val dim = SkyModel.weatherDim(0.9f, 0.8f)
        assertTrue("云雨应当压暗", dim > 0.4f)
        val overcast = SkyModel.applyWeather(clear, earth, 0.9f, 0.8f, 30f)
        fun lum(c: com.skysim.app.core.Rgb) = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b
        assertTrue("阴雨天亮度必须低于晴天（${lum(clear)} -> ${lum(overcast)}）", lum(overcast) < lum(clear) * 0.75f)
        val spreadClear = clear.b - clear.r
        val spreadOver = overcast.b - overcast.r
        assertTrue("阴雨天要更“灰”（色差应变小）", spreadOver < spreadClear)
        assertEquals("无云无雨时不做任何修改", clear, SkyModel.applyWeather(clear, earth, 0f, 0f, 30f))
        val mercury = Planets.byId("mercury")
        val airless = SkyModel.display(mercury, 45f, 30f)
        assertEquals("无大气天体不应被天气系统压暗", airless, SkyModel.applyWeather(airless, mercury, 1f, 1f, 30f))
    }

    @Test
    fun defaultsAndToggles() {
        val d = EffectState.defaults()
        assertEquals(EffectId.values().size, d.size)
        assertTrue("默认开启云层", d[EffectId.CLOUDS]!!.enabled)
        assertFalse("默认不开启极光", d[EffectId.AURORA]!!.enabled)
        val s = EffectState()
        assertEquals(1, s.enabledCount())
        val s2 = s.with(EffectId.AURORA, FxSetting(enabled = true, strength = 0.9f))
        assertEquals(2, s2.enabledCount())
        assertEquals(0.9f, s2.setting(EffectId.AURORA).strength, 0.0001f)
        assertNotNull(s2.setting(EffectId.CLOUDS))
        // 强度越界自动夹紧
        assertEquals(1f, FxSetting(strength = 3f).strengthClamped(), 0.0001f)
    }

    /** 球面上两点的角距离（度）。 */
    private fun angularDistance(az1: Double, alt1: Double, az2: Double, alt2: Double): Double {
        val a1 = alt1 * PI / 180.0; val a2 = alt2 * PI / 180.0
        val dz1 = az1 * PI / 180.0; val dz2 = az2 * PI / 180.0
        val v = sin(a1) * sin(a2) + cos(a1) * cos(a2) * cos(dz1 - dz2)
        return acos(v.coerceIn(-1.0, 1.0)) * 180.0 / PI
    }
}
