package com.skysim.app

import com.skysim.app.core.BodyCatalog
import com.skysim.app.core.PlanetGroup
import com.skysim.app.core.Planets
import com.skysim.app.core.SkyModel
import com.skysim.app.core.StarCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.6 修复的回归测试：只有恒星、没有行星的系统也能进入并选中空间站。
 *
 * v0.5 的 bug 成因：界面用 Planets.byId(planetId) 解析当前天体，而空间站是临时生成的、
 * 不在 Planets.all 里，byId 静默回退到地球，表现为「点空间站没反应 / 切不进去」。
 */
class BodyResolutionTest {

    private val stationStars = listOf("sirius", "vega", "regulus", "procyon", "alphacena", "arcturus", "wolf359")

    @Test
    fun stationIdsAreNotInThePlainPlanetCatalog() {
        // 这正是旧实现失败的机制：空间站不在 Planets.all，byId 会退回地球
        assertTrue(Planets.all.none { it.id.startsWith(BodyCatalog.STATION_PREFIX) })
        assertTrue(BodyCatalog.isStationId("station:sirius"))
        assertFalse(BodyCatalog.isStationId("sirius"))
        // byId 仍然只认真实天体（保留它作为「真实天体库」的语义）
        assertEquals("earth", Planets.byId("station:sirius").id)
        assertNull("解析入口应明确区分：未知 id 不静默替换", BodyCatalog.resolveOrNull("station:not-a-star"))
    }

    @Test
    fun everyStarOnlySystemResolvesToItsStation() {
        assertEquals(7, stationStars.size)
        for (starId in stationStars) {
            assertTrue("$starId 不应有行星", StarCatalog.bodies(starId).isEmpty())
            val p = BodyCatalog.resolve("station:$starId")
            assertTrue("$starId 空间站应被解析成空间站", p.isStation)
            assertEquals("station:$starId", p.id)
            assertEquals("空间站应挂在所属恒星下", starId, p.hostStarId)
            assertEquals("空间站名称要带恒星名", true, p.nameZh.contains(StarCatalog.byId(starId).nameZh))
            // 星色来自恒星温度，不是随便写的
            val star = StarCatalog.byId(starId)
            assertEquals(star.color.r, p.starColor.r, 0.001f)
            assertEquals(star.color.b, p.starColor.b, 0.001f)
            // 无大气 → 天空纯黑
            val sky = SkyModel.display(p, 40f, 24f)
            assertTrue("$starId 空间站天空应是黑的", sky.r < 0.01f && sky.g < 0.01f && sky.b < 0.01f)
        }
    }

    @Test
    fun selectedStationSurvivesTheStateRoundTrip() {
        // 界面里只保存 id 字符串，来回转换后必须仍然是同一个空间站
        for (starId in stationStars) {
            val id = BodyCatalog.resolve("station:$starId").id
            val again = BodyCatalog.resolve(id)
            assertTrue(again.isStation)
            assertEquals(starId, again.hostStarId)
            // 「所属恒星」回跳逻辑依赖 hostStarId
            assertEquals(starId, StarCatalog.starOf(again).id)
            // 从空间站回到恒星目录，再进别的恒星、再回来，仍可解析
            val other = BodyCatalog.resolve("station:vega")
            assertTrue(other.isStation)
            assertEquals(starId, BodyCatalog.resolve(id).hostStarId)
        }
    }

    @Test
    fun stationCacheKeepsIdentityStable() {
        val a = BodyCatalog.stationFor("sirius")
        val b = BodyCatalog.stationFor("sirius")
        assertNotNull(a)
        assertSame("同一颗恒星的空间站应是同一个对象", a, b)
        BodyCatalog.clearStationCache()
        val c = BodyCatalog.stationFor("sirius")
        assertEquals(a!!.id, c!!.id)
        assertEquals(a.sunDistanceAu, c.sunDistanceAu, 1e-12)
        assertEquals(a.solarDaySeconds, c.solarDaySeconds, 1e-6)
    }

    @Test
    fun realBodiesStillResolve() {
        assertEquals("earth", BodyCatalog.resolve("earth").id)
        assertEquals("trappist1b", BodyCatalog.resolve("trappist1b").id)
        assertEquals("trappist1", BodyCatalog.resolve("trappist1b").hostStarId)
        assertEquals("proximab", BodyCatalog.resolve("proximab").id)
        assertEquals("io", BodyCatalog.resolve("io").id)
        assertEquals("ceres", BodyCatalog.resolve("ceres").id)
        assertEquals(PlanetGroup.MOON, BodyCatalog.resolve("io").group)
        // 未知 id 退回地球（唯一兜底），而不是抛异常
        assertEquals("earth", BodyCatalog.resolve("no-such-body").id)
        assertNull(BodyCatalog.resolveOrNull("no-such-body"))
    }

    @Test
    fun leftAndRightLinksAreConsistentForEveryStar() {
        for (star in StarCatalog.stars) {
            val bodies = StarCatalog.bodies(star.id)
            if (bodies.isNotEmpty()) {
                // 有行星：每颗天体都能从 id 解析回来，且归属一致
                for (b in bodies) {
                    val resolved = BodyCatalog.resolve(b.id)
                    assertEquals(star.id, resolved.hostStarId)
                    assertFalse(resolved.isStation)
                    assertEquals(star.id, StarCatalog.starOf(b).id)
                }
                assertNull("有行星的恒星不该有空间站", StarCatalog.stationFor(star))
            } else {
                if (star.id == "sun") continue
                val st = BodyCatalog.resolve("station:${star.id}")
                assertTrue(st.isStation)
                assertEquals(star.id, st.hostStarId)
            }
        }
    }

    @Test
    fun singleBodyAndEmptyBodyEdges() {
        // 只有一颗行星的恒星
        val proximaBodies = StarCatalog.bodies("proxima")
        // v0.8.3：Proxima 系统补上 d 之后是两颗（b、d）
        assertEquals(2, proximaBodies.size)
        assertEquals("proximab", BodyCatalog.resolve(proximaBodies[0].id).id)
        assertNull(StarCatalog.stationFor(StarCatalog.byId("proxima")))
        // 空集合：空间站是唯一可选天体，且不能为 null
        val emptyStars = StarCatalog.stars.filter { StarCatalog.bodies(it.id).isEmpty() && !it.isSun }
        assertEquals(7, emptyStars.size)
        for (s in emptyStars) {
            val station = BodyCatalog.stationFor(s.id)
            assertNotNull("${s.nameZh} 必须至少有一个可选天体", station)
            // 空间站不能混进天体库，否则会污染 66 个真实天体的统计
            assertTrue(Planets.all.none { it.id == station!!.id })
            assertTrue(Planets.byGroup(PlanetGroup.EXOPLANET).none { it.isStation })
        }
    }
}
