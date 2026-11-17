package com.skysim.app

import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.SkyModel
import com.skysim.app.core.StarCatalog
import com.skysim.app.core.StarData
import com.skysim.app.core.Stars
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.tan

/**
 * v0.6.3 母星视大小与星色审计。
 *
 * 角直径口径统一为 θ = 2·atan(R★ / a)，R★ 以太阳半径计、a 以 AU 计；
 * 参考值取 NASA Exoplanet Archive（pscomppars）与 NASA 行星事实表。
 * 这里同时钉住三件事：数据对不对、公式有没有 2 倍误差、渲染钳位会不会把天体画大。
 */
class PhysicsAuditTest {

    /** NASA 行星事实表 / Exoplanet Archive 的参考角直径（度），容差 3%。 */
    private val solarSystemRef = mapOf(
        "mercury" to 1.377, "venus" to 0.737, "earth" to 0.533, "mars" to 0.350,
        "jupiter" to 0.102, "saturn" to 0.056, "uranus" to 0.028, "neptune" to 0.018
    )

    @Test
    fun solarSystemAngularDiametersMatchReference() {
        for ((id, ref) in solarSystemRef) {
            val th = Planets.byId(id).sunAngularDiameterDeg
            assertTrue("$id 视直径 $th 应接近 $ref", abs(th - ref) / ref < 0.03f)
        }
        // 月球/卫星用母星视线距离，应与所属行星一致
        assertEquals(
            Planets.byId("earth").sunAngularDiameterDeg,
            Planets.byId("moon").sunAngularDiameterDeg, 1e-6f
        )
    }

    @Test
    fun exoplanetAngularDiametersMatchComputation() {
        // TRAPPIST-1：0.1192 R☉、a=0.01154 AU → 5.5°
        assertTrue(Planets.byId("trappist1b").sunAngularDiameterDeg in 5.2f..5.8f)
        // KELT-9 b：2.418 R☉、a=0.03368 AU → 约 37°
        assertTrue(Planets.byId("kelt9b").sunAngularDiameterDeg in 35f..39f)
        // WASP-12 b：1.69 R☉、a=0.0234 AU → 约 37°
        assertTrue(Planets.byId("wasp12b").sunAngularDiameterDeg in 35f..39f)
        // 51 Peg b：1.19 R☉、0.052 AU → 约 12.2°
        assertTrue(Planets.byId("51pegb").sunAngularDiameterDeg in 11.5f..13f)
        // HD 209458 b：1.19 R☉、0.0471 AU → 约 13.4°
        assertTrue(Planets.byId("hd209458b").sunAngularDiameterDeg in 12.7f..14.2f)
        // Kepler-16 b：主星 0.6489 R☉、0.7048 AU → 0.49°；伴星 0.2262 R☉ → 0.171°
        assertTrue(Planets.byId("kepler16b").sunAngularDiameterDeg in 0.47f..0.51f)
        val sources = StarCatalog.visualSources("kepler16", Planets.byId("kepler16b").sunDistanceAu)
        assertTrue(sources[1].angularDiameterDeg in 0.16..0.185)
    }

