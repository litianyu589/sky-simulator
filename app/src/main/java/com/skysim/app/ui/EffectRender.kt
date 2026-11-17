package com.skysim.app.ui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.skysim.app.core.Astro
import com.skysim.app.core.FxRules
import com.skysim.app.core.Planet
import com.skysim.app.core.PseudoGroundClip
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyGeo
import com.skysim.app.core.SurfaceKind
import com.skysim.app.core.WeatherState
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

internal fun fxColor(c: Rgb, alpha: Float = 1f): Color =
    Color(c.r.coerceIn(0f, 1f), c.g.coerceIn(0f, 1f), c.b.coerceIn(0f, 1f), alpha.coerceIn(0f, 1f))

/** 云朵团块：以天空坐标（方位/高度）给出，绘制时按时间整体漂移。 */
private data class CloudPuff(val az: Float, val alt: Float, val sizeDeg: Float, val opacity: Float)

private val CLOUD_PUFFS: List<CloudPuff> by lazy {
    val rnd = Random(20251101)
    List(120) {
        val az = rnd.nextFloat() * 360f
        // 高度分布：靠近地平线的云更多（透视上更密集），最高到 62°
        val u = rnd.nextFloat()
        val alt = 2f + 60f * (u * u * 0.65f + u * 0.35f)
        val size = 2.5f + rnd.nextFloat() * 7.5f
        val op = 0.35f + rnd.nextFloat() * 0.65f
        CloudPuff(az, alt, size, op)
    }
}

private data class RainStreak(val az: Float, val alt: Float, val speed: Float, val len: Float)

private val RAIN_STREAKS: List<RainStreak> by lazy {
    val rnd = Random(77031)
    List(130) {
        RainStreak(
            az = rnd.nextFloat() * 360f,
            alt = rnd.nextFloat() * 62f,
            speed = 26f + rnd.nextFloat() * 20f,  // 模拟秒：° / 秒
            len = 1.1f + rnd.nextFloat() * 2.4f
        )
    }
}

/**
 * 云层：团块场 + 随模拟时间整体漂移。
 * 云的颜色由太阳高度、与太阳的角距离决定（朝阳一侧更亮），夜里只留一点夜天光。
 */
internal fun DrawScope.drawClouds(
    planet: Planet,
    geo: SkyGeo,
    proj: SkyProjector,
    weather: WeatherState,
    strength: Float,
    simSeconds: Double
) {
    val cap = FxRules.cloudCapability(planet)
    val cover = weather.cloudCover * strength * cap
    if (cover <= 0.01f) return
    val sunAlt = geo.sunAltDeg
    val day = Astro.daylightFactor(sunAlt)
    val drift = (simSeconds * 0.15).toFloat() % 360f
    val unit = proj.pixelsPerRadian()
    for (puff in CLOUD_PUFFS) {
        val az = Astro.normalize360((puff.az + drift).toDouble()).toFloat()
        val p = proj.projectAzAlt(az, puff.alt) ?: continue
        val r = (puff.sizeDeg * PI / 180.0 * unit).toFloat()
        if (r < 1.2f) continue
        val dAz = ((az - geo.sunAzDeg + 540f) % 360f) - 180f
        val nearSun = cos(dAz * PI / 180.0).toFloat()          // 1 = 靠近太阳
        val lit = (0.45f + 0.55f * nearSun.coerceAtLeast(0f)) * (0.25f + 0.75f * day)
        val base = Rgb(1f, 1f, 1f).mix(planet.nightTint, 1f - day)
        val c1 = fxColor(base, 0.30f * cover * puff.opacity * (0.4f + 0.6f * lit))
        val c2 = fxColor(base, 0.34f * cover * puff.opacity * (0.5f + 0.5f * lit))
        val c3 = fxColor(base, 0.40f * cover * puff.opacity * (0.6f + 0.4f * lit))
        drawCircle(c1, radius = r, center = p)
        drawCircle(c2, radius = r * 0.74f, center = p)
        drawCircle(c3, radius = r * 0.48f, center = p)
    }
}

