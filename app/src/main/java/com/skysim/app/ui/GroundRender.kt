package com.skysim.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.skysim.app.core.Ground3D
import com.skysim.app.core.Planet
import com.skysim.app.core.PseudoGroundClip
import com.skysim.app.core.ReliefKind
import com.skysim.app.core.ReliefProfile
import com.skysim.app.core.Rgb
import com.skysim.app.core.SceneOptions
import com.skysim.app.core.SkyModel
import com.skysim.app.core.Terrain
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * v0.6.5：3D 地面渲染。
 *
 * 与 v0.6.4 的屏幕矩形色块不同，这里把极坐标网格（圈 × 方位）逐个格点投影成梯形，
 * 由远到近绘制，光照用真实法线做朗伯项。相机 yaw / pitch 变化时，格点位置随之移动，
 * 山脊的远近关系与遮挡也由投影决定，所以地面会和天空一样随视角平移、起伏。
 */
internal fun DrawScope.drawGround3D(
    planet: Planet,
    proj: SkyProjector,
    lookAz: Float,
    lookAlt: Float,
    sunAzDeg: Float,
    sunAltDeg: Float,
    scene: SceneOptions,
    w: Float,
    h: Float
) {
    val profile = Terrain.profileOf(planet) ?: return
    // v0.8.3 修复：与伪地面、二维地面共用同一条地平线判据。地平线已落到屏幕下缘之外
    // （或地平线方向本身就不可投影）时，三维地面整体在屏幕下方，可见面积为 0，直接不画。
    if (PseudoGroundClip.horizonTopY(lookAz, lookAlt, h, proj.pixelsPerRadian()) == null) return
    val preset = Ground3D.preset(scene.quality)
    val cam = Ground3D.Cam(lookAz, lookAlt, w, h, proj.pixelsPerRadian())
    val rings = preset.rings
    val sectors = preset.sectors
    val veg = scene.vegetation.coerceIn(0f, 1f)
    val skyAtHorizon = SkyModel.toSrgb(SkyModel.softClip(SkyModel.display(planet, 2f, sunAltDeg)))
    val hy = Ground3D.horizonY(cam)

    // ---- 底色带（v0.6.8）------------------------------------------------
    // 目的只有一个：地平线附近先铺一层「天空色 → 地面色」的渐变，
    // 这样格与格之间的抗锯齿缝隙、被裁剪掉的窄条都不会露出天空底色，
    // 地面也不会在地平线处出现一条能看出来的边。
    val midGround = Ground3D.cellColor(
        planet, profile, 0.5f, false, 0f, 0.78f, skyAtHorizon, 0.45f
    )
    val bandTop = max(0f, hy)
    val bandBottom = min(h, bandTop + Ground3D.HAZE_END_PX)
    if (bandBottom > bandTop) {
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(rgb(skyAtHorizon), rgb(midGround)),
                startY = bandTop,
                endY = bandBottom
            ),
            topLeft = Offset(0f, bandTop),
            size = Size(w, bandBottom - bandTop)
        )
    }
    if (bandBottom < h) {
        drawRect(
            color = rgb(midGround),
            topLeft = Offset(0f, bandBottom),
            size = Size(w, h - bandBottom)
        )
    }

    // ---- 地面格子：远 → 近 ----------------------------------------------
    val quads = Ground3D.groundQuads(
        cam, planet, preset, sunAzDeg, sunAltDeg, skyAtHorizon, veg, scene.shadowStrength
    )
    // ---- 地平线霾：一层不透明度渐变的天空色 ------------------------------
    // 屏幕空间的一层雾，保证「地面从哪儿开始」在任何方位、任何 pitch 都看不出来。
    if (hy < h) {
        val top = max(0f, hy)
        val bottom = min(h, hy + Ground3D.HAZE_END_PX)
        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(rgb(skyAtHorizon, 1f), rgb(skyAtHorizon, 0f)),
                startY = hy,
                endY = hy + Ground3D.HAZE_END_PX
            ),
            topLeft = Offset(0f, top),
            size = Size(w, bottom - top)
        )
    }

    // v0.7.2：Path 复用 + 面积剔除。原来每格都 new 一个 Path，
    // 低画质 1500 格就是 1500 个对象/帧；屏幕面积 < 2 px² 的格子肉眼不可见，直接丢。
    val path = Path()
    var drawn = 0
    for (q in quads) {
        val poly = q.poly
        if (poly.size < 3) continue
        var area2 = 0f
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            area2 += a[0] * b[1] - b[0] * a[1]
        }
        if (kotlin.math.abs(area2) * 0.5f < 2f) continue
        drawn++
        // 每个格子向外扩 0.7 px：相邻格子的抗锯齿边缘互相压住，不会留下一道缝
        var cx = 0f
        var cy = 0f
        for (p in poly) { cx += p[0]; cy += p[1] }
        cx /= poly.size
        cy /= poly.size
        path.reset()
        path.apply {
            for (i in poly.indices) {
                val dx = poly[i][0] - cx
                val dy = poly[i][1] - cy
                val len = kotlin.math.sqrt(dx * dx + dy * dy).coerceAtLeast(1e-3f)
                val x = poly[i][0] + dx / len * 0.7f
                val y = poly[i][1] + dy / len * 0.7f
                if (i == 0) moveTo(x, y) else lineTo(x, y)
            }
            close()
        }
        drawPath(path, rgb(q.colour))
    }

    // ---- 树 -------------------------------------------------------------
    if (profile.trees && veg > 0.03f) {
        val mesh = Ground3D.heightField(planet, preset)
        val dist = Ground3D.ringDistances(planet, rings, Ground3D.nearDistanceKm(cam))
        val maxPx = h * 0.12f
        for (r in 2 until rings) {
            if (!Ground3D.treeVisible(dist[r])) continue      // 只在 20–200 m 内画树
            for (s in 0 until sectors) {
                val tree = Ground3D.treeAt(planet, r, s, veg)
                if (tree <= 0.05f) continue
                if (profile.water && mesh[r][s] < profile.seaLevel) continue
                val treeKm = 0.006f + 0.010f * tree
                val base = Ground3D.surfaceWorld(
                    planet, mesh[r][s], dist[r], (s.toFloat() / sectors) * 360f
                )
                val p = Ground3D.treeScreen(cam, base, treeKm, maxPx) ?: continue
                val hPx = p[2]
                val wPx = (hPx * 0.28f).coerceIn(0.6f, h * 0.02f)
                val trunk = Rgb(0.05f, 0.09f, 0.04f).scale((0.35f + 0.65f * veg).coerceIn(0f, 1f))
                drawPath(
                    Path().apply {
                        moveTo(p[0], p[1] - hPx)
                        lineTo(p[0] - wPx, p[1])
                        lineTo(p[0] + wPx, p[1])
                        close()
                    },
                    rgb(trunk)
                )
            }
        }
    }
}

