package com.skysim.app.ui

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.nativeCanvas
import com.skysim.app.core.Astro
import com.skysim.app.core.Perf
import com.skysim.app.core.SkyGridCache
import androidx.compose.ui.graphics.toArgb
import com.skysim.app.core.EffectState
import com.skysim.app.core.FxRules
import com.skysim.app.core.Ground3D
import com.skysim.app.core.Planet
import com.skysim.app.core.PseudoGroundClip
import com.skysim.app.core.WeatherState
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyGeo
import com.skysim.app.core.SceneOptions
import com.skysim.app.core.Terrain
import com.skysim.app.core.SkyModel
import com.skysim.app.core.StarCatalog
import kotlin.math.PI
import kotlin.math.min
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan
import kotlin.random.Random

internal data class Vec3(val x: Float, val y: Float, val z: Float)

private fun toVec(azDeg: Float, altDeg: Float): Vec3 {
    val az = azDeg * PI / 180.0
    val alt = altDeg * PI / 180.0
    return Vec3(
        (cos(alt) * cos(az)).toFloat(),
        (cos(alt) * sin(az)).toFloat(),
        sin(alt).toFloat()
    )
}

private fun toAzAlt(v: Vec3): Pair<Float, Float> {
    val alt = asin(v.z.toDouble().coerceIn(-1.0, 1.0)) * 180.0 / PI
    val az = atan2(v.y.toDouble(), v.x.toDouble()) * 180.0 / PI
    return Pair(Astro.normalize360(az).toFloat(), alt.toFloat())
}

/** 绕任意轴旋转（Rodrigues 公式）。 */
private fun rotate(v: Vec3, axis: Vec3, angleDeg: Float): Vec3 {
    val a = angleDeg * PI / 180.0
    val c = cos(a)
    val s = sin(a)
    val k = axis
    val dot = v.x * k.x + v.y * k.y + v.z * k.z
    val cx = k.y * v.z - k.z * v.y
    val cy = k.z * v.x - k.x * v.z
    val cz = k.x * v.y - k.y * v.x
    return Vec3(
        (v.x * c + cx * s + k.x * dot * (1 - c)).toFloat(),
        (v.y * c + cy * s + k.y * dot * (1 - c)).toFloat(),
        (v.z * c + cz * s + k.z * dot * (1 - c)).toFloat()
    )
}

private data class Star(val vec: Vec3, val mag: Float)

private val starField: List<Star> = run {
    val rnd = Random(20261101)
    val list = ArrayList<Star>(420)
    repeat(420) {
        val az = rnd.nextFloat() * 360f
        val alt = asin(rnd.nextFloat() * 2f - 1f) * 180.0 / PI
        list.add(Star(toVec(az, alt.toFloat()), 0.25f + rnd.nextFloat() * 0.75f))
    }
    list
}

internal class SkyProjector(
    val w: Float,
    val h: Float,
    val lookAz: Float,
    val lookAlt: Float,
    fovDeg: Float = 80f
) {
    private val scale: Float = (w / 2f) / tan(fovDeg * PI / 360.0).toFloat()
    private val fwd = toVec(lookAz, lookAlt)
    private val right = Vec3(-sin(lookAz * PI / 180.0).toFloat(), cos(lookAz * PI / 180.0).toFloat(), 0f)
    private val up = Vec3(
        (-sin(lookAlt * PI / 180.0) * cos(lookAz * PI / 180.0)).toFloat(),
        (-sin(lookAlt * PI / 180.0) * sin(lookAz * PI / 180.0)).toFloat(),
        cos(lookAlt * PI / 180.0).toFloat()
    )

    fun projectAzAlt(azDeg: Float, altDeg: Float): Offset? = project(toVec(azDeg, altDeg))

    fun project(d: Vec3): Offset? {
        val dz = d.x * fwd.x + d.y * fwd.y + d.z * fwd.z
        if (dz <= 0.08f) return null
        val dx = d.x * right.x + d.y * right.y + d.z * right.z
        val dy = d.x * up.x + d.y * up.y + d.z * up.z
        return Offset(w / 2f + dx / dz * scale, h / 2f - dy / dz * scale)
    }

    fun pixelsPerRadian(): Float = scale
}

internal fun rgb(c: Rgb, alpha: Float = 1f): Color = Color(
    c.r.coerceIn(0f, 1f), c.g.coerceIn(0f, 1f), c.b.coerceIn(0f, 1f), alpha
)

