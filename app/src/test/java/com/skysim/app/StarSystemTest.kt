package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.DataConfidence
import com.skysim.app.core.ExoPlanets2
import com.skysim.app.core.FxRules
import com.skysim.app.core.Planet
import com.skysim.app.core.PlanetGroup
import com.skysim.app.core.Planets
import com.skysim.app.core.SkyModel
import com.skysim.app.core.StarCatalog
import com.skysim.app.core.StarData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.sqrt

class StarSystemTest {

    private val stationOnly = listOf("sirius", "vega", "regulus", "procyon", "alphacena", "arcturus", "wolf359")

    @Test
    fun starCatalogIsComplete() {
        assertEquals("v0.8.3：32 颗恒星（v0.8.2 的 30 + Kepler-22、Kepler-452）", 32, StarCatalog.stars.size)
        assertEquals("恒星 id 唯一", 32, StarCatalog.stars.map { it.id }.toSet().size)
        assertEquals("太阳在列表里", "sun", StarCatalog.stars[0].id)
        for (s in StarCatalog.stars) {
            assertTrue("${s.nameZh} 温度", s.tempK in 2000.0..40000.0)
            assertTrue("${s.nameZh} 半径", s.radiusSolar > 0.0)
            assertTrue("${s.nameZh} 光度", s.luminositySolar > 0.0)
            assertTrue("${s.nameZh} 距离", s.distanceLy >= 0.0)
        }
        // 光谱型覆盖 M/K/G/F/A/B
        val types = StarCatalog.stars.map { it.spectralCode }.toSet()
        for (t in listOf("M", "K", "G", "F", "A", "B")) {
            assertTrue("缺少 $t 型恒星", t in types)
        }
    }

    @Test
    fun everyPlanetBelongsToAKnownStar() {
        val ids = StarCatalog.stars.map { it.id }.toSet()
        for (p in Planets.all) {
            assertTrue("${p.nameZh} 的宿主 ${p.hostStarId} 不存在", p.hostStarId in ids)
        }
        // 所有恒星下的天体拼起来必须正好是整个天体库，不重不漏
        val collected = StarCatalog.stars.flatMap { StarCatalog.bodies(it.id) }.map { it.id }
        assertEquals("恒星分组不重不漏", Planets.all.size, collected.size)
        assertEquals("没有重复归属", Planets.all.size, collected.toSet().size)
        assertEquals("根目录天体总数（v0.8.3：68 + 8）", 76, StarCatalog.stars.sumOf { StarCatalog.bodies(it.id).size })
    }

    @Test
    fun solarSystemStaysUnderTheSun() {
        val sunBodies = StarCatalog.bodies("sun")
        assertEquals(26, sunBodies.size)
        assertEquals(8, sunBodies.count { it.group == PlanetGroup.PLANET })
        assertEquals(12, sunBodies.count { it.group == PlanetGroup.MOON })
        assertEquals(6, sunBodies.count { it.group == PlanetGroup.DWARF })
        assertTrue("地球在太阳系", sunBodies.any { it.id == "earth" })
        assertTrue("海卫一在太阳系", sunBodies.any { it.id == "triton" })
        assertNull("太阳系不需要空间站", StarCatalog.stationFor(StarCatalog.byId("sun")))
    }

    @Test
    fun planetsAreUnderTheirOwnStars() {
        val trappist = StarCatalog.bodies("trappist1")
        assertEquals(7, trappist.size)
        assertTrue(trappist.all { it.confidence in DataConfidence.values() })
        assertEquals(4, StarCatalog.bodies("hr8799").size)
        assertEquals(4, StarCatalog.bodies("55cnc").size)
        assertEquals(3, StarCatalog.bodies("toi700").size)
        assertEquals(3, StarCatalog.bodies("k23").size)
        assertEquals(3, StarCatalog.bodies("kepler11").size)
        assertEquals(3, StarCatalog.bodies("kepler90").size)
        // 有行星的恒星不生成空间站
        assertNull(StarCatalog.stationFor(StarCatalog.byId("trappist1")))
        assertNull(StarCatalog.stationFor(StarCatalog.byId("hr8799")))
        // 比邻星 b 在比邻星下，而不是「系外行星」平铺
        assertTrue(StarCatalog.bodies("proxima").any { it.id == "proximab" })
        assertEquals(0, StarCatalog.bodies("sun").count { it.id == "proximab" })
    }

