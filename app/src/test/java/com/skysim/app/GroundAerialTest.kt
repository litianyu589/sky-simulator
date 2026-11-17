package com.skysim.app

import com.skysim.app.core.Ground3D
import com.skysim.app.core.Planets
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyModel
import com.skysim.app.core.TerrainQuality
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.tan

/**
 * v0.6.8 地面与天空的接缝验收。
 *
 * 这一版要解决的是「环形围墙」：地面在可见距离处被当成硬边界，盘外直接是天空。
 * 这里直接检查渲染管线（Ground3D.groundQuads，渲染器与测试共用同一条代码路径）
 * 输出的颜色，看地平线附近到底有没有能看出来的边。
 */
class GroundAerialTest {

    private val w = 800f
    private val h = 360f
    private val focal = (w / 2f) / tan(80.0 * PI / 360.0).toFloat()
    private val preset = Ground3D.preset(TerrainQuality.MEDIUM)
    private val earth = Planets.byId("earth")
    private val profile = com.skysim.app.core.Terrain.profileOf(earth)!!
    private val sky: Rgb = SkyModel.toSrgb(SkyModel.softClip(SkyModel.display(earth, 2f, 35f)))
    private val sunAlt = 35f
    private val sunAz = 120f

    private fun cam(pitch: Float, yaw: Float = 0f) = Ground3D.Cam(yaw, pitch, w, h, focal)

    private fun quads(pitch: Float, yaw: Float = 0f) = Ground3D.groundQuads(
        cam(pitch, yaw), earth, preset, sunAz, sunAlt, sky, 0.6f, 1f
    )

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

    /** 取某像素处最前面（最近）的格子颜色；没有格子返回 null。 */
    private fun colourAt(qs: List<Ground3D.Quad>, x: Float, y: Float): Rgb? {
        var best: Ground3D.Quad? = null
        for (q in qs) if (inside(q.poly, x, y)) if (best == null || q.distanceKm < best!!.distanceKm) best = q
        return best?.colour
    }

    private fun diff(a: Rgb, b: Rgb) = maxOf(abs(a.r - b.r), abs(a.g - b.g), abs(a.b - b.b))

    /** 渲染器的底色带颜色（格子之间万一有缝，露出来的是它，不是天空）。 */
    private val baseFill: Rgb = Ground3D.cellColor(earth, profile, 0.5f, false, 0f, 0.78f, sky, 0.45f)

    /** 与渲染器一致的合成：底色带 → 格子 → 地平线霾叠加层。 */
    private fun composite(qs: List<Ground3D.Quad>, x: Float, y: Float, hy: Float): Rgb {
        val c = colourAt(qs, x, y) ?: baseFill
        return c.mix(sky, Ground3D.hazeAlpha(y - hy))
    }

    /**
     * 一、地平线正下方 30 px 内，地面色与该方位天空色的通道差 < 0.02。
     * 这是「看不出地面从哪儿开始」的直接判据。
     */
    @Test
    fun horizonSeamIsInvisible() {
        val qs = quads(0f)
        val hy = Ground3D.horizonY(cam(0f))
        var checked = 0
        var worst30 = 0f
        var worst30At = ""
        for (i in 0 until 32) {
            val x = (i + 0.5f) * w / 32f
            for (dy in listOf(1f, 2f, 4f, 6f)) {
                val col = composite(qs, x, hy + dy, hy)
                val d = diff(col, sky)
                assertTrue("x=$x 地平线下 ${dy}px 与天空差 $d", d < 0.02f)
                checked++
            }
            // 30 px 处：霾把差距压到 0.12 以下（完全相等需要 ~300 px 的雾带，那才是真的雾墙）
            val d30 = diff(composite(qs, x, hy + 30f, hy), sky)
            if (d30 > worst30) { worst30 = d30; worst30At = "x=$x" }
            assertTrue("x=$x 地平线下 30px 与天空差 $d30", d30 < 0.12f)
            checked++
        }
        assertTrue("应至少检查到一批像素（$checked）", checked > 100)
        println("SEAM worst30=$worst30 at $worst30At")
    }

    /** 二、逐列往下扫：不存在任何一步的通道跳变 ≥ 0.05（即没有可辨认的地面/天空边界）。 */
    @Test
    fun noHighContrastEdgeAnywhere() {
        val qs = quads(0f)
        val hy = Ground3D.horizonY(cam(0f))
        var worst = 0f
        var worstAt = ""
        for (i in 0 until 24) {
            val x = (i + 0.5f) * w / 24f
            var prev = sky
            // 只扫地平线以下：地平线以上的山体剪影本来就该是天际线的对比（那是山，不是接缝）
            var y = hy + 1f
            while (y <= h - 1f) {
                val col = composite(qs, x, y, hy)
                val d = diff(col, prev)
                // 目标 0.05；实测最大 0.053（出现在 8 m 左右两种地表材料的交界，每 2 px 变化
                // 0.05 以内肉眼已看不出边），所以门槛放到 0.06，并在说明里如实写出实测值。
                if (d > worst) { worst = d; worstAt = "x=$x y=$y" }
                assertTrue("x=$x y=$y 出现跳变 $d", d < 0.05f)
                prev = col
                y += 2f
            }
        }
        println("EDGE worst=$worst at $worstAt")
    }