@Composable
fun SkyCanvas(
    planet: Planet,
    latitudeDeg: Float,
    geo: SkyGeo,
    uctFraction: Float,
    showSunPath: Boolean,
    lookAz: Float,
    lookAlt: Float,
    fx: EffectState,
    weather: WeatherState,
    animSeconds: Double,
    scene: SceneOptions = SceneOptions(),
    modifier: Modifier = Modifier
) {
    Canvas(modifier = modifier) {
        val frameStart = System.nanoTime()
        Perf.beginFrame()
        val w = size.width
        val h = size.height
        if (w <= 1f || h <= 1f) return@Canvas
        val proj = SkyProjector(w, h, lookAz, lookAlt)
        val sunAlt = geo.sunAltDeg
        val cover = weather.cloudCover * fx.setting(com.skysim.app.core.EffectId.CLOUDS).strengthClamped() * FxRules.cloudCapability(planet)
        val precip = weather.precipIntensity * fx.setting(com.skysim.app.core.EffectId.RAIN).strengthClamped() * FxRules.rainCapability(planet)
        val dim = SkyModel.weatherDim(cover, precip)

        // 图层顺序：天空 → 星空 → 极光 → 环/卫星/太阳 → 云 → 雨 → 地面 → 霾 → 虹 → 晕 → 闪电
        var __t = System.nanoTime()
        if (scene.twilight.active) {
            drawSkyRows(planet, sunAlt, lookAz, lookAlt, geo.sunAzDeg, proj, w, h, weather, fx, scene)
        } else {
            drawSkyGradient(planet, sunAlt, lookAz, geo.sunAzDeg, proj, w, h, weather, fx, scene)
        }
        com.skysim.app.core.Perf.mark(com.skysim.app.core.Perf.ST_SKY, System.nanoTime() - __t)
        // 空间站没有大气与地平线遮挡，星空一直可见
        drawStars(planet, if (planet.isStation) -18f else sunAlt, latitudeDeg, geo.hourAngleDeg, proj)
        if (fx.setting(com.skysim.app.core.EffectId.AURORA).enabled) {
            drawAurora(planet, latitudeDeg, proj, fx.setting(com.skysim.app.core.EffectId.AURORA).strengthClamped(), animSeconds, sunAlt)
        }
        if (planet.ring != null) drawRings(planet, latitudeDeg, proj, sunAlt)
        drawMoons(planet, geo, proj, sunAlt)
        if (showSunPath) drawSunPath(planet, latitudeDeg, geo, proj)
        drawSun(planet, geo, proj, sunAlt, 1f - 0.65f * dim)
        if (fx.setting(com.skysim.app.core.EffectId.CLOUDS).enabled) {
            drawClouds(planet, geo, proj, weather, fx.setting(com.skysim.app.core.EffectId.CLOUDS).strengthClamped(), animSeconds)
        }
        if (fx.setting(com.skysim.app.core.EffectId.RAIN).enabled) {
            drawRain(planet, proj, weather, fx.setting(com.skysim.app.core.EffectId.RAIN).strengthClamped(), animSeconds)
        }
        if (!planet.isStation) {
            __t = System.nanoTime()
            when {
                !scene.terrainOn -> drawGround(planet, proj, w, h, sunAlt, lookAz)
                Terrain.profileOf(planet) != null ->
                    drawGround3D(planet, proj, lookAz, lookAlt, geo.sunAzDeg, sunAlt, scene, w, h)
                Ground3D.isPseudoGround(planet) ->
                    drawPseudoGround(planet, proj, lookAz, lookAlt, sunAlt, w, h)
                else -> drawGround(planet, proj, w, h, sunAlt, lookAz)
            }
            // 地平线辉光：地面与天空的接缝统一在这里柔化，任何地面绘制都不再自己画硬边。
            drawHorizonGlow(planet, sunAlt, lookAz, lookAlt, proj, w, h)
            com.skysim.app.core.Perf.mark(com.skysim.app.core.Perf.ST_GROUND, System.nanoTime() - __t)
        }
        if (fx.setting(com.skysim.app.core.EffectId.HAZE).enabled) {
            __t = System.nanoTime()
            drawHaze(planet, proj, sunAlt, fx.setting(com.skysim.app.core.EffectId.HAZE).strengthClamped(), weather, lookAz)
            com.skysim.app.core.Perf.mark(com.skysim.app.core.Perf.ST_HAZE, System.nanoTime() - __t)
        }
        if (fx.setting(com.skysim.app.core.EffectId.RAINBOW).enabled) {
            drawRainbow(planet, geo, proj, fx.setting(com.skysim.app.core.EffectId.RAINBOW).strengthClamped(), weather, dim)
        }
        if (fx.setting(com.skysim.app.core.EffectId.HALO).enabled) {
            drawHalo(planet, geo, proj, fx.setting(com.skysim.app.core.EffectId.HALO).strengthClamped(), weather.cloudCover)
        }
        if (fx.setting(com.skysim.app.core.EffectId.LIGHTNING).enabled) {
            drawLightning(planet, weather, fx.setting(com.skysim.app.core.EffectId.LIGHTNING).strengthClamped(), animSeconds)
        }
        drawCompass(proj, lookAz)
        Perf.endFrame(System.nanoTime() - frameStart)
    }
}