    @Test
    fun starOnlySystemsGetASensibleStation() {
        assertEquals(7, stationOnly.size)
        for (id in stationOnly) {
            val star = StarCatalog.byId(id)
            assertTrue("$id 不应有行星", StarCatalog.bodies(id).isEmpty())
            val st = StarCatalog.stationFor(star)
            assertNotNull("$id 应该有空间站", st)
            st!!
            assertTrue(st.isStation)
            assertEquals("station:$id", st.id)
            assertEquals(id, st.hostStarId)
            // 位置：等效应日照距离 a = √(L/L☉)，不是贴在恒星表面
            val a = sqrt(star.luminositySolar)
            assertEquals("$id 空间站距离", a, st.sunDistanceAu, 0.01 * a + 1e-6)
            assertTrue("$id 空间站应在恒星之外", st.sunDistanceAu > star.radiusSolar * 696000.0 / 1.495979e8 * 2)
            // 无大气：气压、光学厚度都是 0，天空纯黑，没有任何天气效果
            assertEquals(0.0, st.pressureAtm, 1e-12)
            assertEquals(0.0, st.rayTau, 1e-12)
            assertEquals(0.0, st.aerosolTau, 1e-12)
            assertFalse(FxRules.cloudCapability(st) > 0f)
            assertFalse(FxRules.auroraVisible(st, 90f, -30f))
            val sky = SkyModel.display(st, 40f, 24f)
            assertTrue("$id 空间站天空应为纯黑", sky.r < 0.01f && sky.g < 0.01f && sky.b < 0.01f)
            // 恒星本体可见：视直径在合理范围内（不能是一个点，也不能占满天空）
            assertTrue("$id 恒星视直径 ${st.sunAngularDiameterDeg}", st.sunAngularDiameterDeg in 0.05f..30f)
            // 本地历法可用
            assertTrue(st.localDaysPerYear >= 1)
            assertTrue(st.localSecondLengthSeconds > 0)
        }
    }

    @Test
    fun stationViewIsStableAndUsesStarColour() {
        val star = StarCatalog.byId("wolf359")
        val st = StarCatalog.stationFor(star)!!
        val geo = Astro.stationGeo()
        assertEquals("空间站没有昼夜变化", geo.sunAltDeg, Astro.stationGeo(starAltDeg = 24f).sunAltDeg, 0.001f)
        assertTrue("恒星在地平线以上", geo.sunAltDeg > 0f)
        // 母星偏色按温度推导：M6V 明显偏红
        assertTrue(st.starColor.r > st.starColor.b)
        // 换一颗 A 型星，偏色应更蓝
        val sirius = StarCatalog.stationFor(StarCatalog.byId("sirius"))!!
        assertTrue("A 型星应比 M 型星更蓝", sirius.starColor.b > st.starColor.b)
        assertTrue("A 型星的蓝色分量应接近满值", sirius.starColor.b > 0.95f)
    }

