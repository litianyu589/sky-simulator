package com.skysim.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * v0.8.3 修复：伪地面 / 霾 / 二维地面 / 三维地面共用的地平线判据。
 *
 * 起因：气态行星把视线仰到 88° 时画面里冒出地面。原因是伪地面分支在拿不到地平线投影时
 * 用屏幕中线（h * 0.5）兜底，而 SkyProjector 在视线与目标方向的夹角超过约 85.42°
 * （前向余弦 dz <= 0.08）时就返回 null——仰角上限 88° 正落在这段区间，于是从 85.4° 到 88°
 * 整整 2.6° 里每帧都把半屏画成地面。
 *
 * 这里的规则只有一条：投影拿不到地平线，或者地平线已经落到屏幕下缘之外，就返回 null，
 * 调用方必须这一帧不画，不得用任何屏幕中线或比例常量兜底。判据与 SkyProjector.project()
 * 完全一致（同一个 dz <= 0.08 近平面阈值、同一个焦距换算），且不依赖 Compose，
 * 因此可以在单元测试里直接对解析值断言。
 */
object PseudoGroundClip {

    /** 与 SkyProjector.project() 的近平面阈值完全一致：前向余弦 <= 此值即不可投影。 */
    const val MIN_FORWARD_COS = 0.08f

    private const val DEG = PI.toFloat() / 180f

    /** 目标方向与视线方向的前向余弦（等于两者夹角的余弦）。 */
    fun forwardCos(lookAzDeg: Float, lookAltDeg: Float, azDeg: Float, altDeg: Float): Float {
        val la = lookAzDeg * DEG
        val lp = lookAltDeg * DEG
        val a = azDeg * DEG
        val e = altDeg * DEG
        val dx = cos(e) * cos(a)
        val dy = cos(e) * sin(a)
        val dz = sin(e)
        val fx = cos(lp) * cos(la)
        val fy = cos(lp) * sin(la)
        val fz = sin(lp)
        return dx * fx + dy * fy + dz * fz
    }

    /** 该方向是否可投影——与 SkyProjector.project() 的 dz <= 0.08 返回 null 是同一个判据。 */
    fun canProject(lookAzDeg: Float, lookAltDeg: Float, azDeg: Float, altDeg: Float): Boolean =
        forwardCos(lookAzDeg, lookAltDeg, azDeg, altDeg) > MIN_FORWARD_COS

    /**
     * 视线正前方（az = lookAz）上仰角 [altDeg] 对应的屏幕 y。
     * 不可投影时返回 null——调用方不得用常量顶替。
     */
    fun projectAltY(
        lookAzDeg: Float,
        lookAltDeg: Float,
        altDeg: Float,
        h: Float,
        focalPx: Float
    ): Float? {
        if (!canProject(lookAzDeg, lookAltDeg, lookAzDeg, altDeg)) return null
        return h * 0.5f + focalPx * tan((lookAltDeg - altDeg) * DEG)
    }

    /**
     * 地平线（仰角 0）的屏幕 y，也是任何地面分支唯一允许使用的顶边。
     *
     * 返回 null 的两种情况：
     * 1) 地平线方向不可投影（视线夹角 > 85.42°，dz <= 0.08）；
     * 2) 地平线已经落到屏幕下缘之外（y > h），此时地面整体在屏幕下方，可见面积为 0。
     *
     * 地平线跑到屏幕上缘之外（y < 0）不算不可见：那时整屏都应当是地面，返回 0，
     * 由调用方把顶边压到屏幕上缘。
     */
    fun horizonTopY(
        lookAzDeg: Float,
        lookAltDeg: Float,
        h: Float,
        focalPx: Float
    ): Float? {
        val y = projectAltY(lookAzDeg, lookAltDeg, 0f, h, focalPx) ?: return null
        if (y > h) return null
        return if (y < 0f) 0f else y
    }
}