/**
 * 分层天空的屏幕渲染（v0.6.5）。
 *
 * v0.6.4 把天空画成 40 条离散色带、每条带内只有 9 个方位采样、带与带之间台阶跳色，
 * 真机上于是出现横向硬边和方形接缝。这里改成：
 *   1. 用一张 26 行 × 15 方位的粗网格算辐亮度（线性 RGB），网格之间线性插值；
 *   2. 逐屏幕行（每 2–4 物理像素一行）取色，行内用水平渐变按方位连续插值；
 *   3. 每行加极小幅度的 dither，压掉 8-bit 量化在夜空里形成的色带。
 * 任意相邻两行、两列之间都不再有跳变，夜间从纯黑过渡到深蓝不会出现直线边界。
 */
private val skyRaster = SkyRaster(64, 64)

private fun DrawScope.drawSkyRows(
    planet: Planet,
    sunAlt: Float,
    lookAz: Float,
    lookAlt: Float,
    geoSunAzDeg: Float,
    proj: SkyProjector,
    w: Float,
    h: Float,
    weather: WeatherState,
    fx: EffectState,
    scene: SceneOptions
) {
    val cover = weather.cloudCover * fx.setting(com.skysim.app.core.EffectId.CLOUDS).strengthClamped() * FxRules.cloudCapability(planet)
    val precip = weather.precipIntensity * fx.setting(com.skysim.app.core.EffectId.RAIN).strengthClamped() * FxRules.rainCapability(planet)

    // v0.8.2：采样密度并入画质档。低 26×15=390、中 26×25=650、高 26×37=962，
    // 三档都覆盖完整 ±180° 方位（背日侧的维纳斯带与地影不允许缺席）。
    val rows = com.skysim.app.core.SkyGrid.ROWS
    val cols = com.skysim.app.core.SkyGrid.columns(scene.quality)
    skyGrid.ensure(rows, cols)
    val altTop = com.skysim.app.core.SkyGrid.ALT_TOP_DEG
    val altBottom = com.skysim.app.core.SkyGrid.ALT_BOTTOM_DEG
    // v0.8.1：26×37 = 962 次辐亮度计算覆盖完整方位。输入量化后做缓存，
    // 时间暂停、只转视角（±1° 内）时整块网格直接复用，稳态零重算。
    val gridKey = SkyGridCache.mix(
        planet.id.hashCode(),
        SkyGridCache.q(sunAlt, 0.05f),
        SkyGridCache.q(cover, 0.02f),
        SkyGridCache.q(precip, 0.02f),
        SkyGridCache.q(geoSunAzDeg, 1f),
        SkyGridCache.q(scene.twilight.strength, 0.02f),
        scene.quality.ordinal
    )
    val flat = skyGrid.get(gridKey) { out ->
        var k = 0
        for (i in 0 until rows) {
            val alt = altTop + (altBottom - altTop) * i / (rows - 1f)
            for (j in 0 until cols) {
                // v0.7.1：方位以「太阳方位」为原点采样，网格与视线方向无关。
                // 拖动/俯仰时只有屏幕投影在变，背景不再重算 —— 天空不会黏住，也不会每帧重建。
                val az = geoSunAzDeg - 180f + 360f * j / (cols - 1f)
                Perf.countedRadiance()
                val lin = SkyModel.applyWeather(
                    SkyModel.radiance(planet, alt, sunAlt, az, geoSunAzDeg, scene.twilight),
                    planet, cover, precip, sunAlt
                )
                out[k++] = lin.r
                out[k++] = lin.g
                out[k++] = lin.b
            }
        }
    }
    fun gv(i: Int, j: Int, c: Int) = flat[(i * cols + j) * 3 + c]

    // 每一列相对太阳的方位偏移（完整 360°），投影时再叠加 太阳方位 − 视线方位
    val relAz = com.skysim.app.core.SkyGrid.relAzDegrees(cols)
    val sunMinusLook = geoSunAzDeg - lookAz
    val focal = proj.pixelsPerRadian()
    // 三档必须真的不一样：不只是像素步长，还有「最多画多少行」
    val (baseStep, rowCap) = when (scene.quality) {
        com.skysim.app.core.TerrainQuality.LOW -> 4f to 90
        com.skysim.app.core.TerrainQuality.MEDIUM -> 3f to 170
        com.skysim.app.core.TerrainQuality.HIGH -> 2f to 320
    }
    val step = maxOf(baseStep, h / rowCap.toFloat())
    // 预分配：整个循环里不再新建任何数组
    val stopX = FloatArray(cols + 2)
    val stopA = IntArray(cols + 2)
    val cleanX = FloatArray(cols + 2)
    val cleanA = IntArray(cols + 2)
    val rowsToDraw = ((h / step).toInt() + 2).coerceIn(1, 400)
    val raster = skyRaster
    raster.ensure(64, rowsToDraw)
    // v0.8.2：行高按「屏幕高度 / 行数」取，网格行与屏幕行严格一一对应
    //（原来行高用 step、位图又被拉伸到 h，垂直方向有 2% 的系统性错位）。
    val rowY = h / rowsToDraw
    for (rowIndex in 0 until rowsToDraw) {
        val y = rowIndex * rowY
        // 屏幕行 → 视线高度角：反解 atan((h/2 − y)/focal) 再加镜头俯仰
        val altDeg = lookAlt + (kotlin.math.atan(((h * 0.5f - y) / focal).toDouble()) * 180.0 / Math.PI).toFloat()
        val t = ((altTop - altDeg) / (altTop - altBottom)).coerceIn(0f, 1f) * (rows - 1)
        val i0 = t.toInt().coerceIn(0, rows - 1)
        val i1 = (i0 + 1).coerceAtMost(rows - 1)
        val f = (t - i0).coerceIn(0f, 1f)
        for (j in 0 until cols) {
            val r = gv(i0, j, 0) + (gv(i1, j, 0) - gv(i0, j, 0)) * f
            val g = gv(i0, j, 1) + (gv(i1, j, 1) - gv(i0, j, 1)) * f
            val b = gv(i0, j, 2) + (gv(i1, j, 2) - gv(i0, j, 2)) * f
            val srgb = SkyModel.toSrgb(SkyModel.softClip(Rgb(r, g, b)))
            val rr = (srgb.r.coerceIn(0f, 1f) * 255f).toInt()
            val gg = (srgb.g.coerceIn(0f, 1f) * 255f).toInt()
            val bb = (srgb.b.coerceIn(0f, 1f) * 255f).toInt()
            stopA[j] = (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
        // v0.8.2：只保留相机前方的列，首尾颜色延伸到屏幕两端（见 SkyGrid.buildStops）。
        // 0.8.1 把视线背后的列贴到屏幕边缘，那些列的颜色属于相机背后 90°–180° 的方向，
        // 太阳方位每转 1° 就换一列 ⇒ 边缘颜色逐帧跳变，这是闪烁/花屏的直接来源。
        val nClean = com.skysim.app.core.SkyGrid.buildStops(
            lookAlt, relAz, sunMinusLook, focal, w, stopA, cleanX, cleanA
        )
        if (nClean > 0) raster.fillRow(rowIndex, nClean, cleanX, cleanA, w)
    }
    raster.upload()
    drawImage(
        image = raster.image,
        dstOffset = IntOffset(0, 0),
        dstSize = IntSize(w.toInt(), h.toInt()),
        filterQuality = FilterQuality.Low
    )
}


/**
 * 地平线辉光（v0.6.6）：在天空与地面之间叠一条极淡的过渡带。
 * 位置由与地面完全相同的相机公式给出（h/2 + f·tan(pitch)），所以俯仰变化时它与地面严丝合缝。
 */
internal fun DrawScope.drawHorizonGlow(
    planet: Planet,
    sunAltDeg: Float,
    lookAz: Float,
    lookAltDeg: Float,
    proj: SkyProjector,
    w: Float,
    h: Float
) {
    val cam = com.skysim.app.core.Ground3D.Cam(lookAz, lookAltDeg, w, h, proj.pixelsPerRadian())
    val y = com.skysim.app.core.Ground3D.horizonY(cam)
    val glowH = (h * 0.045f).coerceAtLeast(5f)
    if (y + glowH < 0f || y - glowH > h) return
    val c = SkyModel.toSrgb(SkyModel.softClip(SkyModel.display(planet, 1.5f, sunAltDeg)))
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(rgb(c, 0f), rgb(c, 0.26f), rgb(c, 0.10f), rgb(c, 0f)),
            startY = y - glowH,
            endY = y + glowH
        ),
        topLeft = Offset(0f, y - glowH),
        size = Size(w, glowH * 2f)
    )
}

