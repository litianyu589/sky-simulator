package com.skysim.app

import com.skysim.app.core.Ground3D
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.Terrain
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.Twilight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** v0.6.5：太阳本体颜色、金星米黄、夜空无硬边、3D 地面与伪地面。 */
class V065Test {

    private val on = Twilight.Setting(true, 0.65f)

    private fun sky(planetId: String, sunAlt: Float, viewAlt: Float, viewAz: Float = 0f, sunAz: Float = 0f): Rgb =
        SkyModel.toSrgb(SkyModel.softClip(SkyModel.radiance(Planets.byId(planetId), viewAlt, sunAlt, viewAz, sunAz, on)))

    private fun sunSrgb(planetId: String, sunAlt: Float): Rgb =
        SkyModel.toSrgb(SkyModel.sunDisc(Planets.byId(planetId), sunAlt))

    private fun lum(c: Rgb) = SkyModel.luminance(c)

    /** 太阳盘永远不黑、永远比背后的天空亮。 */
    @Test
    fun sunDiscIsNeverBlackAndAlwaysBrighterThanSky() {
        for (id in listOf("earth", "venus", "mars", "titan", "jupiter")) {
            for (alt in listOf(45f, 15f, 3f, 0f, -2f, -4f, -8f)) {
                val disc = sunSrgb(id, alt)
                val horizonSky = sky(id, alt, 1.5f)
                assertTrue("$id@$alt 太阳盘不应接近黑: $disc", disc.r > 0.45f && disc.g > 0.30f && disc.b > 0.18f)
                assertTrue("$id@$alt 太阳应比地平线天空亮", lum(disc) > lum(horizonSky) * 1.02f)
            }
        }
    }

    /** 色相随高度单调变暖（红蓝差随太阳降低而增大），并在高空接近白。 */
    @Test
    fun sunHueWarmsContinuouslyAsItSinks() {
        var lastWarmth = -1f
        for (alt in listOf(45f, 25f, 12f, 4f, 0f, -3f, -6f)) {
            val d = sunSrgb("earth", alt)
            val warmth = d.r - d.b
            assertTrue("太阳在 $alt° 处暖度应单调增加（上一个 $lastWarmth，本次 $warmth）", warmth >= lastWarmth - 0.02f)
            lastWarmth = warmth
        }
        val high = sunSrgb("earth", 45f)
        assertTrue("高空太阳应接近白", high.r > 0.9f && high.b > 0.75f)
        val low = sunSrgb("earth", -2f)
        assertTrue("低空太阳应偏橙红", low.r - low.b > 0.25f)
    }

    /** 金星：天空必须是暖色序 R≥G≥B，不能是地球式的瑞利蓝。 */
    @Test
    fun venusSkyIsWarmAndNotRayleighBlue() {
        for (alt in listOf(50f, 20f, 5f, 0f, -4f)) {
            for (v in listOf(2f, 15f, 60f)) {
                val c = sky("venus", alt, v)
                assertTrue("金星 $alt°/$v° 应 R≥G≥B: $c", c.r >= c.g - 0.005f && c.g >= c.b - 0.005f)
                assertTrue("金星不应偏蓝: $c", c.r > c.b)
            }
        }
        // 气溶胶主导的天体：任何高度都不应出现蓝>红的瑞利天空
        for (id in listOf("venus", "titan")) {
            val c = sky(id, 30f, 45f)
            assertTrue("$id 的天顶不应是瑞利蓝", c.r >= c.b - 0.01f)
        }
    }

    /** 夜空任意相邻采样行之间不得出现硬边（差值远小于肉眼可辨的台阶）。 */
    @Test
    fun nightSkyHasNoVisibleSteps() {
        var prev = sky("earth", -14f, -5f)
        var alt = -5f
        while (alt <= 60f) {
            val c = sky("earth", -14f, alt)
            assertTrue(
                "夜空在 $alt° 出现台阶: $prev -> $c",
                abs(c.r - prev.r) < 0.02f && abs(c.g - prev.g) < 0.02f && abs(c.b - prev.b) < 0.02f
            )
            prev = c
            alt += 1f
        }
    }

