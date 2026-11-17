package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.DataConfidence
import com.skysim.app.core.EffectId
import com.skysim.app.core.FxRules
import com.skysim.app.core.Planet
import com.skysim.app.core.PlanetGroup
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.Stars
import com.skysim.app.core.SurfaceKind
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlanetCatalogTest {

    private val newIds = listOf(
        "enceladus", "iapetus", "titania", "miranda", "charon",
        "ceres", "vesta", "eris", "makemake", "haumea",
        "trappist1b", "trappist1c", "trappist1d", "trappist1e", "trappist1f", "trappist1g",
        "proximab", "kepler186f", "k218b", "hd189733b", "wasp12b", "51pegb",
        "gj1214b", "lhs1140b", "teegardenb", "kelt9b"
    )

    @Test
    fun catalogHasAllNewBodies() {
        assertEquals("v0.8.3：总数 76（v0.8.2 的 68 + 新增 8 颗系外行星）", 76, Planets.all.size)
        assertEquals("id 必须唯一", Planets.all.size, Planets.all.map { it.id }.toSet().size)
        for (id in newIds) {
            assertTrue("缺少 $id", Planets.all.any { it.id == id })
        }
        assertEquals(8, Planets.byGroup(PlanetGroup.PLANET).size)
        assertEquals(12, Planets.byGroup(PlanetGroup.MOON).size)
        assertEquals(6, Planets.byGroup(PlanetGroup.DWARF).size)
        assertEquals(50, Planets.byGroup(PlanetGroup.EXOPLANET).size)
    }

    @Test
    fun physicalDataIsLegal() {
        for (p in Planets.all) {
            assertTrue("${p.nameZh} 半径", p.radiusKm > 0)
            assertTrue("${p.nameZh} 自转/日长", p.solarDaySeconds > 0)
            assertTrue("${p.nameZh} 公转", p.orbitalPeriodDays > 0)
            assertTrue("${p.nameZh} 倾角", p.axialTiltDeg in -180.0..180.0)
            assertTrue("${p.nameZh} 气压", p.pressureAtm >= 0)
            assertTrue("${p.nameZh} 光学厚度", p.rayTau >= 0 && p.aerosolTau >= 0)
            assertTrue("${p.nameZh} 距离", p.sunDistanceAu > 0)
            assertTrue("${p.nameZh} 年长上限", p.localDaysPerYear in 1..2_000_000)
            assertEquals("${p.nameZh} 月长合计", p.localDaysPerYear, p.monthLengths.sum())
            assertTrue("${p.nameZh} 本地秒长", p.localSecondLengthSeconds > 0)
            for (c in listOf(p.aerosolTint, p.groundTint, p.nightTint)) {
                assertTrue("${p.nameZh} 颜色越界", c.r in 0f..1f && c.g in 0f..1f && c.b in 0f..1f)
            }
        }
    }

    @Test
    fun airlessBodiesHaveNoWeather() {
        val airless = listOf("moon", "mercury", "iapetus", "titania", "miranda", "charon", "ceres", "vesta", "makemake", "haumea")
        for (id in airless) {
            val p = Planets.byId(id)
            assertEquals("$id 不应有云", 0f, FxRules.cloudCapability(p), 0.0001f)
            assertEquals("$id 不应有降水", 0f, FxRules.rainCapability(p), 0.0001f)
            assertFalse("$id 不应有虹", FxRules.rainbowVisible(p, 20f, 1f))
            assertFalse("$id 不应有晕", FxRules.haloVisible(p, 30f, 0.4f))
            assertFalse("$id 不应有极光", FxRules.auroraVisible(p, 90f, -30f))
            val sky = SkyModel.display(p, 40f, 30f)
            assertTrue("$id 白天天空也应接近全黑", sky.r < 0.02f && sky.g < 0.02f && sky.b < 0.03f)
        }
    }

    @Test
    fun exoplanetConfidenceIsHonest() {
        val exo = Planets.byGroup(PlanetGroup.EXOPLANET)
        assertEquals(50, exo.size)
        for (p in exo) {
            assertTrue("${p.nameZh} 母星温度", p.starTempK in 2000.0..40000.0)
            assertTrue("${p.nameZh} 母星半径", p.starRadiusSolar > 0.0)
            assertTrue("${p.nameZh} 光度", p.starLuminositySolar > 0.0)
            assertTrue("${p.nameZh} 距地球", p.distanceLy > 0.0)
            assertTrue("${p.nameZh} 平衡温度", p.eqTempK > 0.0)
            assertTrue("${p.nameZh} 半径", p.radiusEarths > 0.0)
            assertTrue("${p.nameZh} 天空依据", p.skyBasisZh.isNotEmpty())
            // UNKNOWN 的必须按无大气处理，不能凭空给一个大气
            if (p.confidence == DataConfidence.UNKNOWN) {
                assertEquals("${p.nameZh} 未知大气却给了气压", 0.0, p.pressureAtm, 1e-12)
                assertEquals("${p.nameZh} 未知大气却给了光学厚度", 0.0, p.rayTau, 1e-12)
                assertFalse("${p.nameZh} 未知大气却有云", FxRules.cloudCapability(p) > 0f)
                assertTrue(
                    "${p.nameZh} 未知大气的表面类型只能是岩质或冰质",
                    p.surfaceKind == SurfaceKind.ROCK || p.surfaceKind == SurfaceKind.ICE
                )
            }
        }
        // 至少要有 5 颗是有观测证据的，且必须有未知的（说明保守处理真的被用到）
        assertTrue(exo.count { it.confidence == DataConfidence.OBSERVED } >= 5)
        assertTrue(exo.any { it.confidence == DataConfidence.UNKNOWN })
    }

    @Test
    fun hostStarColourAndAngularSize() {
        val sun = Planets.byId("earth")
        val m = Planets.byId("trappist1b")
        val a = Planets.byId("kelt9b")
        // 太阳本身恒为 (1,1,1)
        assertEquals(1f, sun.starColor.r, 0.001f)
        assertEquals(1f, sun.starColor.g, 0.001f)
        assertEquals(1f, sun.starColor.b, 0.001f)
        // M 型红矮星偏红，A 型偏蓝
        assertTrue("M 型应比太阳红", m.starColor.r > m.starColor.b)
        assertTrue("A 型应比太阳蓝", a.starColor.b >= a.starColor.r)
        // 视直径：近距热木星的母星比太阳大得多
        assertTrue("地球看太阳约 0.53°", abs(sun.sunAngularDiameterDeg - 0.532f) < 0.05f)
        assertTrue("KELT-9b 的母星视直径应远大于太阳", a.sunAngularDiameterDeg > 20f)
        assertTrue("TRAPPIST-1 b 的母星也比太阳大", m.sunAngularDiameterDeg > 2f)
        // 光谱分类
        assertEquals(Stars.SpectralType.M, Stars.spectralTypeOf(2566.0))
        assertEquals(Stars.SpectralType.G, Stars.spectralTypeOf(5772.0))
        assertEquals(Stars.SpectralType.A, Stars.spectralTypeOf(10170.0))
    }

    @Test
    fun unknownAtmosphereGivesBlackSkyButObservedDoesNot() {
        val b = Planets.byId("trappist1b")
        val k2 = Planets.byId("k218b")
        val darkSky = SkyModel.display(b, 45f, 40f)
        assertTrue("未知大气 = 天空全黑", darkSky.r < 0.01f && darkSky.g < 0.01f)
        val brightSky = SkyModel.display(k2, 45f, 40f)
        assertTrue("有观测大气的行星应该有可见天空亮度", brightSky.b > 0.05f)
        assertTrue("K2-18b 的天空应偏蓝", brightSky.b > brightSky.r)
    }

    @Test
    fun tidalLockFreezesSunAndClock() {
        val e = Planets.byId("trappist1e")
        assertTrue(e.tidallyLocked)
        val noon = Astro.sunGeo(e, 0f, 100, 0.10, anchored = false, tidalNightSide = false)
        val later = Astro.sunGeo(e, 0f, 100, 0.75, anchored = false, tidalNightSide = false)
        assertEquals("潮汐锁定：太阳方位不随时间变化", noon.sunAzDeg, later.sunAzDeg, 0.001f)
        assertEquals("潮汐锁定：太阳高度不随时间变化", noon.sunAltDeg, later.sunAltDeg, 0.001f)
        assertTrue("永昼面太阳应在地平线以上", noon.sunAltDeg > 20f)
        assertEquals("永昼面 = 极昼", true, noon.polarDay)
        assertEquals("永昼面白昼 24 小时", 24f, noon.dayLengthHours, 0.01f)

        val night = Astro.sunGeo(e, 0f, 100, 0.10, anchored = false, tidalNightSide = true)
        assertTrue("永夜面太阳应在地平线以下", night.sunAltDeg < -20f)
        assertEquals("永夜面 = 极夜", true, night.polarNight)
        assertEquals(0f, night.dayLengthHours, 0.01f)

        val dayStamp = TimeSystem.localStamp(e, 1_700_000_000L, tidalNightSide = false)
        val nightStamp = TimeSystem.localStamp(e, 1_700_000_000L, tidalNightSide = true)
        assertEquals("永昼面本地时刻冻结在正午", 12, dayStamp.hour)
        assertEquals("永夜面本地时刻冻结在午夜", 0, nightStamp.hour)
        // 日期仍然随时间前进（该星球 1 年 = 1 本地日，所以前进的是年份）
        // 锁定后没有本地日，历法按轨道周期计日（TRAPPIST-1 e 一年 6 天），跨过一年才换年
        val next = TimeSystem.localStamp(e, 1_700_000_000L + 7 * 86_400L, tidalNightSide = false)
        assertNotEquals("本地历法应继续前进", dayStamp.year, next.year)
    }

    @Test
    fun extremeOrbitsDoNotBreakCalendar() {
        // 极短周期（潮汐锁定）：一年 = 1 本地日
        assertEquals(1, Planets.byId("kelt9b").localDaysPerYear)
        // 同步锁定后本地日不再存在，历法按轨道周期计日（1.5109 d → 2）
        assertEquals(2, Planets.byId("trappist1b").localDaysPerYear)
        // 长周期：阋神星约 1.3 万本地日
        assertTrue(Planets.byId("eris").localDaysPerYear in 10_000..15_000)
        // 自转周期未知的系外行星按恒星日 24 小时处理；
        // 太阳日由 1/T_sid − 1/T_orbit 推出（比恒星日长 0.8 %），所以一年是 129 个本地日而不是 130
        assertEquals(129, Planets.byId("kepler186f").localDaysPerYear)
        // 构造一个退化的天体：日长为 0、公转极短，不能抛异常也不能得到非法的年长
        val degenerate = Planet(
            id = "degenerate", nameZh = "测试天体", nameEn = "Test", kindZh = "系外行星 · 测试",
            radiusKm = 1000.0, siderealPeriodOverrideSeconds = 0.0,
            orbitalPeriodDays = 0.0001, axialTiltDeg = 0.0, gravity = 1.0,
            pressureAtm = 0.0, scaleHeightKm = 1.0, compositionZh = "无",
            sunDistanceAu = 0.01, rayTau = 0.0, aerosolTau = 0.0,
            aerosolTint = Rgb(0.5f, 0.5f, 0.5f), groundTint = Rgb(0.5f, 0.5f, 0.5f),
            nightTint = Rgb(0f, 0f, 0f), surfaceKind = SurfaceKind.ROCK
        )
        assertTrue("退化日长必须被夹到合法范围", degenerate.localDaysPerYear >= 1)
        val stamp = TimeSystem.localStamp(degenerate, 1_700_000_000L)
        assertTrue(stamp.month in 1..degenerate.monthsPerYear)
        assertTrue(stamp.hour in 0..23)
    }

    @Test
    fun auroraTableCoversNewBodies() {
        assertTrue("木卫三有自身磁场", FxRules.auroraVisible(Planets.byId("ganymede"), 65f, -20f))
        assertFalse("谷神星没有磁场", FxRules.auroraVisible(Planets.byId("ceres"), 85f, -20f))
        assertFalse("阋神星没有磁场", FxRules.auroraVisible(Planets.byId("eris"), 85f, -20f))
        assertFalse("系外行星没有磁场数据，保守不出极光", FxRules.auroraVisible(Planets.byId("trappist1e"), 89f, -30f))
        // 效果枚举没被破坏
        assertEquals(7, EffectId.values().size)
    }
}