private fun DrawScope.drawSkyGradient(
    planet: Planet,
    sunAlt: Float,
    lookAz: Float,
    geoSunAzDeg: Float,
    proj: SkyProjector,
    w: Float,
    h: Float,
    weather: WeatherState,
    fx: EffectState,
    scene: SceneOptions
) {
    val cover = weather.cloudCover * fx.setting(com.skysim.app.core.EffectId.CLOUDS).strengthClamped() * FxRules.cloudCapability(planet)
    val precip = weather.precipIntensity * fx.setting(com.skysim.app.core.EffectId.RAIN).strengthClamped() * FxRules.rainCapability(planet)
    val stops = ArrayList<Pair<Float, Color>>()
    var alt = 90f
    while (alt >= -30f) {
        val p = proj.projectAzAlt(lookAz, alt)
        val y = if (p != null) p.y else if (alt > 0) -h else h * 2f
        val t = (y / h).coerceIn(-0.5f, 1.5f)
        val linear = SkyModel.applyWeather(
            SkyModel.radiance(planet, alt, sunAlt, lookAz, geoSunAzDeg, scene.twilight),
            planet, cover, precip, sunAlt
        )
        val c = rgb(SkyModel.toSrgb(SkyModel.softClip(linear)))
        if (stops.isEmpty() || t > stops.last().first + 0.004f) {
            stops.add(t.coerceIn(0f, 1f) to c)
        }
        alt -= if (scene.twilight.active) 2f else 4f
    }
    // 补足首尾
    if (stops.isEmpty()) return
    val sorted = stops.sortedBy { it.first }
    val first = sorted.first()
    val last = sorted.last()
    val full = ArrayList<Pair<Float, Color>>()
    if (first.first > 0f) full.add(0f to first.second)
    full.addAll(sorted)
    if (last.first < 1f) full.add(1f to last.second)
    val brush = Brush.verticalGradient(*full.toTypedArray())
    drawRect(brush = brush, topLeft = Offset(0f, 0f), size = Size(w, h))
}

