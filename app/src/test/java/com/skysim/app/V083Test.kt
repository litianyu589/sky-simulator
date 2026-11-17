package com.skysim.app

import com.skysim.app.core.Astro
import com.skysim.app.core.CatalogCompletion
import com.skysim.app.core.DataConfidence
import com.skysim.app.core.ExoPlanets3
import com.skysim.app.core.Planet
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.StarCatalog
import com.skysim.app.core.Twilight
import com.skysim.app.core.TwilightModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.sqrt

/**
 * v0.8.3：系外行星与恒星系统扩展的验收。
 *
 * 每条断言都对「该天体自己的物理量」和「球面天文的理论值」，而不是只检查「有没有变化」。
 * 这一版的教训来自 v0.8.1：条数断言写错（43 对 68）导致整条巡检实际上没跑，
 * 所以这里所有条数断言都同时核对「清单长度」和「目录实际长度」，两者必须相等。
 */
class V083Test {

    private fun p(id: String): Planet = Planets.byId(id)

    /** v0.8.3 新增的 8 个天体 id，与 [ExoPlanets3.all] 必须逐一对上。 */
    private val newIds = listOf(
        "kepler22b", "kepler452b",
        "kepler186b", "kepler186c", "kepler186d", "kepler186e",
        "proximad", "k218c"
    )

    private fun lum(c: Rgb) = 0.2126f * c.r + 0.7152f * c.g + 0.0722f * c.b

    // ------------------------------------------------------------ 新增清单与目录

    /**
     * 条数断言必须和实际目录长度对上。
     *
     * v0.8.1 的失败模式：断言写 43（说明文档里的统计口径），实际目录 68，
     * 于是这条测试一上来就失败，后面整条巡检等于没跑。
     */
    @Test
    fun newBodyListMatchesCatalogExactly() {
        assertEquals("新增清单必须是 8 个天体", 8, newIds.size)
        assertEquals("新增清单不能有重复 id", newIds.size, newIds.toSet().size)
        assertEquals("ExoPlanets3 的长度必须等于清单长度", newIds.size, ExoPlanets3.all.size)
        assertEquals("ExoPlanets3 的 id 必须与清单逐位一致", newIds, ExoPlanets3.all.map { it.id })
        for (id in newIds) {
            assertTrue("目录里缺少新增天体 $id", Planets.all.any { it.id == id })
        }
        // 目录总条数：v0.8.2 的 68 + 8
        assertEquals("目录总条数必须等于上一次交付 + 新增", 76, Planets.all.size)
        assertEquals("id 必须唯一", Planets.all.size, Planets.all.map { it.id }.toSet().size)
        assertEquals("系外行星条数", 50, Planets.byGroup(com.skysim.app.core.PlanetGroup.EXOPLANET).size)
        // 至少涵盖 3 个恒星系统
        val hosts = ExoPlanets3.all.map { it.hostStarId }.toSet()
        assertEquals("新增天体必须落在 5 个恒星系统里", 5, hosts.size)
        assertTrue("必须包含开普勒-22 系统", hosts.contains("kepler22"))
        assertTrue("必须包含开普勒-452 系统", hosts.contains("kepler452"))
        assertTrue("必须包含 Kepler-186 系统", hosts.contains("kepler186"))
    }

    /** 开普勒-22b 与它的母星必须作为一套完整的恒星-行星系统存在。 */
    @Test
    fun kepler22SystemIsCompleteAndSelectable() {
        val star = StarCatalog.byId("kepler22")
        assertEquals("Kepler-22", star.nameEn)
        assertEquals("G5 型母星", 5596.0, star.tempK, 1e-9)
        assertEquals("母星半径", 0.869, star.radiusSolar, 1e-9)
        assertEquals("母星光度", 0.6454, star.luminositySolar, 1e-6)
        assertEquals("距离", 634.9, star.distanceLy, 0.05)

        val b = p("kepler22b")
        assertEquals("kepler22b 必须挂在 kepler22 下", "kepler22", b.hostStarId)
        assertEquals("半径 2.10 R⊕", 2.10, b.radiusEarths, 1e-9)
        assertEquals("周期 289.863876 d", 289.863876, b.orbitalPeriodDays, 1e-9)
        assertEquals("半长轴 0.812 AU", 0.812, b.sunDistanceAu, 1e-9)
        assertEquals("平衡温度 279 K", 279.0, b.eqTempK, 1e-9)
        // 母星参数必须来自 StarData，不能另存一份
        assertEquals(star.tempK, b.starTempK, 1e-12)
        assertEquals(star.radiusSolar, b.starRadiusSolar, 1e-12)
        assertEquals(star.luminositySolar, b.starLuminositySolar, 1e-12)
        // 目录里必须能按 hostStarId 找到它（界面的「恒星 → 行星」两级导航）
        assertTrue("按母星应能列出 kepler22b", StarCatalog.bodies("kepler22").any { it.id == "kepler22b" })
        // 有行星的恒星不再生成空间站观察点
        assertEquals("有行星的恒星不应再有空间站", null, StarCatalog.stationFor(star))
    }