    /** 数据本身与权威值的一致性：每条都写死参考值，超过 1% 就报错。 */
    @Test
    fun catalogDataMatchesAuthoritativeValues() {
        // id -> (半长轴 AU, 母星半径 R☉)，取自 NASA Exoplanet Archive pscomppars
        val ref = mapOf(
            "trappist1b" to (0.01154 to 0.1192), "trappist1c" to (0.0158 to 0.1192),
            "trappist1d" to (0.02227 to 0.1192), "trappist1e" to (0.02925 to 0.1192),
            "trappist1f" to (0.03849 to 0.1192), "trappist1g" to (0.04683 to 0.1192),
            "proximab" to (0.04848 to 0.141), "kepler186f" to (0.432 to 0.523),
            "k218b" to (0.1429 to 0.411), "hd189733b" to (0.03126 to 0.75),
            "wasp12b" to (0.0234 to 1.69), "51pegb" to (0.052 to 1.19),
            "gj1214b" to (0.01505 to 0.2162), "lhs1140b" to (0.0946 to 0.2159),
            "teegardenb" to (0.0259 to 0.12), "kelt9b" to (0.03368 to 2.418),
            "hr8799b" to (68.0 to 1.49338), "hr8799c" to (38.0 to 1.49338),
            "hr8799d" to (24.0 to 1.49338), "hr8799e" to (16.4 to 1.49338),
            "toi700b" to (0.0678 to 0.4104), "toi700c" to (0.0931 to 0.4104),
            "toi700d" to (0.1642 to 0.4104), "kepler90i" to (0.12014 to 1.29),
            "55cncb" to (0.118 to 0.98), "55cncc" to (0.247 to 0.98),
            "55cncf" to (0.802 to 0.98), "55cnce" to (0.01544 to 0.98),
            "k23b" to (0.0747 to 0.546), "k23c" to (0.1357 to 0.546), "k23d" to (0.2014 to 0.546),
            "kepler11b" to (0.091 to 1.065), "kepler11c" to (0.107 to 1.065),
            "kepler11e" to (0.195 to 1.065), "kepler442b" to (0.409 to 0.598),
            "gj1132b" to (0.0157 to 0.2211), "hd209458b" to (0.04707 to 1.19),
            "kepler16b" to (0.7048 to 0.6489), "wasp39b" to (0.04828 to 0.939)
        )
        for ((id, pair) in ref) {
            val p = Planets.byId(id)
            val (a, r) = pair
            assertTrue("$id 半长轴 ${p.sunDistanceAu} 应为 $a", abs(p.sunDistanceAu / a - 1.0) < 0.01)
            assertTrue("$id 母星半径 ${p.starRadiusSolar} 应为 $r", abs(p.starRadiusSolar / r - 1.0) < 0.01)
        }
    }

    @Test
    fun planetStarParamsComeFromStarData() {
        // 行星条目不再各存一份母星参数：任何一处改动都必须同步到天空渲染
        for (p in Planets.all) {
            val host = p.hostStarId
            if (host == "sun" || StarData.all.none { it.id == host }) continue
            val s = StarData.byId(host)
            assertEquals("${p.id} 母星半径", s.radiusSolar, p.starRadiusSolar, 1e-9)
            assertEquals("${p.id} 母星温度", s.tempK, p.starTempK, 1e-9)
            assertEquals("${p.id} 母星光度", s.luminositySolar, p.starLuminositySolar, 1e-9)
            assertEquals("${p.id} 母星名", s.nameZh, p.starNameZh)
        }
    }

    @Test
    fun angularDiameterFormulaHasNoDoubleFactor() {
        // 太阳系实测值反推：θ_earth 必须落在 0.53° 附近，而不是 1.07°（2 倍误差）
        val earth = Planets.byId("earth").sunAngularDiameterDeg
        assertTrue("地球看太阳应约 0.53°，实测 $earth", abs(earth - 0.533f) < 0.01f)
        // 屏幕半径：θ=0.533°、1000 px/rad → tan(0.2665°)×1000 ≈ 4.65 px（半径，不是直径 9.3 px）
        val r = SkyModel.discRadiusPx(0.533f, 1000f)
        assertTrue("半径应约 4.65px，实测 $r", abs(r - 4.65f) < 0.25f)
        // 大角直径用 tan 而非小角近似
        val big = SkyModel.discRadiusPx(37f, 1000f)
        assertTrue("37° 用 tan 换算应约 334px，实测 $big", abs(big - tan(Math.toRadians(18.5)).toFloat() * 1000f) < 2f)
    }