private fun DrawScope.drawSun(
    planet: Planet,
    geo: SkyGeo,
    proj: SkyProjector,
    sunAlt: Float,
    alpha: Float = 1f
) {
    val pos = proj.projectAzAlt(geo.sunAzDeg, geo.sunAltDeg) ?: return
    // angularDiameter 是直径；Canvas.drawCircle 接收半径。远日行星允许太阳小于 1px，
    // 不用固定最小值把海王星的太阳错误放大成地球太阳的尺寸。
    // 上限只做「不越出画布」的安全钳位：KELT-9 b 的母星视直径约 37°，
    // 真实投影半径可达屏幕宽度的四成，绝不能再用固定像素上限把它压小。
    val discRadius = SkyModel.discRadiusPx(
        planet.sunAngularDiameterDeg, proj.pixelsPerRadian(), maxPx = size.minDimension * 2f
    )
    val transmission = SkyModel.sunTransmission(planet, sunAlt)
    val glow = SkyModel.glowTint(planet, sunAlt)
    val thickHaze = planet.aerosolTau >= 1.0
    val glowR = (discRadius * (if (thickHaze) 24f else 9f) + 26f)
        .coerceAtMost(size.minDimension * 0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                rgb(glow, (0.62f * alpha * (0.18f + 0.82f * transmission)).coerceIn(0f, 0.7f)),
                rgb(glow, (0.22f * alpha * (0.18f + 0.82f * transmission)).coerceIn(0f, 0.4f)),
                rgb(glow, 0f)
            ),
            center = pos,
            radius = glowR
        ),
        radius = glowR,
        center = pos
    )
    // 盘的颜色由 SkyModel.sunDisc 给出：色相随高度从亮白→金→橙红连续变化，
    // 亮度有下限（不低于当地天空地平线亮度的 1.2 倍），因此任何天体、任何高度角都不会变黑。
    val disc = SkyModel.toSrgb(SkyModel.sunDisc(planet, sunAlt))
    if (thickHaze) {
        // 金星与土卫六：太阳被厚雾散射成弥散亮斑。中心最亮、向外渐暗，
        // 绝不能出现 v0.6.4 那种「黑心亮圈」。
        val spot = SkyModel.hazySunSpot(planet, sunAlt).mix(disc, 0.35f)
        val spotAlpha = (0.85f * alpha).coerceIn(0f, 0.9f)
        val spotR = maxOf(discRadius * 2.2f, size.minDimension * 0.014f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    rgb(disc, spotAlpha),
                    rgb(spot, spotAlpha * 0.72f),
                    rgb(spot, spotAlpha * 0.30f),
                    rgb(spot, 0f)
                ),
                center = pos,
                radius = spotR * 3.4f
            ),
            radius = spotR * 3.4f,
            center = pos
        )
    } else {
        drawCircle(rgb(disc, alpha), radius = discRadius, center = pos)
        // 盘心高光：只在盘足够大时叠加，颜色仍由太阳本体色给出（不再用固定星色乘透射率）
        if (discRadius > 1.2f) {
            drawCircle(
                rgb(disc.mix(Rgb(1f, 1f, 1f), 0.35f), (alpha * 0.85f).coerceIn(0f, 0.85f)),
                radius = (discRadius * 0.42f).coerceAtLeast(0.12f),
                center = pos
            )
        }
    }

    // 双星系统的伴星光盘：使用独立半径与色温。当前方位是目录中的示意角偏移，
    // 后续可由双星轨道相位钩子替换为随时间变化的真实投影位置。
    val companion = StarCatalog.visualSources(planet.hostStarId, planet.sunDistanceAu)
        .drop(1).firstOrNull()
    if (companion != null) {
        val companionPos = proj.projectAzAlt(
            (geo.sunAzDeg + companion.azimuthOffsetDeg.toFloat()), geo.sunAltDeg
        )
        if (companionPos != null) {
            val companionRadius = SkyModel.discRadiusPx(
                companion.angularDiameterDeg.toFloat(), proj.pixelsPerRadian(),
                maxPx = size.minDimension * 2f
            )
            val companionColor = companion.color
            val companionAlpha = (alpha * transmission).coerceIn(0f, 1f)
            val haloRadius = (companionRadius * 8f + 18f).coerceAtMost(size.minDimension * 0.5f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        rgb(companionColor, 0.28f * companionAlpha),
                        rgb(companionColor, 0f)
                    ),
                    center = companionPos,
                    radius = haloRadius
                ),
                radius = haloRadius,
                center = companionPos
            )
            drawCircle(
                rgb(companionColor.mix(Rgb(1f, 1f, 1f), 0.45f), companionAlpha),
                radius = companionRadius,
                center = companionPos
            )
        }
    }
}

