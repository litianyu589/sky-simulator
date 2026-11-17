package com.skysim.app.core

/**
 * 光源光谱：把「天空为什么是蓝的」从写死的色值改成**这颗恒星的颜色**。
 *
 * 瑞利散射的 1/λ⁴ 只决定「短波散射得多」，真正进入眼睛的是恒星在该波长的辐射。
 * 这里用普朗克黑体谱在三个采样波长上的能量比，并以太阳（5772 K）归一：
 * G2V 星的权重恰好是 1.0（所以 v0.6.9 的天空一个像素都不变），
 * M 型星（TRAPPIST-1，约 2566 K）的蓝光权重会掉到百分之一量级，天空自然偏橙红而不是蓝。
 */
object StarSpectrum {

    const val REFERENCE_K = 5772.0
    val LAMBDAS_NM = doubleArrayOf(610.0, 550.0, 460.0)

    /** 普朗克谱辐射（相对值，单位无关）。 */
    fun planck(lambdaNm: Double, tK: Double): Double {
        if (tK <= 0.0) return 0.0
        val l = lambdaNm * 1e-9
        val h = 6.62607015e-34
        val c = 2.99792458e8
        val kB = 1.380649e-23
        val num = 2.0 * h * c * c / (l * l * l * l * l)
        val x = h * c / (l * kB * tK)
        if (x > 700.0) return 0.0          // 指数溢出保护
        return num / Math.expm1(x)
    }

    /** 三通道权重（以 5772 K 归一）。 */
    /** 参考谱（5772 K）只算一次：v0.7.1 之前每次调用都重算 3 次普朗克积分。 */
    private val REF = DoubleArray(3) { planck(LAMBDAS_NM[it], REFERENCE_K) }
    private val weightCache = HashMap<Long, DoubleArray>()

    /** 光谱权重（只读，调用方不得修改返回数组）。按整数开尔文缓存。 */
    fun rgbWeight(tK: Double): DoubleArray {
        val key = Math.round(tK)
        return weightCache.getOrPut(key) {
            DoubleArray(3) { planck(LAMBDAS_NM[it], key.toDouble()) / REF[it] }
        }
    }

    /** 句子说明，供 UI / 文档引用。 */
    fun describe(tK: Double): String = when {
        tK >= 5000 -> "G/K 型（${tK.toInt()} K）：短波充足，蓝天成立"
        tK >= 3000 -> "M 型（${tK.toInt()} K）：蓝光弱，天空偏暗橙"
        tK > 0 -> "晚期 M/褐矮星（${tK.toInt()} K）：几乎没有蓝光"
        else -> "温度未知：按太阳谱处理"
    }
}