/** 降水：斜向下落的线段，落速与模拟时间同步（倍速播放时雨也按倍速下落）。 */
internal fun DrawScope.drawRain(
    planet: Planet,
    proj: SkyProjector,
    weather: WeatherState,
    strength: Float,
    simSeconds: Double
) {
    val cap = FxRules.rainCapability(planet)
    val amount = weather.precipIntensity * strength * cap
    if (amount <= 0.01f) return
    val wind = 0.10f // 每 1° 下落高度对应的方位偏移（风斜）
    val visible = (RAIN_STREAKS.size * amount).toInt().coerceAtLeast(6)
    val unit = proj.pixelsPerRadian()
    for (i in 0 until visible) {
        val s = RAIN_STREAKS[i]
        val cycle = 62f
        val alt = 62f - (((s.alt + s.speed * (simSeconds % 1000.0).toFloat()) % cycle))
        val a1 = alt
        val a2 = alt - s.len * (0.6f + amount * 0.8f)
        val az1 = s.az
        val az2 = s.az + (a1 - a2) * wind
        if (a1 < -2f || a2 > 64f) continue
        val p1 = proj.projectAzAlt(Astro.normalize360(az1.toDouble()).toFloat(), a1) ?: continue
        val p2 = proj.projectAzAlt(Astro.normalize360(az2.toDouble()).toFloat(), a2) ?: continue
        val col = fxColor(Rgb(0.75f, 0.85f, 1f).mix(planet.aerosolTint, 0.35f), 0.28f + 0.32f * amount)
        drawLine(col, start = p1, end = p2, strokeWidth = (1.2f + 1.6f * amount) * (unit / 900f).coerceIn(0.6f, 2.2f))
    }
}

/**
 * 把「以某个天空方向为中心、角半径 radius 的圆上角度 theta 处」换算成方位/高度。
 * 彩虹、日晕都用它保证几何正确（反日点半径 42°/51°，日晕绕日面 22°）。
 */
internal fun pointOnCone(centerAz: Double, centerAlt: Double, radiusDeg: Double, thetaDeg: Double): DoubleArray {
    val azR = centerAz * PI / 180.0
    val altR = centerAlt * PI / 180.0
    // 本地 ENU 单位向量（x 向东，y 向北，z 向上）
    val cx = cos(altR) * sin(azR)
    val cy = cos(altR) * cos(azR)
    val cz = sin(altR)
    // 与轴垂直的一组基向量
    val e1 = doubleArrayOf(-cy, cx, 0.0)          // 水平方向
    val n1 = kotlin.math.sqrt(e1[0] * e1[0] + e1[1] * e1[1])
    val ex = if (n1 < 1e-9) 1.0 else e1[0] / n1
    val ey = if (n1 < 1e-9) 0.0 else e1[1] / n1
    val e1x = ex; val e1y = ey; val e1z = 0.0
    // e2 = axis × e1
    val e2x = cy * e1z - cz * e1y
    val e2y = cz * e1x - cx * e1z
    val e2z = cx * e1y - cy * e1x
    val r = radiusDeg * PI / 180.0
    val th = thetaDeg * PI / 180.0
    val px = cos(r) * cx + sin(r) * (cos(th) * e1x + sin(th) * e2x)
    val py = cos(r) * cy + sin(r) * (cos(th) * e1y + sin(th) * e2y)
    val pz = cos(r) * cz + sin(r) * (cos(th) * e1z + sin(th) * e2z)
    val alt = kotlin.math.asin(pz.coerceIn(-1.0, 1.0)) * 180.0 / PI
    val az = (kotlin.math.atan2(px, py) * 180.0 / PI + 360.0) % 360.0
    return doubleArrayOf(az, alt)
}

/**
 * 彩虹：主虹（约 42°，内紫外红）与副虹（约 51°，颜色反向、更暗），都围绕反日点。
 * 每条色带画成一条 Path（一次绘制调用），避免每帧上千次 drawLine。
 */
internal fun DrawScope.drawRainbow(
    planet: Planet,
    geo: SkyGeo,
    proj: SkyProjector,
    strength: Float,
    weather: WeatherState,
    dim: Float
) {
    val sunAlt = geo.sunAltDeg
    if (!FxRules.rainbowVisible(planet, sunAlt, weather.precipIntensity * strength)) return
    val antiAz = Astro.normalize360(geo.sunAzDeg + 180.0)
    val antiAlt = -sunAlt.toDouble()
    // 太阳越高、虹越贴地平线、越暗；雨越大越明显
    val fade = (1f - (sunAlt / 44f)).coerceIn(0f, 1f)
    val alpha = (0.30f + 0.60f * fade) * strength *
        (0.25f + 0.75f * weather.precipIntensity) * (1f - 0.35f * dim)

    fun band(radiusDeg: Float, widthDeg: Float, col: Color) {
        val path = Path()
        var started = false
        var theta = 0.0
        while (theta <= 360.0) {
            val pt = pointOnCone(antiAz, antiAlt, radiusDeg.toDouble(), theta)
            val p = proj.projectAzAlt(pt[0].toFloat(), pt[1].toFloat())
            if (p == null) {
                started = false
            } else {
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            }
            theta += 5.0
        }
        drawPath(path, col, style = Stroke(width = widthDeg * PI.toFloat() / 180f * proj.pixelsPerRadian()))
    }

    val primary = listOf(
        Color(0.55f, 0.45f, 1f), Color(0.35f, 0.62f, 1f), Color(0.35f, 0.95f, 0.70f),
        Color(0.85f, 1f, 0.45f), Color(1f, 0.78f, 0.35f), Color(1f, 0.48f, 0.33f)
    )
    primary.forEachIndexed { i, c ->
        band(40.7f + i * 0.34f, 0.34f, c.copy(alpha = alpha * 0.55f))
    }
    primary.reversed().forEachIndexed { i, c ->
        band(50.5f + i * 0.36f, 0.38f, c.copy(alpha = alpha * 0.17f))
    }
}