private fun DrawScope.drawStars(
    planet: Planet,
    sunAlt: Float,
    latitudeDeg: Float,
    hourAngleDeg: Float,
    proj: SkyProjector
) {
    val alpha = SkyModel.starAlpha(planet, sunAlt)
    if (alpha <= 0.01f) return
    val poleAlt = latitudeDeg
    val poleVec = toVec(0f, poleAlt.coerceIn(-89.9f, 89.9f))
    val tilt = if (latitudeDeg >= 0f) 1f else -1f
    val axis = Vec3(poleVec.x * tilt, poleVec.y * tilt, poleVec.z * tilt)
    val rotation = -hourAngleDeg
    for (s in starField) {
        val v = rotate(s.vec, axis, rotation)
        val p = proj.project(v) ?: continue
        if (p.x < -20 || p.x > size.width + 20 || p.y < -20 || p.y > size.height + 20) continue
        val r = 0.6f + s.mag * 1.5f
        drawCircle(
            Color(1f, 1f, 1f, (alpha * s.mag * 0.95f).coerceIn(0f, 1f)),
            radius = r,
            center = p
        )
    }
}

private fun DrawScope.drawMoons(
    planet: Planet,
    geo: SkyGeo,
    proj: SkyProjector,
    sunAlt: Float
) {
    if (planet.moons.isEmpty()) return
    val illum = Astro.daylightFactor(sunAlt)
    for ((i, moon) in planet.moons.withIndex()) {
        val hourAngleDeg = geo.hourAngleDeg.toDouble() + moon.phaseOffsetDeg.toDouble() + 360.0 * (i + 1) / 7.0
        val decRad = moon.declinationDeg.toDouble() * PI / 180.0
        val haRad = hourAngleDeg * PI / 180.0
        val sinAlt = cos(decRad) * cos(haRad)
        val alt = (asin(sinAlt.coerceIn(-1.0, 1.0)) * 180.0 / PI).toFloat()
        val az = Astro.normalize360(hourAngleDeg + 180.0).toFloat()
        val p = proj.projectAzAlt(az, alt) ?: continue
        val r = (moon.angularDiameterDeg * PI / 180.0 * proj.pixelsPerRadian()).toFloat()
            .coerceIn(1.2f, 40f)
        val tint = moon.tint.scale(0.30f + 0.70f * (0.20f + 0.80f * illum))
        drawCircle(rgb(tint), radius = r, center = p)
        drawCircle(Color(1f, 1f, 1f, 0.10f), radius = r * 1.6f, center = p)
    }
}