    @Test
    fun subPixelClampNeverInflatesTheSunNoticeably() {
        // 高分辨率（≈1080p 的 1000 px/rad）与低分辨率（≈480p 的 300 px/rad）都要检查
        for (pprad in listOf(1000f, 300f)) {
            for (p in Planets.all) {
                val raw = tan(Math.toRadians(p.sunAngularDiameterDeg / 2.0)).toFloat() * pprad
                val drawn = SkyModel.discRadiusPx(p.sunAngularDiameterDeg, pprad)
                // 没有任何最小像素钳位：画出来的半径必须就是几何半径
                assertEquals("${p.id}@$pprad 不应被放大", raw, drawn, 1e-4f)
            }
        }
        // 远日天体的太阳必须仍是亚像素：海王星 ≈0.15px、冥王星 ≈0.12px
        assertTrue("neptune 太阳应小于 0.2px", SkyModel.discRadiusPx(Planets.byId("neptune").sunAngularDiameterDeg, 1000f) < 0.2f)
        assertTrue("pluto 太阳应小于 0.2px", SkyModel.discRadiusPx(Planets.byId("pluto").sunAngularDiameterDeg, 1000f) < 0.2f)
        // 大角直径天体绝不能被上限压小：KELT-9 b 的 37° 在 1000 px/rad 下应画成 ≈334 px 半径
        val big = SkyModel.discRadiusPx(Planets.byId("kelt9b").sunAngularDiameterDeg, 1000f)
        assertTrue("KELT-9 b 太阳半径应远大于 45px，实测 $big", big > 300f)
    }

    @Test
    fun starColourFollowsTemperature() {
        // 太阳 = 精确的 (1,1,1)
        val sun = Stars.relativeColor(5772.0)
        assertEquals(1f, sun.r, 1e-4f)
        assertEquals(1f, sun.g, 1e-4f)
        assertEquals(1f, sun.b, 1e-4f)
        // M 型红矮星偏红：r > b
        val m = Stars.relativeColor(2566.0)
        assertTrue("M 型应偏红", m.r > m.b + 0.15f)
        // A/B 型偏蓝：b >= r
        val a = Stars.relativeColor(9602.0)
        assertTrue("A 型应偏蓝", a.b >= a.r)
        val b = Stars.relativeColor(12460.0)
        assertTrue("B 型应更偏蓝", b.b >= a.b - 1e-3f)
        // 单调性：温度越高，蓝/红比越大
        var last = 0f
        for (t in listOf(2500.0, 3000.0, 4000.0, 5000.0, 5772.0, 7000.0, 9000.0, 12000.0)) {
            val c = Stars.relativeColor(t)
            val ratio = c.b / c.r
            assertTrue("$t K 的蓝红比应单调不降", ratio >= last - 1e-3f)
            last = ratio
        }
    }

    @Test
    fun spectralTypeBandsAreConsistent() {
        val bands = mapOf(
            "M" to (2400.0..3700.0), "K" to (3700.0..5200.0), "G" to (5200.0..6000.0),
            "F" to (6000.0..7500.0), "A" to (7500.0..10500.0), "B" to (10000.0..30000.0)
        )
        for (s in StarData.all) {
            val code = Stars.spectralTypeOf(s.tempK).name
            val band = bands[code]!!
            assertTrue("${s.nameZh} ${s.tempK} K 落在 $code 型区间外", s.tempK in band)
        }
    }

    @Test
    fun airlessBodiesKeepStarColourWithoutTint() {
        val moon = Planets.byId("moon")
        val disc = SkyModel.sunDisc(moon, 45f)
        val star = moon.starColor
        assertEquals(star.r, disc.r, 1e-6f)
        assertEquals(star.b, disc.b, 1e-6f)
        val sky = SkyModel.display(moon, 45f, 45f)
        assertTrue("无大气天体天空不应发蓝", sky.b < 0.02f)
    }

