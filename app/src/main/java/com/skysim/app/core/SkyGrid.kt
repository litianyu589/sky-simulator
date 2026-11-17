package com.skysim.app.core

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * v0.8.2：天空网格的采样与投影规则（纯函数，可单测）。
 *
 * 三档画质对应三套采样密度，全部覆盖完整 360° 方位（太阳侧与背日侧都必须采到）：
 *   低 26×15 = 390   中 26×25 = 650   高 26×37 = 962
 * 0.8.0 的 390 个采样是「15 列 × 太阳 ±60°」，只覆盖 120°；本版低档的 390 个采样
 * 是「15 列 × ±180°」，每列 25.7°，横向更粗但背日侧不再缺席 —— 这是刻意的取舍：
 * 覆盖优先于横向分辨率（维纳斯带与地影在背日侧，缺列的代价高于粗列）。
 *
 * 硬约束（v0.7.9 定案，本版继续）：整片同时过渡、不按行/按列渐进推进。
 * 本对象只做「一次算完整张网格」的采样与「每行投影」的纯计算，不涉及任何逐帧分片。
 */
object SkyGrid {
    const val ROWS = 26
    const val ALT_TOP_DEG = 90f
    const val ALT_BOTTOM_DEG = -18f

    fun columns(quality: TerrainQuality): Int = quality.skyColumns

    /** 一档画质的辐亮度采样点数（行 × 列）。 */
    fun samples(quality: TerrainQuality): Int = ROWS * quality.skyColumns

    /** 每一列相对太阳的方位偏移（度），跨度严格 360°。 */
    fun relAzDegrees(cols: Int): FloatArray {
        require(cols >= 2) { "cols 至少 2" }
        return FloatArray(cols) { -180f + 360f * it / (cols - 1f) }
    }

    /**
     * 把一行里所有「相机前方」的列投影成停点，并把首尾颜色延伸到屏幕边缘。
     *
     * v0.8.1 把视线背后的列贴到屏幕左右边缘（`if (sinD >= 0) w else 0`），
     * 但那些列的颜色属于相机背后 90°–180° 的方向；太阳方位每转 1° 就有列跨过这条边界，
     * 边缘颜色于是逐帧跳变 —— 真机上看到的闪烁/花屏就是它。这里只保留可见列，
     * 再用最左/最右可见列的颜色把屏幕两端补齐（线性外推，连续）。
     *
     * v0.8.3 竖条纹修复版2：可见列判据加一条「只投影单调分支」。
     * 投影 `x = w/2 + f·cosP·sinD / (cos²P·cosD + sin²P)` 只通过 sinD、cosD 依赖方位，
     * 所以方位 D 与 180°−D 会落到几乎同一个 x 上；仰角越低分母 ≈ cosD，背向列被
     * `forward > 0.02` 自然挡掉，仰角一高分母退化成 ≈ sin²P（仰角 10° 起就恒 > 0.02），
     * 整圈 360° 方位被压进屏幕中间一小段并沿中线折叠，按 x 排序后相邻停点交替出现
     * 两个分支的颜色 —— 这就是高仰角下 30–200 px 宽的明暗竖条。
     * 一行屏幕像素只能承载一条单调分支，因此 |D| ≥ 90° 的列不再投影；
     * `forward > 0.02` 保留作数值保护（防止 0 附近相除）。屏幕两端的延伸色取的是
     * |D|→90° 的最外可见列，那里两个分支本来就重合，所以延伸色与修复前逐位相同。
     *
     * @return 停点数量；outX/outA 前 n 项为升序停点与 ARGB
     */
    fun buildStops(
        viewAltDeg: Float,
        relAz: FloatArray,
        sunMinusLook: Float,
        focal: Float,
        w: Float,
        colours: IntArray,
        outX: FloatArray,
        outA: IntArray
    ): Int {
        val cols = relAz.size
        val cosP = cos(viewAltDeg * PI.toFloat() / 180f)
        val sinP = sin(viewAltDeg * PI.toFloat() / 180f)
        var n = 0
        for (j in 0 until cols) {
            val dRad = (relAz[j] + sunMinusLook) * PI.toFloat() / 180f
            val sinD = sin(dRad)
            val cosD = cos(dRad)
            // v0.8.3 竖条纹修复版2：一行像素只承载单调分支（|D| < 90°）。
            // D 与 180°−D 在屏幕上几乎同样位置，两支同时投影会让相邻停点颜色来回跳。
            if (cosD <= 0f) continue
            // 与 v0.7.1 起的投影公式完全一致：fwd = 视线方向与列方向的点积
            val forward = cosP * cosD * cosP + sinP * sinP
            if (forward <= 0.02f) continue
            // 夹到一个屏幕宽度外：可见但擦过镜头边缘的列 x 会到 ±10⁴ 量级，
            // 夹住不影响线性插值，也避免浮点精度把相邻停点算成同一条。
            outX[n] = (w * 0.5f + focal * (cosP * sinD) / forward).coerceIn(-w, 2f * w)
            outA[n] = colours[j]
            n++
        }
        if (n == 0) return 0
        // 插入排序（n ≤ 37，且序列本身近似有序）
        for (a in 1 until n) {
            val kx = outX[a]
            val ka = outA[a]
            var b = a - 1
            while (b >= 0 && outX[b] > kx) {
                outX[b + 1] = outX[b]
                outA[b + 1] = outA[b]
                b--
            }
            outX[b + 1] = kx
            outA[b + 1] = ka
        }
        // 去重（相邻停点小于 0.01 px 视为同一条）
        var m = 0
        for (a in 0 until n) {
            if (m == 0 || outX[a] > outX[m - 1] + 0.01f) {
                outX[m] = outX[a]
                outA[m] = outA[a]
                m++
            }
        }
        // 覆盖到屏幕两端：左端用最左可见列的颜色，右端用最右可见列的颜色
        if (outX[0] > 0f) {
            for (i in m downTo 1) {
                outX[i] = outX[i - 1]
                outA[i] = outA[i - 1]
            }
            outX[0] = 0f
            m++
        }
        if (outX[m - 1] < w) {
            outX[m] = w
            outA[m] = outA[m - 1]
            m++
        }
        return m
    }
}