private fun DrawScope.drawSunPath(
    planet: Planet,
    latitude: Float,
    geo: SkyGeo,
    proj: SkyProjector
) {
    val path = Path()
    var started = false
    val points = ArrayList<Offset>()
    for (i in 0..144) {
        val f = i / 144.0
        val g = Astro.sunGeo(planet, latitude, 0, f, false)
        val p = proj.projectAzAlt(g.sunAzDeg, g.sunAltDeg) ?: run { started = false; null }
        if (p != null) {
            if (!started) {
                path.moveTo(p.x, p.y); started = true
            } else {
                path.lineTo(p.x, p.y)
            }
            if (i % 6 == 0) points.add(p)
        }
    }
    drawPath(path, Color(1f, 1f, 1f, 0.22f), style = Stroke(width = 1.4f))
    for (p in points) {
        drawCircle(Color(1f, 1f, 1f, 0.5f), radius = 1.8f, center = p)
    }
    // 日出 / 日落标记
    val riseP = proj.projectAzAlt(
        Astro.sunGeo(planet, latitude, 0, 0.25, false).sunAzDeg,
        Astro.sunGeo(planet, latitude, 0, 0.25, false).sunAltDeg
    )
    riseP?.let { drawCircle(Color(1f, 0.85f, 0.45f, 0.85f), radius = 3.5f, center = it) }
    val setP = proj.projectAzAlt(
        Astro.sunGeo(planet, latitude, 0, 0.75, false).sunAzDeg,
        Astro.sunGeo(planet, latitude, 0, 0.75, false).sunAltDeg
    )
    setP?.let { drawCircle(Color(1f, 0.55f, 0.30f, 0.85f), radius = 3.5f, center = it) }
}

private fun DrawScope.drawRings(
    planet: Planet,
    latitudeDeg: Float,
    proj: SkyProjector,
    sunAlt: Float
) {
    val ring = planet.ring ?: return
    val illum = Astro.daylightFactor(sunAlt)
    val rPlanet = planet.radiusKm
    val lat = latitudeDeg * PI / 180.0
    val obs = Vec3(cos(lat).toFloat(), 0f, sin(lat).toFloat()) // 观测者在本地子午线上
    val up = obs
    val east = Vec3(0f, 1f, 0f)
    val north = Vec3(
        up.y * east.z - up.z * east.y,
        up.z * east.x - up.x * east.z,
        up.x * east.y - up.y * east.x
    )
    val inner = (ring.innerRadiusKm / rPlanet).toFloat()
    val outer = (ring.outerRadiusKm / rPlanet).toFloat()
    val steps = 220
    val color = ring.tint.scale(0.25f + 0.75f * illum)
    for (i in 0..steps) {
        val a0 = 2.0 * PI * i / steps
        val a1 = 2.0 * PI * (i + 1) / steps
        fun point(r: Float, a: Double): Vec3 =
            Vec3((r * cos(a)).toFloat(), (r * sin(a)).toFloat(), 0f)
        val pIn0 = point(inner, a0); val pOut0 = point(outer, a0)
        val pIn1 = point(inner, a1); val pOut1 = point(outer, a1)
        fun dir(p: Vec3): Vec3 {
            val d = Vec3(p.x - obs.x, p.y - obs.y, p.z - obs.z)
            val len = sqrt(d.x * d.x + d.y * d.y + d.z * d.z)
            return Vec3(d.x / len, d.y / len, d.z / len)
        }
        val d0 = dir(pIn0); val d1 = dir(pOut0); val d2 = dir(pOut1); val d3 = dir(pIn1)
        fun skyDir(d: Vec3): Pair<Float, Float> {
            val alt = asin((d.x * up.x + d.y * up.y + d.z * up.z).toDouble().coerceIn(-1.0, 1.0)) * 180 / PI
            val e = d.x * east.x + d.y * east.y + d.z * east.z
            val n = d.x * north.x + d.y * north.y + d.z * north.z
            return Pair(Astro.normalize360(atan2(e.toDouble(), n.toDouble()) * 180 / PI).toFloat(), alt.toFloat())
        }
        val s0 = skyDir(d0); val s1 = skyDir(d1); val s2 = skyDir(d2); val s3 = skyDir(d3)
        val q0 = proj.projectAzAlt(s0.first, s0.second)
        val q1 = proj.projectAzAlt(s1.first, s1.second)
        val q2 = proj.projectAzAlt(s2.first, s2.second)
        val q3 = proj.projectAzAlt(s3.first, s3.second)
        if (q0 != null && q1 != null && q2 != null && q3 != null) {
            val path = Path().apply {
                moveTo(q0.x, q0.y); lineTo(q1.x, q1.y); lineTo(q2.x, q2.y); lineTo(q3.x, q3.y); close()
            }
            drawPath(path, rgb(color, ring.opacity * 0.55f))
        }
    }
}