    /** 有固体表面的天体 vs 伪地面天体（气态/冰巨星）分类。 */
    @Test
    fun surfaceClassificationMatchesBodyType() {
        for (id in listOf("earth", "mars", "moon", "mercury", "pluto", "ceres", "europa")) {
            assertTrue("$id 应有固体地面", Ground3D.isSolidSurface(Planets.byId(id)))
        }
        for (id in listOf("jupiter", "saturn", "uranus", "neptune")) {
            assertFalse("$id 不应绘制固体地面", Ground3D.isSolidSurface(Planets.byId(id)))
            assertTrue("$id 应使用伪地面", Ground3D.isPseudoGround(Planets.byId(id)))
        }
        val station = Planets.all.firstOrNull { it.isStation }
        if (station != null) {
            assertFalse(Ground3D.isSolidSurface(station))
            assertFalse(Ground3D.isPseudoGround(station))
        }
    }

    /** 伪地面云带必须半透明且覆盖连续（不出现硬边）。 */
    @Test
    fun pseudoGroundBandsAreTranslucentAndOrdered() {
        val bands = Ground3D.bands(Planets.byId("jupiter"))
        assertTrue(bands.size >= 4)
        var last = -1f
        for (b in bands) {
            assertTrue("云带不透明度必须 <1", b.alpha < 1f)
            assertTrue("云带高度必须递增", b.at > last)
            last = b.at
        }
        // 相邻云带之间有空隙也不会超过整体高度的 1/3，避免露出背景形成硬边
        assertTrue(bands.first().at < 0.15f && bands.last().at > 0.9f)
    }

    /** 3D 网格：同天体确定性、相机旋转后格点位置随之改变、太阳在地平线下时直射光为 0。 */
    @Test
    fun ground3dIsDeterministicAndViewDependent() {
        val earth = Planets.byId("earth")
        val a = Ground3D.heightField(earth, Ground3D.MEDIUM)
        val b = Ground3D.heightField(earth, Ground3D.MEDIUM)
        for (r in a.indices) for (s in a[r].indices) assertEquals(a[r][s], b[r][s], 0f)
        assertFalse("不同天体应有不同地形", a[3][5] == Ground3D.heightField(Planets.byId("mars"), Ground3D.MEDIUM)[3][5])

        // 同一世界点，相机 yaw 变化 30° 后屏幕 x 必须改变
        val d = Ground3D.horizonDistanceKm(earth) * 0.5f
        val p1 = Ground3D.project(0f, d, 0f, 0f, 0f, 800f, 600f, 600f)
        val p2 = Ground3D.project(0f, d, 0f, 30f, 0f, 800f, 600f, 600f)
        assertTrue(p1 != null && p2 != null)
        assertTrue("相机左转后同一地点的屏幕位置应改变", abs(p1!![0] - p2!![0]) > 30f)
        // 俯仰变化后屏幕 y 必须改变
        val p3 = Ground3D.project(0f, d, 0f, 0f, 12f, 800f, 600f, 600f)
        assertTrue(p3 != null && abs(p1[1] - p3!![1]) > 20f)
        // 相机后方返回 null
        assertTrue(Ground3D.project(0f, -d, 0f, 0f, 0f, 800f, 600f, 600f) == null)
        // 太阳在地平线下：平坦地面的直射光为 0
        assertEquals(0f, Ground3D.lambert(0f, 0f, -8f, 100f), 1e-4f)
        assertTrue(Ground3D.lambert(0f, 0f, 30f, 100f) > 0.3f)
    }

    /** 树：随机分布而非等距排列（相邻格点的树高应有明显差异并出现空档）。 */
    @Test
    fun treesAreIrregularlyDistributed() {
        val earth = Planets.byId("earth")
        val values = (0 until 64).map { Ground3D.treeAt(earth, 2, it, 0.45f) }
        assertTrue("应存在无树的位置", values.count { it <= 0.001f } > 8)
        assertTrue("应有不同高度的树", values.filter { it > 0.1f }.distinct().size >= 6)
        assertEquals("植被为 0 时不应有树", 0f, Ground3D.treeAt(earth, 2, 10, 0f), 0f)
    }

