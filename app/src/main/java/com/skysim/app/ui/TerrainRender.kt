package com.skysim.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.skysim.app.core.Planet
import com.skysim.app.core.ReliefKind
import com.skysim.app.core.Rgb
import com.skysim.app.core.SceneOptions
import com.skysim.app.core.SkyModel
import com.skysim.app.core.Terrain
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/** 地表基础色：按天体与高度分层，只做色块，不假装是贴图。 */
internal fun terrainTint(planet: Planet, kind: ReliefKind, height: Float, sea: Float): Rgb {
    val land = (height - sea).coerceAtLeast(0f) / (1f - sea).coerceAtLeast(0.01f)
    return when (kind) {
        ReliefKind.EARTH -> when {
            height < sea -> Rgb(0.06f, 0.13f, 0.22f)
            land > 0.72f -> Rgb(0.80f, 0.84f, 0.88f)              // 雪线
            land > 0.45f -> planet.groundTint.mix(Rgb(0.42f, 0.36f, 0.28f), 0.5f)
            else -> Rgb(0.15f, 0.30f, 0.15f).mix(planet.groundTint, 0.35f)
        }
        ReliefKind.MARS -> Rgb(0.52f, 0.27f, 0.15f).mix(planet.groundTint, 0.4f * land)
        ReliefKind.CRATERED -> Rgb(0.30f, 0.29f, 0.28f).mix(planet.groundTint, 0.35f)
        ReliefKind.ICY -> Rgb(0.70f, 0.76f, 0.83f).mix(planet.groundTint, 0.25f)
        ReliefKind.SMOOTH -> planet.groundTint.scale(0.9f)
        ReliefKind.NONE -> planet.groundTint
    }
}

/**
 * 地形 + 光影渲染（实验性）。
 *
 * 逐列：高度 → 屏幕 y；太阳方位与高度角 → 该列可见光比例（射线行进）；朗伯余弦 → 亮度。
 * 水面做天空色镜面反射，太阳低垂时沿方位拉出一条高光带。地球额外画植被色带与锥形树剪影。
 */