private fun DrawScope.drawGround(
    planet: Planet,
    proj: SkyProjector,
    w: Float,
    h: Float,
    sunAlt: Float,
    lookAz: Float
) {
    // v0.8.3 修复：二维地面与伪地面、三维地面共用同一条地平线判据。
    // 地平线投影拿不到（视线夹角 > 85.42°）或已落到屏幕下缘之外时不画，不再依赖下面的逐点剔除。
    if (PseudoGroundClip.horizonTopY(lookAz, proj.lookAlt, h, proj.pixelsPerRadian()) == null) return
    val pts = ArrayList<Offset>()
    var az = lookAz - 80f
    while (az <= lookAz + 80f) {
        val p = proj.projectAzAlt(Astro.normalize360(az.toDouble()).toFloat(), 0f)
        if (p != null) pts.add(p)
        az += 2f
    }
    if (pts.size < 2) return
    val minY = pts.minOf { it.y }
    if (minY > h) return
    val ground = SkyModel.surface(planet, sunAlt)
    val path = Path()
    path.moveTo(pts.first().x, pts.first().y)
    for (i in 1 until pts.size) path.lineTo(pts[i].x, pts[i].y)
    path.lineTo(pts.last().x, h + 4f)
    path.lineTo(pts.first().x, h + 4f)
    path.close()
    drawPath(path, rgb(ground))
    // 地平线雾带
    val haze = SkyModel.horizonHaze(planet)
    if (haze > 0.05f) {
        val hz = SkyModel.toSrgb(SkyModel.display(planet, 1.5f, sunAlt))
        drawPath(path, rgb(hz, haze * 0.25f))
    }
    // 地平线过渡：不再画 1px 深色描边（v0.6.4 里那条硬横线就是它），
    // 改用一条很淡的辉光带把天空与地面接起来。
    val hz = SkyModel.toSrgb(SkyModel.display(planet, 1.5f, sunAlt))
    val glowH = (h * 0.035f).coerceAtLeast(6f)
    drawRect(
        brush = Brush.verticalGradient(
            colors = listOf(rgb(hz, 0f), rgb(hz, 0.32f), rgb(hz, 0.10f)),
            startY = minY - glowH * 0.5f,
            endY = minY + glowH
        ),
        topLeft = Offset(0f, minY - glowH * 0.5f),
        size = Size(w, glowH * 1.5f)
    )
}

private fun DrawScope.drawCompass(proj: SkyProjector, lookAz: Float) {
    val labels = when (Str.lang) {
        UiLang.ZH -> listOf(0f to "北 N", 90f to "东 E", 180f to "南 S", 270f to "西 W")
        UiLang.EN -> listOf(0f to "N", 90f to "E", 180f to "S", 270f to "W")
        UiLang.JA -> listOf(0f to "北 N", 90f to "東 E", 180f to "南 S", 270f to "西 W")
    }
    val paint = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(150, 255, 255, 255)
        textSize = 26f
        textAlign = Paint.Align.CENTER
    }
    drawIntoCanvas { canvas ->
        for ((az, text) in labels) {
            val p = proj.projectAzAlt(az, 1.5f) ?: continue
            if (p.x < 40 || p.x > size.width - 40) continue
            canvas.nativeCanvas.drawText(text, p.x, p.y - 6f, paint)
        }
    }
}


/** 复用的行渐变缓存：颜色/位置未变就复用上一帧的 Brush，稳态帧零分配。 */
/** 26×37 的全方位天空粗网格缓存（跨帧复用，键为量化输入）。 */
private val skyGrid = SkyGridCache(26, 37)

private class RowBrushEntry {
    var n = -1
    var wKey = -1
    var xs = FloatArray(0)
    var argb = IntArray(0)
    var brush: Brush? = null
}

private val rowBrushCache = HashMap<Int, RowBrushEntry>()

private fun brushForRow(idx: Int, n: Int, xs: FloatArray, cs: Array<Color>, w: Float): Brush {
    val e = rowBrushCache.getOrPut(idx) { RowBrushEntry() }
    val wKey = (w * 2f).toInt()
    if (e.brush != null && e.n == n && e.wKey == wKey) {
        var same = true
        for (i in 0 until n) {
            if (e.xs[i] != xs[i] || e.argb[i] != cs[i].toArgb()) { same = false; break }
        }
        if (same) return e.brush!!
    }
    if (e.xs.size < n) { e.xs = FloatArray(n); e.argb = IntArray(n) }
    val arr = Array(n) { Pair(xs[it], cs[it]) }
    e.n = n
    e.wKey = wKey
    System.arraycopy(xs, 0, e.xs, 0, n)
    for (i in 0 until n) e.argb[i] = cs[i].toArgb()
    val b = Brush.horizontalGradient(colorStops = arr, startX = 0f, endX = w)
    e.brush = b
    Perf.countedAlloc()
    return b
}