internal fun DrawScope.drawPseudoGround(
    planet: Planet,
    proj: SkyProjector,
    lookAz: Float,
    lookAlt: Float,
    sunAltDeg: Float,
    w: Float,
    h: Float
) {
    // v0.8.3 修复：地平线投影拿不到（视线夹角 > 85.42°，dz <= 0.08）或已落到屏幕下缘之外时，
    // 这一帧直接不画伪地面，天空照常铺满。绝不用屏幕中线 h * 0.5 或任何比例常量兜底。
    val horizon = PseudoGroundClip.horizonTopY(lookAz, lookAlt, h, proj.pixelsPerRadian()) ?: return
    val top = horizon.coerceIn(0f, h)
    val span = (h - top).coerceAtLeast(1f)
    val day = com.skysim.app.core.Astro.daylightFactor(sunAltDeg)
    val bands = Ground3D.bands(planet)
    // 底层：从地平线向下逐渐加深的云海，透明度低，不留硬边
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(
                rgb(bands.first().colour, 0.02f),
                rgb(bands.first().colour, 0.22f),
                rgb(bands[bands.size / 2].colour, 0.55f),
                rgb(bands.last().colour, 0.80f)
            ),
            startY = top,
            endY = h
        ),
        topLeft = Offset(0f, top),
        size = Size(w, h - top)
    )
    // 云带条纹：位置随 yaw 平移（视差），越靠下越宽、越不透明
    var i = 0
    for (b in bands) {
        val t = b.at
        val y = top + span * t.pow(1.25f)
        val bandH = span * (0.035f + 0.10f * t)
        val drift = ((lookAz * (3f + 6f * t)) % w + w) % w
        val a = (b.alpha * (0.5f + 0.5f * day)).coerceIn(0f, 0.75f)
        val path = Path().apply {
            moveTo(-w + drift - w, y)
            lineTo(w + drift, y)
            lineTo(w + drift, y + bandH)
            lineTo(-w + drift - w, y + bandH)
            close()
        }
        drawPath(
            path,
            brush = Brush.horizontalGradient(
                colors = listOf(
                    rgb(b.colour, a * 0.35f),
                    rgb(b.colour.scale(1.06f), a),
                    rgb(b.colour.scale(0.92f), a * 0.55f),
                    rgb(b.colour, a * 0.3f)
                ),
                startX = -w + drift - w,
                endX = w + drift
            )
        )
        i++
    }
    // 地平线处的云层遮掩：一条很淡的过渡带，消除天空与伪地面之间的硬边
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(rgb(bands.first().colour, 0f), rgb(bands.first().colour, 0.45f)),
            startY = top - span * 0.10f,
            endY = top + span * 0.10f
        ),
        topLeft = Offset(0f, top - span * 0.10f),
        size = Size(w, span * 0.20f)
    )
    if (i == 0) return
}