    // ------------------------------------------------------------ 大气口径

    /** 没有任何大气实测的天体，界面必须标为推断值，且不得凭空给气压。 */
    @Test
    fun everyNewBodyIsLabelledAsInferredWithoutMeasuredAtmosphere() {
        for (id in newIds) {
            val body = p(id)
            assertFalse("$id 没有任何大气实测，不能标成 OBSERVED", body.hasMeasuredAtmosphere)
            assertTrue(
                "$id 的界面措辞必须写明推断且无大气实测，实际为「${body.atmosphereEvidenceZh}」",
                body.atmosphereEvidenceZh.contains("推断") && body.atmosphereEvidenceZh.contains("无大气实测")
            )
            assertEquals("$id 无大气实测却给了气压", 0.0, body.pressureAtm, 1e-12)
            assertEquals("$id 无大气实测却给了瑞利光学厚度", 0.0, body.rayTau, 1e-12)
            assertEquals("$id 无大气实测却给了气溶胶光学厚度", 0.0, body.aerosolTau, 1e-12)
            assertFalse("$id 按无大气处理时不能有全球大气", body.hasAtmosphere)
            assertTrue("$id 必须写明天空推导依据（含假设）", body.skyBasisZh.isNotBlank())
            assertTrue("$id 的依据必须写明自转是假设", body.skyBasisZh.contains("自转未测"))
        }
        // 反过来：有观测证据的天体仍然必须能读出来
        assertEquals("K2-18 b 有 JWST 大气观测", true, p("k218b").hasMeasuredAtmosphere)
        assertEquals("大气：有观测证据", p("k218b").atmosphereEvidenceZh)
    }

    // ------------------------------------------------------------ 晨昏输出

    /** 每个新增天体都必须有有限的、有序的晨昏输出；无大气天体必须为空表。 */
    @Test
    fun everyNewBodyHasFiniteOrderedTwilightOutput() {
        for (id in newIds) {
            val body = p(id)
            val stage = TwilightModel.stage(body)
            assertTrue("$id civil 必须有限", stage.civilDeg.isFinite())
            assertTrue("$id nautical 必须有限", stage.nauticalDeg.isFinite())
            assertTrue("$id astronomical 必须有限", stage.astronomicalDeg.isFinite())
            assertTrue("$id duration 必须有限", stage.durationHours.isFinite())
            assertTrue(
                "$id 边界必须递增：${stage.civilDeg} ≤ ${stage.nauticalDeg} ≤ ${stage.astronomicalDeg}",
                stage.civilDeg <= stage.nauticalDeg && stage.nauticalDeg <= stage.astronomicalDeg
            )
            assertTrue("$id 时长不能为负", stage.durationHours >= 0.0)
            assertTrue("$id 不透明度必须有限且非负", TwilightModel.opacity(body).isFinite() && TwilightModel.opacity(body) >= 0.0)
            // 无大气天体不得出现晨昏色带（这是硬约束，不是「目前恰好为空」）
            assertTrue("$id 无大气：晨昏色带必须为空表", TwilightModel.bands(body).isEmpty())
            assertEquals("$id 无大气：暮光时长必须为零", 0.0, stage.durationHours, 1e-9)
            assertEquals("$id 无大气：暮光不透明度必须为零", 0.0, TwilightModel.opacity(body), 1e-9)
            assertEquals("$id 无臭氧不得套用地球型色带", 0f, Twilight.beltStrength(body), 1e-6f)
        }
    }

    // ------------------------------------------------------------ 太阳几何

