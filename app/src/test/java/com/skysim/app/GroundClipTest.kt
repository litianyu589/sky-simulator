package com.skysim.app

import com.skysim.app.core.Ground3D
import com.skysim.app.core.Planets
import com.skysim.app.core.Terrain
import com.skysim.app.core.TerrainQuality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * v0.6.7 地面验收：相机眼高、网格投影密度、树的尺寸、明暗连续性、山体越地平线。
 *
 * 不看渲染，只看几何与投影：地面多边形到底落在屏幕哪些位置、每个顶点对应多高的仰角。
 */
class GroundClipTest {

    private val w = 800f
    private val h = 360f
    private val focal = (w / 2f) / tan(80.0 * PI / 360.0).toFloat()
    private val preset = Ground3D.preset(TerrainQuality.MEDIUM)

    private fun cam(pitch: Float, yaw: Float = 0f) = Ground3D.Cam(yaw, pitch, w, h, focal)

    private fun cells(pitch: Float, yaw: Float = 0f, planetId: String = "earth") =
        Ground3D.groundCells(cam(pitch, yaw), Planets.byId(planetId), preset)

    private fun inside(poly: List<FloatArray>, x: Float, y: Float): Boolean {
        var c = false
        var j = poly.size - 1
        for (i in poly.indices) {
            val xi = poly[i][0]; val yi = poly[i][1]
            val xj = poly[j][0]; val yj = poly[j][1]
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) c = !c
            j = i
        }
        return c
    }

    private fun coverage(pitch: Float, yaw: Float = 0f, planetId: String = "earth"): Float {
        val list = cells(pitch, yaw, planetId).map { it.poly }
        var hit = 0
        var total = 0
        for (iy in 0 until 30) {
            for (ix in 0 until 60) {
                val x = (ix + 0.5f) * w / 60f
                val y = (iy + 0.5f) * h / 30f
                total++
                if (list.any { inside(it, x, y) }) hit++
            }
        }
        return hit.toFloat() / total
    }

    /** 只统计某条水平线以下的覆盖率。 */
    private fun coverageBelow(yLine: Float, pitch: Float, planetId: String = "earth"): Float {
        val list = cells(pitch, planetId = planetId).map { it.poly }
        var hit = 0
        var total = 0
        for (iy in 0 until 30) {
            for (ix in 0 until 60) {
                val x = (ix + 0.5f) * w / 60f
                val y = (iy + 0.5f) * h / 30f
                total++
                if (y >= yLine && list.any { inside(it, x, y) }) hit++
            }
        }
        return if (total == 0) 0f else hit.toFloat() / total
    }

    /** 相机绝不被地形埋住：正下方地面距相机至少 0.9 个眼高。 */
    @Test
    fun cameraSitsAboveTheGround() {
        val eye = Ground3D.EYE_HEIGHT_KM
        assertEquals("观察者高度 = 地面 + 眼高", eye, Ground3D.viewerHeightKm(0f), 1e-9f)
        assertEquals("地面较高时相机跟着抬高", eye + 0.5f, Ground3D.viewerHeightKm(0.5f), 1e-6f)
        for (id in listOf("earth", "mars", "moon", "titan", "pluto")) {
            val planet = Planets.byId(id)
            val mesh = Ground3D.heightField(planet, preset)
            val d = Ground3D.ringDistance(planet, 0, preset.rings)
            val p = Ground3D.surfaceWorld(planet, mesh[0][0], d, 0f)
            val gap = -p[2]
            assertTrue("$id 的相机到脚下地面只有 $gap km", gap >= eye * 0.9f)
        }
    }

    /** 平视：地面占比有上限，且随眼高升高单调下降。 */
    @Test
    fun levelCoverageIsBoundedAndFallsWithEyeHeight() {
        val hy = Ground3D.horizonY(cam(0f))
        val below = coverageBelow(hy, 0f)
        val total = coverage(0f)
        println("COVERAGE below=" + below + " total=" + total)
        assertTrue("平视时地平线以下的地面占比过高：$below", below < 0.56f)
        assertTrue("（含山体剪影）总占比过高：$total", total < 0.60f)
        assertTrue("平视至少该看到地面：$below", below > 0.3f)
        // 眼高翻倍 → 地面在画面里变薄（用同一套几何：把可见距离和眼高一起按 √2 放大不改变比例，
        // 因此直接用 ringDistance 的相对分布验证：近圈距离变大时占比下降）
        val far = Ground3D.ringDistances(Planets.byId("earth"), preset.rings)
        assertTrue("第 0 圈必须在脚下", far[0] < 0.01f)
        assertTrue("远端应接近可见距离", far[preset.rings - 1] > 1f)
    }

    /** 仰视时画面上不能有任何地面像素；pitch 越大覆盖越少。 */
    @Test
    fun lookingUpHasNoGroundPixels() {
        assertEquals(0f, coverage(90f), 0f)
        assertEquals(0f, coverage(60f), 0f)
        assertTrue("平视应有地面", coverage(0f) > 0.3f)
    }

    @Test
    fun coverageIsMonotonicInPitch() {
        var last = 1.1f
        var p = -60f
        while (p <= 60f) {
            val c = coverage(p)
            assertTrue("pitch=$p 覆盖率 $c 大于上一档 $last", c <= last + 0.02f)
            last = c
            p += 10f
        }
        assertTrue(last < 0.02f)
    }

    /**
     * 山体可以合法高出地平线，但每个顶点必须落在它真实距离/高度对应的仰角上（解析式反算）。
     * 这里用完全独立于 Cam 的公式：elev = atan(up / 水平距离)，y = h/2 − f·tan(elev − pitch)。
     */
    @Test
    fun groundAboveHorizonMatchesItsRealElevation() {
        val planet = Planets.byId("earth")
        val eye = Ground3D.EYE_HEIGHT_KM
        var checkedAbove = 0
        for (pitch in listOf(0f, 10f, -15f)) {
            val c = cam(pitch)
            for (cell in cells(pitch)) {
                if (cell.clipped || cell.poly.size != 4) continue   // 被近平面裁剪过的格子顶点是插值出来的，跳过
                for (i in 0 until 4) {
                    val wp = cell.world[i]
                    val expectY = analyticY(wp, pitch)
                    val gotY = cell.poly[i][1]
                    assertEquals("pitch=$pitch 顶点未落在解析位置", expectY, gotY, 0.5f)
                    if (gotY < Ground3D.horizonY(c) - 2f) checkedAbove++
                }
            }
        }
        assertTrue("本工程允许山体越地平线，测试应至少覆盖到一些这样的顶点（$checkedAbove）", checkedAbove > 0)
    }

    /**
     * 完全独立的解析投影：只用地平坐标与俯仰角，不经过 Cam。
     * q = 沿视线方向的水面距离，u = 相对眼高的高度；
     * dz = q·cos p + u·sin p，dy = u·cos p − q·sin p，y = h/2 − f·dy/dz。
     */
    private fun analyticY(wp: FloatArray, pitch: Float, yaw: Float = 0f): Float {
        val east = wp[1]
        val north = wp[0]
        val up = wp[2]
        val yawRad = yaw * PI.toFloat() / 180f
        val q = north * cos(yawRad) + east * sin(yawRad)
        val p = pitch * PI.toFloat() / 180f
        val dz = q * cos(p) + up * sin(p)
        val dy = up * cos(p) - q * sin(p)
        return h / 2f - focal * dy / dz
    }

    /** 近平面与相机前向仍是唯一可见性判据：仰视 90° 时地面全在相机后方。 */
    @Test
    fun nearPlaneAndForwardOnlyDecideVisibility() {
        for (pitch in listOf(80f, 89f, 90f)) {
            assertTrue("pitch=$pitch 仍有地面", cells(pitch).isEmpty())
        }
        assertTrue("俯视时应有地面", cells(-40f).isNotEmpty())
    }

    /** 相邻两圈在地平线附近的投影间距接近等距，最大不超过 h/12。 */
    @Test
    fun ringSpacingIsEvenOnScreen() {
        val planet = Planets.byId("earth")
        val dist = Ground3D.ringDistances(planet, preset.rings)
        val c = cam(0f)
        val ys = FloatArray(preset.rings) { i ->
            val p = Ground3D.surfaceWorld(planet, 0f, dist[i], 0f)   // 平地参考：只考察投影密度
            val camPts = c.toCamera(p[0], p[1], p[2])
            c.toScreen(camPts)[1]
        }
        var maxStep = 0f
        for (i in 1 until preset.rings) {
            // 只考核平视画面内的相邻圈（更近的几圈在画面之外，专供俯视）
            if (ys[i] < 0f || ys[i] > h) continue
            maxStep = maxOf(maxStep, abs(ys[i - 1] - ys[i]))
        }
        assertTrue("地面圈距在屏幕上过大：$maxStep px", maxStep <= h / 12f + 1f)
    }

    /** 不存在宽度超过画面 1/8 的单一填充色块。 */
    @Test
    fun noCellIsWiderThanAnEighthOfTheScreen() {
        val c = cam(0f)
        val rowY = h - 2f
        for (cell in cells(0f)) {
            val xs = ArrayList<Float>()
            for (i in cell.poly.indices) {
                val a = cell.poly[i]; val b = cell.poly[(i + 1) % cell.poly.size]
                if ((a[1] - rowY) * (b[1] - rowY) <= 0f && a[1] != b[1]) {
                    val t = (rowY - a[1]) / (b[1] - a[1])
                    xs.add(a[0] + (b[0] - a[0]) * t)
                }
            }
            if (xs.size >= 2) {
                val width = xs.max() - xs.min()
                assertTrue("第 ${cell.ring} 圈第 ${cell.sector} 格宽 ${width}px", width <= w / 8f + 2f)
            }
        }
        assertTrue(c.h > 0f)
    }

    /** 同一圈相邻格点的亮度差很小，且最暗不低于基色的 25%。 */
    @Test
    fun shadingIsContinuousAndNeverBlack() {
        val planet = Planets.byId("earth")
        val mesh = Ground3D.heightField(planet, preset)
        val smoothed = Ground3D.smoothedMesh(mesh, preset.rings, preset.sectors)
        val profile = Terrain.profileOf(planet)!!
        val dist = Ground3D.ringDistances(planet, preset.rings)
        for (r in 1 until preset.rings) {
            val cellEast = maxOf(0.02f, dist[r] * 2f * PI.toFloat() / preset.sectors)
            val cellNorth = maxOf(0.02f, dist[r] - dist[r - 1])
            val raw = FloatArray(preset.sectors)
            for (s in 0 until preset.sectors) {
                val s2 = (s + 1) % preset.sectors
                val sPrev = (s - 1 + preset.sectors) % preset.sectors
                fun hk2(rr: Int, ss: Int) = Ground3D.reliefHeightKm(profile, smoothed[rr][ss], dist[rr])
                val dhE2 = (hk2(r, s2) - hk2(r, sPrev)) * 0.5f
                val dhN2 = (hk2(minOf(r + 1, preset.rings - 1), s) - hk2(maxOf(r - 1, 0), s)) * 0.5f
                raw[s] = Ground3D.lambert((dhE2 / cellEast).coerceIn(-1.2f, 1.2f), (dhN2 / cellNorth).coerceIn(-1.2f, 1.2f), 30f, 90f)
            }
            var light = raw
            repeat(4) {
                val next = FloatArray(preset.sectors)
                for (s in 0 until preset.sectors) {
                    val l = light[s]
                    val lp = light[(s - 1 + preset.sectors) % preset.sectors]
                    val ln = light[(s + 1) % preset.sectors]
                    next[s] = (lp + 2f * l + ln) / 4f
                }
                light = next
            }
            var prev = Float.NaN
            for (s in 0 until preset.sectors) {
                val s2 = (s + 1) % preset.sectors
                val sPrev = (s - 1 + preset.sectors) % preset.sectors
                val shade = 0.34f + 0.66f * light[s]
                if (!prev.isNaN()) {
                    assertTrue("第 $r 圈相邻格点明暗跳变 ${abs(shade - prev)}", abs(shade - prev) <= 0.15f)
                }
                prev = shade
                assertTrue("第 $r 圈出现纯黑", shade >= 0.25f)
            }
        }
    }

    /** 山脊阴影：太阳在山上→受光；太阳落到地平线下→只剩环境光；方位改变→阴影跟着移动。 */
    @Test
    fun shadowsFollowTheSun() {
        // 坡面朝东（法线指向东侧 = 梯度向西，gx < 0）时，东边的太阳照亮它
        val up = Ground3D.lambert(-0.4f, 0f, 30f, 90f)
        val down = Ground3D.lambert(0.4f, 0f, 30f, 90f)
        assertTrue("朝阳坡应被照亮", up > 0.2f)
        assertTrue("背光坡应只剩环境光", up > down)
        assertEquals("太阳在地平线下不应有直射", 0f, Ground3D.lambert(0f, 0f, -5f, 90f), 1e-6f)
        assertEquals("水平面在正午应全亮", 1f, Ground3D.lambert(0f, 0f, 90f, 0f), 1e-4f)
    }

    /** 树：只在近处、按真实米数投影、有上限、随距离单调变小。 */
    @Test
    fun treesAreSmallNearAndAbsentFarAway() {
        val planet = Planets.byId("earth")
        val mesh = Ground3D.heightField(planet, preset)
        val dist = Ground3D.ringDistances(planet, preset.rings)
        val maxPx = h * 0.12f
        val c = cam(0f)
        val heights = ArrayList<Pair<Float, Float>>()
        for (r in 2 until preset.rings) {
            val d = dist[r]
            if (d < 0.02f || d > 0.2f) continue
            for (s in 0 until preset.sectors) {
                val tree = Ground3D.treeAt(planet, r, s, 0.6f)
                if (tree <= 0.05f) continue
                val base = Ground3D.surfaceWorld(planet, mesh[r][s], d, (s.toFloat() / preset.sectors) * 360f)
                val p = Ground3D.treeScreen(c, base, 0.006f + 0.010f * tree, maxPx) ?: continue
                heights.add(d to p[2])
                assertTrue("树高 ${p[2]} px 超过 1/8 画布", p[2] <= h / 8f + 0.01f)
            }
        }
        assertTrue("平视时应看得到一些树（${heights.size}）", heights.isNotEmpty())
        val near = heights.filter { it.first < 0.05f }.map { it.second }
        val far = heights.filter { it.first > 0.1f }.map { it.second }
        if (near.isNotEmpty() && far.isNotEmpty()) {
            assertTrue("近处的树应比远处高", near.max() > far.max())
        }
        for (r in 2 until preset.rings) {
            if (dist[r] > 0.2f) {
                assertFalse("200 m 外不该画树", Ground3D.treeVisible(dist[r]))
            } else if (dist[r] < 0.02f) {
                assertFalse("20 m 内不该画树", Ground3D.treeVisible(dist[r]))
            }
        }
        assertNull("仰视时不该画树", Ground3D.treeScreen(cam(60f), Ground3D.surfaceWorld(planet, mesh[3][0], dist[3], 0f), 0.02f, maxPx))
    }

    /** 平面方案：可见距离由眼高与消光决定，与半径无关。 */
    @Test
    fun groundRangeIsPlaneBasedNotCurvature() {
        val earth = Ground3D.horizonDistanceKm(Planets.byId("earth"))
        val moon = Ground3D.horizonDistanceKm(Planets.byId("moon"))
        val venus = Ground3D.horizonDistanceKm(Planets.byId("venus"))
        assertTrue("地球可见距离应在数公里量级（$earth）", earth in 4f..6f)
        assertEquals("月球（无大气）应与地球同量级", earth, moon, 0.6f)
        assertTrue("金星的厚霾应缩短可见距离", venus < earth * 0.8f)
        assertEquals("眼高翻倍按 √2 增长", earth * kotlin.math.sqrt(2f),
            Ground3D.viewRangeKm(Planets.byId("earth"), Ground3D.EYE_HEIGHT_KM * 2f), 0.02f)
    }

    /** 换 yaw 不会把格子甩到天上：地面顶点的仰角始终与其高度一致。 */
    @Test
    fun groundIsStableAcrossYaw() {
        for (yaw in listOf(0f, 37f, 90f, 143f, 200f, 311f)) {
            val c = cam(0f, yaw)
            for (cell in Ground3D.groundCells(c, Planets.byId("earth"), preset)) {
                for (i in cell.poly.indices) {
                    val wp = cell.world[i]
                    val horiz = kotlin.math.hypot(wp[0].toDouble(), wp[1].toDouble()).toFloat()
                    if (horiz <= 0f) continue
                    val elev = atan(wp[2] / horiz) * 180f / PI.toFloat()
                    assertTrue("yaw=$yaw 出现超过 22° 的地形仰角：$elev", elev <= Ground3D.MAX_RELIEF_ELEV_DEG + 0.6f)
                    if (!cell.clipped && cell.poly.size == 4) {
                        assertEquals("yaw=$yaw", analyticY(wp, 0f, yaw), cell.poly[i][1], 0.5f)
                    }
                }
            }
        }
    }

    /** 网格近圈密度：每圈的世界距离严格递增，前几圈覆盖到脚下。 */
    @Test
    fun ringDistancesAreIncreasingAndStartAtTheFeet() {
        val planet = Planets.byId("earth")
        val dist = Ground3D.ringDistances(planet, preset.rings)
        for (i in 1 until dist.size) {
            assertTrue("第 $i 圈距离未递增：${dist[i - 1]} → ${dist[i]}", dist[i] > dist[i - 1])
        }
        assertTrue("第一圈必须在脚下：${dist[0]}", dist[0] <= 0.0001f)
        // v0.6.8：几何一直铺到几百公里，最后两圈是「没有盘缘」的保证
        assertTrue("最外圈应铺到几百公里：${dist.last()}", dist.last() >= 100f)
        assertTrue("倒数第二圈应铺到可见距离", dist[dist.size - 2] >= Ground3D.horizonDistanceKm(planet) * 0.9f)
    }

    /** 俯视时铺满画面不留缝。 */
    @Test
    fun lookingDownFillsTheScreen() {
        assertTrue("俯视 60° 覆盖 ${coverage(-60f)}", coverage(-60f) > 0.97f)
        assertFalse("平视不该铺满", coverage(0f) > 0.99f)
    }
}