    @Test
    fun binaryColourIsFluxWeightedNotAveraged() {
        val p = Planets.byId("kepler16b")
        val src = StarCatalog.visualSources("kepler16", p.sunDistanceAu)
        val f1 = src[0].irradianceSolar
        val f2 = src[1].irradianceSolar
        val expectR = ((src[0].color.r * f1 + src[1].color.r * f2) / (f1 + f2)).toFloat()
        val mixed = p.skyIlluminationColor
        assertEquals("应按辐照度加权", expectR, mixed.r, 1e-5f)
        // 伴星只贡献约 4% 通量，混合色应非常接近主星而不是「两星平均」
        // 用蓝通道检验：两星的红通道都被归一化到 1.0，区分不出来
        val naiveMeanB = (src[0].color.b + src[1].color.b) / 2f
        assertTrue("不应是简单平均", abs(mixed.b - src[0].color.b) < abs(mixed.b - naiveMeanB))
        assertTrue("伴星更冷，混合后应比纯主星更红", mixed.b <= src[0].color.b + 1e-6f)
        assertTrue("伴星通量占比应很小", f2 / (f1 + f2) < 0.1)
    }

    @Test
    fun dustForwardScatteringComesFromParameterNotId() {
        val mars = Planets.byId("mars")
        assertTrue("火星应有非零的前向散射因子", mars.aerosolAsymmetry > 0.5f)
        // 同一天体取消该因子后，日落时的蓝色前向散射必须消失 —— 证明它由参数驱动
        val noDust = mars.copy(aerosolAsymmetry = 0f)
        val sep = 15f
        val sunAlt = 2f
        val withDust = SkyModel.radiance(mars, 8f, sunAlt, sep, 0f)
        val without = SkyModel.radiance(noDust, 8f, sunAlt, sep, 0f)
        assertTrue("有尘埃时太阳附近应更蓝", withDust.b > without.b * 1.05f)
        // 其他天体不应被误伤
        assertEquals(0f, Planets.byId("earth").aerosolAsymmetry, 1e-6f)
        assertEquals(0f, Planets.byId("titan").aerosolAsymmetry, 1e-6f)
    }

    @Test
    fun thickHazeDesaturationAndWarmingAreParameterDriven() {
        val venus = Planets.byId("venus")
        val titan = Planets.byId("titan")
        // 金星：厚气溶胶 + 暖色 → 低仰角更暖；把光学厚度调薄后该效应应减弱
        val low = SkyModel.radiance(venus, 2f, 30f)
        val thin = SkyModel.radiance(venus.copy(aerosolTau = 0.05), 2f, 30f)
        assertTrue("厚雾应把天空推向暖色（r 相对 b 升高）", low.r / low.b > thin.r / thin.b)
        // 土卫六同样由参数导出，不再是 id 特判
        val titanLow = SkyModel.radiance(titan, 2f, 20f)
        val titanThin = SkyModel.radiance(titan.copy(aerosolTau = 0.05), 2f, 20f)
        assertTrue("土卫六厚雾也应有暖色地平线", titanLow.r / titanLow.b > titanThin.r / titanThin.b)
    }

    @Test
    fun starAngularDiameterStaysFiniteForEveryBody() {
        for (p in Planets.all) {
            assertTrue("${p.id} 视直径应为有限正值", p.sunAngularDiameterDeg.isFinite() && p.sunAngularDiameterDeg > 0f)
            assertTrue("${p.id} 视直径不应超过 90°", p.sunAngularDiameterDeg < 90f)
        }
        for (s in StarCatalog.stars) {
            if (s.isSun) continue
            val station = StarCatalog.stationFor(s) ?: continue
            // 空间站位于 √L 等效应日照距离：恒星越暗站位越近，视直径可达数度（沃夫 359 约 2.3°）
            assertTrue("${s.nameZh} 空间站视直径应在 0.01°–3°", station.sunAngularDiameterDeg in 0.01f..3f)
        }
    }
}