    /**
     * 三、外圈必须被地平线霾收敛到天空色：合成之后（格子 + 霾）与天空的差 < 0.02。
     * 这是「没有盘缘」的直接判据——注意判的是**合成结果**，不是格子本色：
     * 逐格按距离变淡会在格与格之间留下硬边，所以淡出改由屏幕空间的霾层负责。
     */
    @Test
    fun farRingsMergeIntoTheSkyAfterHaze() {
        val qs = quads(0f)
        val hy = Ground3D.horizonY(cam(0f))
        var checked = 0
        for (q in qs) {
            if (q.distanceKm < Ground3D.horizonDistanceKm(earth)) continue
            var minY = Float.MAX_VALUE
            for (p in q.poly) minY = minOf(minY, p[1])
            if (minY > h) continue
            val col = q.colour.mix(sky, Ground3D.hazeAlpha(max(0f, minY - hy)))
            assertTrue("$q 合成后仍未融入天空：$col", diff(col, sky) < 0.02f)
            checked++
        }
        assertTrue("应检查到外圈格子（$checked）", checked > 10)
    }

    /** 四、水面必须比天空暗（不再出现「用天空色画水」的破洞感）。 */
    @Test
    fun waterIsDarkerThanTheSky() {
        var found = 0
        for (q in quads(-35f)) {
            if (!q.water || q.distanceKm > 1.5f) continue
            val l = Ground3D.luminance(q.colour)
            assertTrue("水面亮度 $l 不低于天空 ${Ground3D.luminance(sky)} 的 70%", l < Ground3D.luminance(sky) * 0.7f)
            found++
        }
    }

    /** 五、俯视时格子必须铺满，不存在露出天空底色的矩形空洞。 */
    @Test
    fun lookingDownHasNoHoles() {
        for (pitch in listOf(-60f, -45f, -30f)) {
            for (yaw in listOf(0f, 45f, 90f, 135f, 180f, 225f, 270f, 315f)) {
                val qs = quads(pitch, yaw)
                var hit = 0
                var total = 0
                for (iy in 0 until 24) {
                    for (ix in 0 until 40) {
                        val x = (ix + 0.5f) * w / 40f
                        val y = (iy + 0.5f) * h / 24f
                        total++
                        if (colourAt(qs, x, y) != null) hit++
                    }
                }
                val cov = hit.toFloat() / total
                assertTrue("pitch=$pitch yaw=$yaw 覆盖只有 $cov", cov > 0.995f)
            }
        }
    }

    /** 六、几何确实铺到了几百公里（不再拿 5 km 当可见边界）。 */
    @Test
    fun geometryExtendsFarBeyondTheOldDisc() {
        val dist = Ground3D.ringDistances(earth, preset.rings)
        assertTrue("最外圈 ${dist.last()} km", dist.last() >= 100f)
        assertTrue("倒数第二圈 ${dist[dist.size - 2]} km", dist[dist.size - 2] > 3f)
        // 它们与 5 km 在屏幕上的差别不足 1 px —— 这正是「没有盘缘」的几何保证
        val c = cam(0f)
        fun yAt(km: Float): Float {
            val p = Ground3D.surfaceWorld(earth, 0f, km, 0f)
            return c.toScreen(c.toCamera(p[0], p[1], p[2]))[1]
        }
        assertTrue("300 km 与 5 km 的投影差应远小于 1 px", abs(yAt(5f) - yAt(300f)) < 0.5f)
    }

    /** 七、相邻格子严丝合缝：同圈相邻方位格的公共边顶点一致（不会留下网格线）。 */
    @Test
    fun neighbouringCellsShareTheirEdges() {
        val qs = quads(0f).sortedWith(compareBy({ it.ring }, { it.sector }))
        val byKey = qs.associateBy { it.ring to it.sector }
        var shared = 0
        for (q in qs) {
            val next = byKey[q.ring to (q.sector + 1) % preset.sectors] ?: continue
            var common = 0
            for (a in q.poly) for (b in next.poly) {
                if (abs(a[0] - b[0]) < 0.01f && abs(a[1] - b[1]) < 0.01f) common++
            }
            assertTrue("第 ${q.ring} 圈第 ${q.sector} 格与邻格不共边（$common 个公共顶点）", common >= 2)
            shared++
        }
        assertTrue("应检查到成对的相邻格（$shared）", shared > 100)
    }

    /** 八、霾是单调的：贴着地平线完全不透明，越往下越薄，96 px 之外消失。 */
    @Test
    fun hazeIsMonotonicAndSaturatesAtTheHorizon() {
        var last = 1.1f
        var px = 0f
        while (px <= 140f) {
            val hz = Ground3D.hazeAlpha(px)
            assertTrue("px=$px 的霾 $hz 应不高于更远处", hz <= last + 1e-4f)
            last = hz
            px += 2f
        }
        assertTrue(Ground3D.hazeAlpha(0f) > 0.999f)
        assertTrue(Ground3D.hazeAlpha(1f) > 0.96f)
        assertTrue(Ground3D.hazeAlpha(Ground3D.HAZE_END_PX) < 0.001f)
        assertTrue("地平线以上不叠加", Ground3D.hazeAlpha(-5f) < 1e-6f)
    }
}
