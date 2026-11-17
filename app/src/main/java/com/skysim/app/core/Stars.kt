package com.skysim.app.core

import kotlin.math.ln
import kotlin.math.pow

/**
 * 母星光谱类型与色温 → 颜色。
 *
 * 做法：用黑体色温的公开近似算法（Tanner Helland 的 sRGB 拟合）得到该温度的黑体颜色，
 * 再除以 5772 K（太阳）的同通道结果并归一化，得到「相对太阳」的偏色。
 * 因此太阳自己恒为 (1,1,1)，其他恒星按真实色温偏红或偏蓝，不是随手给的色相。
 */
object Stars {

    enum class SpectralType(val labelZh: String, val rangeZh: String) {
        M("M 型红矮星", "2400–3700 K"),
        K("K 型橙矮星", "3700–5200 K"),
        G("G 型黄矮星（太阳型）", "5200–6000 K"),
        F("F 型白色主序星", "6000–7500 K"),
        A("A 型白色主序星", "7500–10500 K（含 A0/B9 边界）"),
        B("B 型蓝白星", "10000–30000 K")
    }

    fun spectralTypeOf(tempK: Double): SpectralType = when {
        tempK < 3700.0 -> SpectralType.M
        tempK < 5200.0 -> SpectralType.K
        tempK < 6000.0 -> SpectralType.G
        tempK < 7500.0 -> SpectralType.F
        tempK < 10500.0 -> SpectralType.A
        else -> SpectralType.B
    }

    /** 黑体颜色（sRGB 0..1，未归一化）。 */
    fun blackbody(tempK: Double): Rgb {
        val t = tempK.coerceIn(1000.0, 40000.0) / 100.0
        val r = if (t <= 66.0) 255.0 else 329.698727446 * (t - 60.0).pow(-0.1332047592)
        val g = if (t <= 66.0) 99.4708025861 * ln(t) - 161.1195681661
        else 288.1221695283 * (t - 60.0).pow(-0.0755148492)
        val b = when {
            t >= 66.0 -> 255.0
            t <= 19.0 -> 0.0
            else -> 138.5177312231 * ln(t - 10.0) - 305.0447927307
        }
        return Rgb((r / 255.0).toFloat().coerceIn(0f, 1f), (g / 255.0).toFloat().coerceIn(0f, 1f), (b / 255.0).toFloat().coerceIn(0f, 1f))
    }

    private val SUN_REF: Rgb by lazy { blackbody(5772.0) }

    /** 相对太阳的偏色（太阳 = (1,1,1)，最大分量恒为 1）。 */
    fun relativeColor(tempK: Double): Rgb {
        val c = blackbody(tempK)
        val rr = (c.r / SUN_REF.r).coerceAtLeast(1e-4f)
        val rg = (c.g / SUN_REF.g).coerceAtLeast(1e-4f)
        val rb = (c.b / SUN_REF.b).coerceAtLeast(1e-4f)
        val mx = maxOf(rr, rg, rb)
        return Rgb(rr / mx, rg / mx, rb / mx)
    }
}