/** 22° 日晕 + 幻日：需要卷云级薄云与地平线以上的太阳。 */
internal fun DrawScope.drawHalo(
    planet: Planet,
    geo: SkyGeo,
    proj: SkyProjector,
    strength: Float,
    cloudCover: Float
) {
    val sunAlt = geo.sunAltDeg
    if (!FxRules.haloVisible(planet, sunAlt, cloudCover)) return
    // 太阳越低，晕越靠地平线、越弱
    val vis = ((sunAlt - 3f) / 25f).coerceIn(0f, 1f) * (0.2f + 0.8f * strength)
    if (vis <= 0.01f) return
    val sunAz = geo.sunAzDeg

    // 22° 环
    var prev: Offset? = null
    var theta = 0f
    while (theta <= 360f) {
        val pt = pointOnCone(sunAz.toDouble(), sunAlt.toDouble(), 22.0, theta.toDouble())
        val p = proj.projectAzAlt(pt[0].toFloat(), pt[1].toFloat())
        if (p != null && prev != null) {
            drawLine(Color(1f, 0.98f, 0.9f, 0.22f * vis), start = prev!!, end = p, strokeWidth = 2.4f)
            drawLine(Color(0.85f, 0.9f, 1f, 0.10f * vis), start = prev!!, end = p, strokeWidth = 6.5f)
        }
        prev = p
        theta += 3f
    }
    // 幻日：太阳两侧 22° 处的亮斑（近似取同高度）
    for (side in listOf(-1f, 1f)) {
        val az = Astro.normalize360((sunAz + side * 22f).toDouble()).toFloat()
        val p = proj.projectAzAlt(az, sunAlt) ?: continue
        val r = size.minDimension * 0.012f
        drawCircle(Color(1f, 0.97f, 0.88f, 0.30f * vis), radius = r * 3.2f, center = p)
        drawCircle(Color(1f, 0.99f, 0.94f, 0.45f * vis), radius = r * 1.6f, center = p)
        drawCircle(Color(1f, 1f, 1f, 0.55f * vis), radius = r * 0.7f, center = p)
    }
}

/**
 * 极光：帘幕状结构，靠近极向地平线。形态随模拟时间流动（倍速播放时同步加速）。
 * 用加法混合叠加，颜色按高度分层：低层绿 → 中层青 → 高层洋红。
 */
internal fun DrawScope.drawAurora(
    planet: Planet,
    latitudeDeg: Float,
    proj: SkyProjector,
    strength: Float,
    simSeconds: Double,
    sunAlt: Float
) {
    if (!FxRules.auroraVisible(planet, latitudeDeg, sunAlt)) return
    val cap = FxRules.auroraCapability(planet)
    val baseAz = if (latitudeDeg >= 0f) 0f else 180f            // 向极方向
    val spread = (70f * (1f - (abs(latitudeDeg) - 45f) / 90f)).coerceIn(35f, 80f)
    // v0.8.3 修复：同类兜底一并移除。极光贴着地平线铺开，地平线投影拿不到时整条极光
    // 也不可能在屏幕上（baseAz 方向的视线夹角已经超过近平面判据），这一帧直接不画。
    val yHorizon = PseudoGroundClip.projectAltY(baseAz, proj.lookAlt, 0f, size.height, proj.pixelsPerRadian())
        ?: return
    val yTop = PseudoGroundClip.projectAltY(baseAz, proj.lookAlt, 42f, size.height, proj.pixelsPerRadian())
        ?: return
    val t = simSeconds
    val curtains = 5
    for (k in 0 until curtains) {
        val phase = k * 1.7
        val wobble = sin(t * 0.21 + phase) * spread * 0.35f
        val a0 = baseAz - spread + k * (2f * spread / curtains) + wobble
        val a1 = a0 + spread * 0.30f
        val path = Path()
        var started = false
        val n = 22
        var i = 0
        while (i <= n) {
            val az = a0 + (a1 - a0) * i / n
            val altTop = 18f + 16f * sin(t * 0.33 + phase + i * 0.5f).toFloat()
            val p = proj.projectAzAlt(Astro.normalize360(az.toDouble()).toFloat(), altTop)
            if (p != null) {
                if (!started) { path.moveTo(p.x, p.y); started = true } else path.lineTo(p.x, p.y)
            }
            i++
        }
        if (!started) continue
        i = n
        while (i >= 0) {
            val az = a0 + (a1 - a0) * i / n
            val p = proj.projectAzAlt(Astro.normalize360(az.toDouble()).toFloat(), 0.5f)
            if (p != null) path.lineTo(p.x, p.y)
            i--
        }
        val alpha = (0.10f + 0.30f * strength) * cap * (0.7f + 0.3f * sin(t * 0.5 + phase).toFloat())
        val brush = Brush.verticalGradient(
            colors = listOf(
                Color(0.85f, 0.30f, 0.85f, 0f),
                Color(0.80f, 0.32f, 0.88f, alpha * 0.55f),
                Color(0.35f, 0.90f, 0.95f, alpha * 0.85f),
                Color(0.30f, 1.00f, 0.55f, alpha),
                Color(0.30f, 1.00f, 0.55f, 0f)
            ),
            startY = yTop,
            endY = yHorizon
        )
        drawPath(path = path, brush = brush, blendMode = BlendMode.Plus)
    }
}

