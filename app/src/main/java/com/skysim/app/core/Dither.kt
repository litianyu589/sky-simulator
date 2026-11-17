package com.skysim.app.core

/**
 * v0.7.3：4×4 有序抖动（Bayer），用来消掉天空渐变上的 8-bit 分级台阶。
 *
 * 为什么是「有序」而不是随机：随机抖动（蓝噪声）在低分辨率位图放大后会变成色块；
 * 4×4 Bayer 在放大后仍是规则细纹，且行均值稳定 —— 关键是**逐行均值不再随行号跳变**，
 * 这正是 v0.7.2 出现横向扫描线的原因（当时每行加的是同一个整行常数抖动，行与行相差 1–3 个色阶，
 * 一整行同色偏差就成了肉眼可见的横条纹）。
 */
object Dither {
    /** 4×4 Bayer 矩阵，0..15 */
    private val BAYER4 = intArrayOf(
        0, 8, 2, 10,
        12, 4, 14, 6,
        3, 11, 1, 9,
        15, 7, 13, 5
    )

    /** 幅度：±0.5 个 8-bit 色阶。低于阈值可见差（约 1 阶），但足以打散同为 1 阶的量化台阶。 */
    const val AMPLITUDE_LEVELS = 0.5f

    /** 返回 −0.5..+0.5 个色阶的偏移，单位是 0..1 的颜色值。 */
    fun offset(row: Int, col: Int): Float {
        val b = BAYER4[((row and 3) shl 2) or (col and 3)]
        return ((b + 0.5f) / 16f - 0.5f) * AMPLITUDE_LEVELS / 255f
    }

    /** 一行（4 像素窗口）的均值偏移，测试用：应当接近 0，且与行号无关。 */
    fun rowMean(row: Int): Double {
        var s = 0.0
        for (c in 0 until 4) s += offset(row, c)
        return s / 4.0
    }
}