internal fun DrawScope.drawTerrainGround(
    planet: Planet,
    proj: SkyProjector,
    lookAz: Float,
    sunAzDeg: Float,
    sunAltDeg: Float,
    scene: SceneOptions,
    w: Float,
    h: Float
) {
    val profile = Terrain.profileOf(planet) ?: return
    val columns = scene.quality.columns.coerceAtMost((w / 3f).toInt().coerceAtLeast(24))
    val heights = Terrain.heightField(planet, columns)
    if (heights.isEmpty()) return
    val trees = Terrain.treeField(planet, columns, scene.vegetation)
    val sea = profile.seaLevel
    val horizon = SkyModel.toSrgb(SkyModel.display(planet, 2f, sunAltDeg))
    val ampPx = h * 0.16f
    val deltaAz = sunAzDeg - lookAz

    val xs = FloatArray(columns)
    val ys = FloatArray(columns)
    val baseY = FloatArray(columns)
    for (i in 0 until columns) {
        val t = i.toFloat() / (columns - 1).coerceAtLeast(1)
        val az = lookAz - 45f + 90f * t
        val p = proj.projectAzAlt(az, 0f)
        xs[i] = p?.x ?: (-w * 0.2f + w * 1.4f * t)
        baseY[i] = p?.y ?: (h * 0.55f)
        val elev = (heights[i] - sea).coerceAtLeast(0f) * ampPx
        ys[i] = baseY[i] - elev
    }

    // 天空到地面的整体轮廓（山脊线）
    val ridge = Path()
    ridge.moveTo(xs[0], ys[0])
    for (i in 1 until columns) ridge.lineTo(xs[i], ys[i])
    ridge.lineTo(xs[columns - 1], h + 4f)
    ridge.lineTo(xs[0], h + 4f)
    ridge.close()

    val ambient = SkyModel.toSrgb(SkyModel.softClip(horizon.scale(0.30f)))
    drawPath(ridge, rgb(ambient.mix(planet.groundTint, 0.35f)))

    // 逐列上色：直射 + 环境光、水面镜面、植被
    for (i in 0 until columns) {
        val x0 = xs[i] - (w / columns) * 0.6f
        val width = (w / columns) * 1.25f
        val top = ys[i]
        val bottom = h + 2f
        if (bottom <= top) continue
        val isWater = heights[i] < sea && profile.water
        val vis = if (isWater) 1f else Terrain.sunVisibility(
            planet, heights, trees, i, sunAltDeg, deltaAz,
            stepKm = 2.0f, steps = scene.quality.steps
        )
        val light = Terrain.lightMix(vis, sunAltDeg, scene.shadowStrength)
        var tint: Rgb
        if (isWater) {
            val depth = ((sea - heights[i]) / sea.coerceAtLeast(0.01f)).coerceIn(0f, 1f)
            val water = Rgb(0.05f, 0.12f, 0.20f).mix(horizon, 0.28f - 0.18f * depth)
            val glint = Terrain.waterGlint(sunAltDeg, deltaAz) * (0.35f + 0.65f * (1f - depth))
            val sunTint = planeSunTint(planet)
            tint = water.scale(0.45f + 0.55f * light).mix(sunTint, (glint * 0.5f).coerceIn(0f, 0.6f))
        } else {
            var ground = terrainTint(planet, profile.kind, heights[i], sea)
            if (profile.trees && scene.vegetation > 0.02f) {
                val veg = (trees[i] / 0.55f).coerceIn(0f, 1f) * scene.vegetation
                ground = ground.mix(Rgb(0.10f, 0.26f, 0.12f), 0.45f * veg)
            }
            tint = ground.scale(0.22f + 0.95f * light)
        }
        val c = SkyModel.toSrgb(SkyModel.softClip(tint)).clamp01()
        drawRect(Color(c.r, c.g, c.b), topLeft = Offset(x0, top), size = Size(width, bottom - top))

        // 树剪影（地球）：只在该列有植被且高于海平面时画一个团状轮廓
        if (profile.trees && !isWater && trees[i] > 0.06f && scene.vegetation > 0.05f) {
            val th = (trees[i] * ampPx * 2.4f).coerceAtLeast(3f)
            if (th > 1.5f) {
                val tw = (w / columns) * 1.1f
                val tree = Path().apply {
                    moveTo(xs[i] - tw * 0.5f, top + 1f)
                    lineTo(xs[i], top - th)
                    lineTo(xs[i] + tw * 0.5f, top + 1f)
                    close()
                }
                val shade = (0.10f + 0.5f * light).coerceIn(0.05f, 0.6f)
                drawPath(tree, Color(0.05f, 0.13f + 0.10f * shade, 0.06f, 0.92f))
            }
        }
    }

    // 距离感：越靠近画面底部的近景越暗（模拟地表向观察者延伸时的衰减与视角压缩），
    // 只用一条全宽渐变，代价与固定开销无关。
    val depth = Path().apply {
        moveTo(0f, baseY[columns / 2] - ampPx * 0.1f)
        lineTo(w, baseY[columns / 2] - ampPx * 0.1f)
        lineTo(w, h + 4f)
        lineTo(0f, h + 4f)
        close()
    }
    drawPath(
        depth,
        brush = Brush.verticalGradient(
            listOf(Color(0f, 0f, 0f, 0f), Color(0f, 0f, 0f, 0.18f), Color(0f, 0f, 0f, 0.42f)),
            startY = baseY[columns / 2] - ampPx * 0.1f,
            endY = h
        )
    )

    // 水面高光带：太阳低垂时沿方位拉长
    if (profile.water) {
        val glint = Terrain.waterGlint(sunAltDeg, deltaAz)
        if (glint > 0.05f) {
            val p = proj.projectAzAlt(sunAzDeg, 0.4f)
            if (p != null && p.x > -w * 0.3f && p.x < w * 1.3f) {
                val streakW = w * (0.05f + 0.16f * glint)
                for (k in 0..5) {
                    val yy = p.y + h * 0.012f * k
                    val alpha = (glint * 0.22f * (1f - k / 6f)).coerceIn(0f, 0.3f)
                    drawRect(
                        Color(1f, 0.86f, 0.62f, alpha),
                        topLeft = Offset(p.x - streakW / 2f * (1f - k * 0.1f), yy),
                        size = Size(streakW * (1f - k * 0.1f), h * 0.010f)
                    )
                }
            }
        }
    }
}

private fun planeSunTint(planet: Planet): Rgb {
    val sc = planet.starColor
    return Rgb(sc.r.coerceAtMost(1f), sc.g.coerceAtMost(1f), sc.b.coerceAtMost(1f))
}