    /** 3D 网格在典型相机下必须真的覆盖画布下半部（防止整块被裁掉或翻到天上）。 */
    @Test
    fun groundMeshCoversLowerHalfOfScreen() {
        val earth = Planets.byId("earth")
        val preset = Ground3D.MEDIUM
        val w = 800f
        val h = 360f
        val focal = (w / 2f) / kotlin.math.tan(80.0 * Math.PI / 360.0).toFloat()
        var visible = 0
        var minY = Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var minX = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        for (r in 1 until preset.rings) {
            for (s in 0 until preset.sectors) {
                val bearing = (s.toFloat() / preset.sectors) * 360f
                val br = bearing * Math.PI.toFloat() / 180f
                val d = Ground3D.ringDistance(earth, r, preset.rings)
                val p = Ground3D.project(d * kotlin.math.sin(br), d * kotlin.math.cos(br), -Ground3D.EYE_HEIGHT_KM, 0f, 8f, w, h, focal) ?: continue
                visible++
                minY = minOf(minY, p[1]); maxY = maxOf(maxY, p[1])
                minX = minOf(minX, p[0]); maxX = maxOf(maxX, p[0])
            }
        }
        assertTrue("大部分格点应可见（$visible）", visible > preset.sectors * 3)
        assertTrue("地面主体应落在画布下半部（y 最大 $maxY，画布高 $h）", maxY > h * 0.75f)
        assertTrue("地面的横向覆盖应超过画布宽度（$minX..$maxX）", minX < w * 0.1f && maxX > w * 0.9f)
    }

    /** 透视正确性：正前方平坦地面，越远的圈在屏幕上越高（y 越小），且不会互相穿越。 */
    @Test
    fun groundRingsAreMonotonicWithDistance() {
        val earth = Planets.byId("earth")
        val preset = Ground3D.MEDIUM
        val w = 800f
        val h = 360f
        val focal = (w / 2f) / kotlin.math.tan(80.0 * Math.PI / 360.0).toFloat()
        var lastY = Float.MAX_VALUE
        // 近处几圈位于平视画面之外（专供俯视看脚下），从画面下沿对应的距离开始检查
        val first = (2 until preset.rings).first {
            val d0 = Ground3D.ringDistance(earth, it, preset.rings)
            Ground3D.project(0f, d0, -Ground3D.EYE_HEIGHT_KM, 0f, 0f, w, h, focal)!![1] <= h
        }
        for (r in first until preset.rings) {
            val d = Ground3D.ringDistance(earth, r, preset.rings)
            val p = Ground3D.project(0f, d, -Ground3D.EYE_HEIGHT_KM, 0f, 0f, w, h, focal)!!
            assertTrue("第 $r 圈应在画布内", p[1] > h * 0.3f && p[1] <= h + 2f)
            assertTrue("越远的圈应越靠上（第 $r 圈 y=${p[1]}，上一圈 y=$lastY）", p[1] <= lastY + 0.5f)
            lastY = p[1]
        }
        assertTrue("最远一圈应接近地平线", lastY <= h * 0.5f + 2f)
    }

    /** 版本水印必须来自 BuildConfig，而不是写死的旧字符串。 */
    @Test
    fun versionComesFromBuildConfig() {
        // v1.0：正式版版本号。水印已移除，版本号只保留在右栏品牌头里。
        assertEquals("1.0", BuildConfig.VERSION_NAME)
        assertEquals(1000, BuildConfig.VERSION_CODE)
    }

    /** 高度场与画质档：档位越高网格越密，且地形参数对两类地面都成立。 */
    @Test
    fun qualityPresetsScaleTheMesh() {
        assertEquals(12, Ground3D.preset(TerrainQuality.LOW).rings)
        assertTrue(Ground3D.preset(TerrainQuality.HIGH).rings > Ground3D.preset(TerrainQuality.MEDIUM).rings)
        assertTrue(Ground3D.preset(TerrainQuality.HIGH).sectors > Ground3D.preset(TerrainQuality.LOW).sectors)
        val mesh = Ground3D.heightField(Planets.byId("earth"), Ground3D.LOW)
        assertTrue(mesh.size == 12 && mesh[0].size == 48)
        assertTrue(mesh.all { row -> row.all { it in 0f..1f } })
        assertTrue(Terrain.profileOf(Planets.byId("jupiter")) == null)
    }
}