    @Test
    fun newExoplanetsAreLegalAndLabelled() {
        assertEquals(25, ExoPlanets2.all.size)
        for (p in ExoPlanets2.all) {
            assertTrue("${p.nameZh} 半径", p.radiusEarths > 0.0)
            assertTrue("${p.nameZh} 质量", p.massEarths > 0.0)
            assertTrue("${p.nameZh} 周期", p.orbitalPeriodDays > 0.0)
            assertTrue("${p.nameZh} 半长轴", p.sunDistanceAu > 0.0)
            assertTrue("${p.nameZh} 平衡温度", p.eqTempK > 0.0)
            assertTrue("${p.nameZh} 重力", p.gravity >= 0.0)
            assertTrue("${p.nameZh} 置信度", p.confidence in DataConfidence.values())
            assertTrue("${p.nameZh} 天空依据", p.skyBasisZh.isNotEmpty())
            assertTrue("${p.nameZh} 星色可用", p.starColor.r > 0f && p.starColor.b > 0f)
            if (p.confidence == DataConfidence.UNKNOWN) {
                assertEquals("${p.nameZh} 未知大气却给了气压", 0.0, p.pressureAtm, 1e-12)
                assertEquals("${p.nameZh} 未知大气却给了光学厚度", 0.0, p.rayTau, 1e-12)
                assertFalse("${p.nameZh} 未知大气却有云", FxRules.cloudCapability(p) > 0f)
            }
            // v0.6.10：锁定天体的太阳日数学上发散，不再是「等于公转周期」
            if (p.tidallyLocked && !p.isSatellite) {
                assertTrue("${p.nameZh} 潮汐锁定太阳日必须发散", p.solarDaySignedSeconds.isInfinite())
                assertEquals("${p.nameZh} 锁定比值应为 1", 1.0, p.spinOrbitRatioEffective, 0.02)
            }
        }
        // 新鲜度检查：新增行星里必须同时有观测、模型、未知三档
        val c = ExoPlanets2.all.groupingBy { it.confidence }.eachCount()
        assertTrue("缺少观测档", (c[DataConfidence.OBSERVED] ?: 0) >= 3)
        assertTrue("缺少模型档", (c[DataConfidence.MODELED] ?: 0) >= 3)
        assertTrue("缺少未知档", (c[DataConfidence.UNKNOWN] ?: 0) >= 1)
    }

    @Test
    fun planetKindsCoverTheRequestedDiversity() {
        val kinds = ExoPlanets2.all.joinToString(" ") { it.kindZh }
        for (need in listOf("巨行星", "超级地球", "迷你海王星", "岩质", "热木星")) {
            assertTrue("缺少 $need 类型", kinds.contains(need))
        }
    }

    @Test
    fun stationIsNotListedAsAPlanet() {
        // 空间站是观察点，不进入天体库，避免污染 40 颗系外行星与 26 个太阳系天体
        assertFalse(Planets.all.any { it.isStation })
        for (p in Planets.all) {
            assertFalse("${p.nameZh} 不该被标成空间站", p.isStation)
            assertEquals("真实天体疯不该挂在站上", false, p.id.startsWith("station:"))
        }
        // 而 StarCatalog 能为无行星恒星临时生成空间站，且每次生成一致
        val a = StarCatalog.stationFor(StarCatalog.byId("vega"))!!
        val b = StarCatalog.stationFor(StarCatalog.byId("vega"))!!
        assertEquals(a.id, b.id)
        assertEquals(a.sunDistanceAu, b.sunDistanceAu, 1e-12)
        assertEquals(a.solarDaySeconds, b.solarDaySeconds, 1e-6)
    }
    @Test
    fun circumbinarySystemExposesTwoStellarSources() {
        val planet = Planets.byId("kepler16b")
        val sources = StarCatalog.visualSources(planet.hostStarId, planet.sunDistanceAu)
        assertEquals(2, sources.size)
        assertEquals("Kepler-16 A", sources[0].nameZh)
        assertEquals("Kepler-16 B", sources[1].nameZh)
        assertTrue(sources[0].angularDiameterDeg > sources[1].angularDiameterDeg)
        assertTrue(sources.all { it.irradianceSolar > 0.0 })
        assertTrue(planet.sunIrradiance > sources[0].irradianceSolar.toFloat())
        assertTrue(planet.skyIlluminationColor.r > 0f)
        assertTrue(StarCatalog.systems.any { it.star.id == "kepler16" && it.bodies.any { p -> p.id == "kepler16b" } })
    }

}