/** 霾 / 沙尘：地平线附近的整体染色，越靠近地面越浓。 */
internal fun DrawScope.drawHaze(
    planet: Planet,
    proj: SkyProjector,
    sunAlt: Float,
    strength: Float,
    weather: WeatherState,
    lookAz: Float
) {
    val cap = FxRules.hazeCapability(planet)
    val wet = 1f + 0.5f * weather.precipIntensity
    val a = (0.10f + 0.35f * strength) * cap * wet
    if (a <= 0.01f) return
    // v0.8.3 修复：地平线投影拿不到或已落到屏幕下缘之外时不画霾，不再用 size.height * 0.6f 兜底。
    val horizon = PseudoGroundClip.horizonTopY(lookAz, proj.lookAlt, size.height, proj.pixelsPerRadian())
        ?: return
    val top = (PseudoGroundClip.projectAltY(lookAz, proj.lookAlt, 18f, size.height, proj.pixelsPerRadian())
        ?: horizon).coerceAtMost(horizon)
    val tint = planet.aerosolTint.mix(planet.groundTint, 0.35f)
    val day = (0.25f + 0.75f * Astro.daylightFactor(sunAlt))
    drawRect(
        brush = Brush.verticalGradient(
            listOf(
                fxColor(tint, 0f),
                fxColor(tint, a * 0.55f * day),
                fxColor(tint, a * day)
            ),
            startY = top,
            endY = horizon
        ),
        topLeft = Offset(0f, minOf(top, horizon)),
        size = Size(size.width, abs(horizon - top) + size.height * 0.05f)
    )
}

/** 闪电：暴雨时的短促闪光（确定性时间函数，不需要随机数也不会闪烁抖动）。 */
internal fun DrawScope.drawLightning(
    planet: Planet,
    weather: WeatherState,
    strength: Float,
    simSeconds: Double
) {
    if (!FxRules.lightningVisible(planet, weather.precipLevel)) return
    val cycle = 7.3
    val phase = simSeconds % cycle
    val level = when {
        phase < 0.05 -> 1.0
        phase < 0.10 -> 0.15
        phase < 0.16 -> 0.85
        phase < 0.34 -> (0.85 * (1.0 - (phase - 0.16) / 0.18))
        else -> 0.0
    }
    if (level <= 0.01) return
    val a = (0.10f + 0.30f * strength) * level.toFloat()
    drawRect(Color(0.92f, 0.95f, 1f, a), topLeft = Offset.Zero, size = size)
    // 一条折线闪电
    if (level > 0.5) {
        val x0 = size.width * (0.25f + 0.5f * ((sin(simSeconds * 0.7).toFloat() + 1f) / 2f))
        val path = Path()
        path.moveTo(x0, size.height * 0.18f)
        var y = size.height * 0.18f
        var x = x0
        var i = 0
        while (i < 7) {
            x += if (i % 2 == 0) size.width * 0.018f else -size.width * 0.022f
            y += size.height * 0.055f
            path.lineTo(x, y)
            i++
        }
        drawPath(path, Color(1f, 1f, 1f, 0.55f * level.toFloat()), style = androidx.compose.ui.graphics.drawscope.Stroke(width = 2.2f))
    }
}

/** 地表是否为气态巨行星云顶（天空下半部应当画云而不是岩石）。 */
internal fun isCloudDeck(p: Planet): Boolean = p.surfaceKind == SurfaceKind.CLOUD_DECK