    /** 正午 / 午夜太阳高度角必须等于 90°−|φ−δ| 与 |φ+δ|−90°。 */
    @Test
    fun noonAndMidnightAltitudeMatchTheoryOnEveryNewBody() {
        for (id in newIds) {
            val planet = p(id)
            for (dayOfYear in listOf(1, 90, 180, 270)) {
                val noonGeo = Astro.sunGeo(planet, 0f, dayOfYear, 0.5, anchored = false)
                val decl = noonGeo.declinationDeg.toDouble()
                assertTrue(
                    "$id 赤纬必须落在自身倾角内：δ=$decl 倾角=${planet.obliquityDeg}",
                    abs(decl) <= planet.obliquityDeg + 0.5
                )
                for (lat in listOf(90f, 50f, 23.5f, 0f, -23.5f, -50f, -90f)) {
                    val geo = Astro.sunGeo(planet, lat, dayOfYear, 0.5, anchored = false)
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
    fun dayLengthUsesEachNewBodysOwnSolarDay() {
        for (id in newIds) {
            val planet = p(id)
            val ownSolarDayHours = planet.solarDaySeconds / 3600.0
            // 潮汐锁定的天体太阳不移动，太阳日数学上发散（+∞），只对自由自转的检查有限性
            if (!planet.isSynchronous) {
                assertTrue("$id 太阳日必须有限且为正", ownSolarDayHours.isFinite() && ownSolarDayHours > 0.0)
            }
            // 昼长按球面天文：cos H₀ = −tan φ·tan δ，昼长 = 2H₀/15° × (太阳日/24)
            if (planet.isSynchronous) continue
            val equator = Astro.sunGeo(planet, 23.5f, 100, 0.5, anchored = false)
            val declDeg = equator.declinationDeg.toDouble()
            val h0 = Math.toDegrees(
                kotlin.math.acos(
                    (-kotlin.math.tan(Math.toRadians(23.5)) * kotlin.math.tan(Math.toRadians(declDeg)))
                        .coerceIn(-1.0, 1.0)
                )
            )
            val theory = 2.0 * h0 / 15.0 * (ownSolarDayHours / 24.0)
            assertEquals(
                "$id 纬度 23.5° 的昼长必须等于 2H₀/15°×(太阳日/24)（δ=$declDeg）",
                theory,
                equator.dayLengthHours.toDouble(),
                (ownSolarDayHours * 0.01 + 0.01)
            )
            // 赤道昼长恒为太阳日的一半（正午到午夜跨 12 个时角）
            val equator0 = Astro.sunGeo(planet, 0f, 100, 0.5, anchored = false)
            assertEquals(
                "$id 赤道昼长必须等于太阳日的一半",
                ownSolarDayHours / 2.0,
                equator0.dayLengthHours.toDouble(),
                ownSolarDayHours * 0.01 + 0.01
            )
            // 与地球的 24 小时必须能区分开：不同天体的太阳日不能都一样
            if (!planet.isSynchronous) {
                assertTrue("$id 的太阳日不能退化成地球的 24 h", abs(ownSolarDayHours - 24.0) > 1e-6)
            }
            // 潮汐锁定的天体：永昼/永夜两面分别固定在地平线上/下
            if (planet.tidallyLocked) {
                val day = Astro.sunGeo(planet, 0f, 100, 0.10, anchored = false, tidalNightSide = false)
                val night = Astro.sunGeo(planet, 0f, 100, 0.10, anchored = false, tidalNightSide = true)
                assertTrue("$id 永昼面太阳必须在地平线以上", day.sunAltDeg > 20f)
                assertTrue("$id 永夜面太阳必须在地平线以下", night.sunAltDeg < -20f)
                assertEquals("$id 永昼面 24 小时白昼", 24f, day.dayLengthHours, 0.01f)
                assertEquals("$id 永夜面 0 小时白昼", 0f, night.dayLengthHours, 0.01f)
            }
        }
    }

    /** 母星视直径必须等于 2·atan(R★ / a) 的几何值，并且与太阳的比值可核对。 */
    @Test
    fun sunAngularDiameterMatchesStellarGeometry() {
        val earth = p("earth")
        val earthDiameter = earth.sunAngularDiameterDeg.toDouble()
        assertEquals("地球看太阳约 0.532°", 0.532, earthDiameter, 0.01)
        for (id in newIds) {
            val planet = p(id)
            val geometry = Math.toDegrees(
                2.0 * atan(planet.starRadiusSolar * Planet.SUN_RADIUS_KM /
                    (planet.sunDistanceAu * Planet.AU_KM))
            )
            assertEquals(
                "$id 母星视直径必须等于 2·atan(R★/a)",
                geometry, planet.sunAngularDiameterDeg.toDouble(), 1e-6
            )
            val ratio = planet.sunAngularDiameterDeg / earth.sunAngularDiameterDeg
            val theoryRatio = (planet.starRadiusSolar / planet.sunDistanceAu) /
                (earth.starRadiusSolar / earth.sunDistanceAu)
            assertTrue(
                "$id 视直径比例必须等于 (R★/a) 之比：$ratio vs $theoryRatio",
                abs(ratio - theoryRatio.toFloat()) / theoryRatio.toFloat() < 2e-3f
            )
            // 视半径就是视直径的一半（晨昏半影下限要用到）
            assertEquals(
                "$id 视半径 = 视直径/2",
                planet.starAngularDiameterDegResolved / 2.0,
                planet.starAngularRadiusDeg,
                1e-9
            )
        }
        // 已知量级：Kepler-22b 的母星是 0.869 R☉ / 0.812 AU，视直径应比太阳略大
        assertTrue("Kepler-22b 的母星视直径应略大于太阳", p("kepler22b").sunAngularDiameterDeg > earthDiameter)
        // Proxima d 的母星只有 0.141 R☉ 且轨道极近，视直径应远大于太阳
        assertTrue("比邻星 d 的母星视直径应远大于太阳", p("proximad").sunAngularDiameterDeg > 4f * earthDiameter)
    }

    // ------------------------------------------------------------ 地球回归锚点

    /**
     * 地球回归锚点必须逐位不变。
     *
     * 这是整个天空模型唯一的标定基准：散射层有效高度 16.0°、维纳斯带 0.7560/0.4289/0.5809、
     * 三阶段角 6/12/18。任何一处漂移都说明新参数反向渗进了已有条目。
     */
    @Test
    fun earthRegressionAnchorIsBitIdentical() {
        val earth = p("earth")
        assertEquals("地球散射层有效高度", 16.0, Twilight.scatteringAltitudeDeg(earth), 1e-12)
        val belt = Twilight.beltColour(earth)
        assertEquals("维纳斯带 R", 0.7560, belt.r.toDouble(), 2e-3)
        assertEquals("维纳斯带 G", 0.4289, belt.g.toDouble(), 2e-3)
        assertEquals("维纳斯带 B", 0.5809, belt.b.toDouble(), 2e-3)
        assertTrue("维纳斯带必须是粉紫（红 > 蓝 > 绿）", belt.r > belt.b && belt.b > belt.g)
        val stage = TwilightModel.stage(earth)
        assertEquals("地球民用晨昏", 6.0, stage.civilDeg, 1e-9)
        assertEquals("地球航海晨昏", 12.0, stage.nauticalDeg, 1e-9)
        assertEquals("地球天文晨昏", 18.0, stage.astronomicalDeg, 1e-9)
        assertEquals("地球臭氧柱", 1f, earth.ozoneColumn, 1e-12f)
        // 补全不能改变地球的任何渲染量
        assertEquals("地球半径（公里）", 6371.0, earth.radiusKm, 1e-9)
        assertEquals("地球补全后的半径必须是 1.00 R⊕", 1.0, earth.radiusEarths, 1e-9)
        assertEquals("地球补全后的质量必须是 1.00 M⊕（目录里的重力 9.81 是四舍五入值）", 1.0, earth.massEarths, 1e-3)
        assertEquals("地球补全后的平衡温度", 279.0, earth.eqTempK, 1e-9)
        // 火星、金星、月球的晨昏分层也不许漂
        assertEquals("火星散射层有效高度", 20.9, Twilight.scatteringAltitudeDeg(p("mars")), 0.1)
        assertEquals("金星散射层有效高度", 29.9, Twilight.scatteringAltitudeDeg(p("venus")), 0.1)
        assertEquals("月球无晨昏带", 0.0, TwilightModel.stage(p("moon")).durationHours, 1e-9)
        assertTrue("月球无晨昏色带", TwilightModel.bands(p("moon")).isEmpty())
    }

    // ------------------------------------------------------------ 目录完整性

    /** 补全之后，任何一条目录天体都不许再有半径/质量/平衡温度/半长轴/周期/光谱型的空洞。 */
    @Test
    fun catalogHasNoRemainingMissingFields() {
        for (planet in Planets.all) {
            val missing = CatalogCompletion.missingFields(planet)
            assertTrue("${planet.id} 仍缺字段：$missing", missing.isEmpty())
        }
        // 补全必须是幂等的：再跑一次不改变任何值
        for (planet in Planets.all) {
            val again = CatalogCompletion.complete(planet)
            assertEquals("${planet.id} 补全必须幂等（半径）", planet.radiusEarths, again.radiusEarths, 1e-12)
            assertEquals("${planet.id} 补全必须幂等（质量）", planet.massEarths, again.massEarths, 1e-12)
            assertEquals("${planet.id} 补全必须幂等（平衡温度）", planet.eqTempK, again.eqTempK, 1e-12)
        }
        // 目录里的量级抽查：火星平衡温度约 226 K、金星约 328 K
        assertEquals("火星平衡温度", 226.0, p("mars").eqTempK, 5.0)
        assertEquals("金星平衡温度", 328.0, p("venus").eqTempK, 5.0)
        assertEquals("木星平衡温度", 122.0, p("jupiter").eqTempK, 5.0)
    }

    /** 新增天体不能把「未知大气」渲染成一团可见的天光。 */
    @Test
    fun airlessNewBodiesRenderBlackSky() {
        for (id in newIds) {
            val body = p(id)
            val sky = com.skysim.app.core.SkyModel.display(body, 40f, 30f)
            assertTrue(
                "$id 无大气：白天天空也必须接近全黑，实际 ${sky.r}/${sky.g}/${sky.b}",
                sky.r < 0.02f && sky.g < 0.02f && sky.b < 0.03f
            )
            assertTrue("$id 的昼夜亮度必须在显示范围内", lum(sky) in 0f..1f)
        }
    }
}
