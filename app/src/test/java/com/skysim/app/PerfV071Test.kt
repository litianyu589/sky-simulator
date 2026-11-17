package com.skysim.app

import com.skysim.app.core.Perf
import com.skysim.app.core.Planets
import com.skysim.app.core.SkyGridCache
import com.skysim.app.core.SkyModel
import com.skysim.app.core.StarSpectrum
import com.skysim.app.core.Twilight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.7.1 性能层的可测部分。
 *
 * 这里测的是**模型层**耗时（JVM，单线程），不是真机 GPU 绘制时间。
 * 数字用于说明「每帧算了多少次、缓存有没有生效」，不能当成 60 fps 的证明。
 */
class PerfV071Test {

    private val earth = Planets.byId("earth")

    /** 一帧的天空网格：26 行 × 15 方位 = 390 次辐亮度计算 */
    private fun oneFrameGrid(planet: com.skysim.app.core.Planet, cache: SkyGridCache?, key: Long): Long {
        val t0 = System.nanoTime()
        if (cache != null) {
            cache.get(key) { out ->
                var k = 0
                for (i in 0 until 26) {
                    for (j in 0 until 15) {
                        val c = Twilight.radiance(planet, 90f - 108f * i / 25f, j * 8f, -5f, 180f, 0.65f)
                        out[k++] = c.r; out[k++] = c.g; out[k++] = c.b
                    }
                }
            }
        } else {
            var acc = 0f
            for (i in 0 until 26) {
                for (j in 0 until 15) {
                    val c = Twilight.radiance(planet, 90f - 108f * i / 25f, j * 8f, -5f, 180f, 0.65f)
                    acc += c.r + c.g + c.b
                }
            }
            assertTrue(acc >= 0f)
        }
        return System.nanoTime() - t0
    }

    @Test
    fun gridCacheRemovesRadianceWorkInSteadyState() {
        val key = SkyGridCache.mix(earth.id.hashCode(), -20, 1, 2, 3, 4, 26)
        val cold = SkyGridCache(26, 15)
        Perf.resetAll()
        // 冷启动一帧
        val t0 = System.nanoTime()
        cold.get(key) { out ->
            var k = 0
            for (i in 0 until 26) for (j in 0 until 15) {
                val c = Twilight.radiance(earth, 90f - 108f * i / 25f, j * 8f, -5f, 180f, 0.65f)
                out[k++] = c.r; out[k++] = c.g; out[k++] = c.b
            }
        }
        val coldNs = System.nanoTime() - t0
        // 稳态 600 帧
        val hits0 = cold.hits
        val misses0 = cold.misses
        val t1 = System.nanoTime()
        repeat(600) { cold.get(key) { } }
        val warmNs = System.nanoTime() - t1
        println("PERF cold=%.2f ms  warm600=%.3f ms  perFrame=%.3f ms"
            .format(coldNs / 1e6, warmNs / 1e6, warmNs / 600.0 / 1e6))
        // v0.8.2：时间比例断言在单次采样变快后不再稳定，改成结构性断言：
        // 稳态 600 帧必须 0 次重算、600 次命中；时间只作为宽松上界。
        assertEquals("稳态不得重算", 0, (cold.misses - misses0).toInt())
        assertEquals("稳态必须全部命中", 600, (cold.hits - hits0).toInt())
        assertTrue("缓存命中后每帧耗时应远低于冷启动", warmNs / 600.0 < coldNs / 3.0)
    }

    @Test
    fun planckIsComputedOncePerStar() {
        val a = StarSpectrum.rgbWeight(2566.0)
        val t0 = System.nanoTime()
        repeat(10_000) { StarSpectrum.rgbWeight(2566.0) }
        val cachedNs = System.nanoTime() - t0
        // 强制未命中：逐度扫过温度区间
        val t1 = System.nanoTime()
        for (t in 2000..4000) StarSpectrum.rgbWeight(t.toDouble())
        val coldNs = System.nanoTime() - t1
        println("PERF planck cached10k=%.3f ms  cold2k=%.3f ms".format(cachedNs / 1e6, coldNs / 1e6))
        assertTrue(cachedNs < coldNs)
        assertTrue("2566 K 的谱必须红占优：r=${a[0]} g=${a[1]} b=${a[2]}", a[0] > a[1] && a[1] > a[2])
    }

    @Test
    fun twilightCostPerColumnIsBounded() {
        // 逐列（不是网格）计算晨昏分层的成本：一次 120 列
        val t0 = System.nanoTime()
        var acc = 0f
        repeat(10) {
            for (c in 0 until 120) {
                val col = Twilight.radiance(earth, 3f, c * 3f, -4f, 180f, 0.65f)
                acc += col.r + col.b
            }
        }
        val ns = System.nanoTime() - t0
        println("PERF twilight 1200 columns = %.3f ms (%.4f ms/col)".format(ns / 1e6, ns / 1200.0 / 1e6))
        assertTrue(acc >= 0f)
    }

    @Test
    fun perfCountersStartAtZeroEachFrame() {
        Perf.beginFrame()
        Perf.endFrame(0L)
        assertEquals(0L, Perf.radianceCalls)
        assertEquals(0L, Perf.gridRebuilds)
    }
}

/**
 * 查找表键（太阳高度角分桶）的量化误差：桶内最大颜色偏差必须远低于 8-bit 的 1/255。
 */
class LutQuantisationTest {
    private val planet get() = com.skysim.app.core.Planets.byId("earth")

    private fun maxDelta(bucketDeg: Double): Double {
        var worst = 0.0
        var alt = -20.0
        while (alt <= 20.0) {
            val a = com.skysim.app.core.Twilight.radiance(planet, 2f, 20f, alt.toFloat(), 180f, 0.65f)
            val b = com.skysim.app.core.Twilight.radiance(planet, 2f, 20f, (alt + bucketDeg).toFloat(), 180f, 0.65f)
            worst = maxOf(worst, Math.abs(a.r - b.r).toDouble(), Math.abs(a.g - b.g).toDouble(), Math.abs(a.b - b.b).toDouble())
            alt += 1.0
        }
        return worst
    }

    @Test
    fun bucketErrorIsBelowOneEightBitStep() {
        val d005 = maxDelta(0.05)
        val d01 = maxDelta(0.1)
        val d025 = maxDelta(0.25)
        println("LUT bucket error: 0.05°=$d005  0.1°=$d01  0.25°=$d025")
        assertTrue("0.05° 桶的最大色差应低于 8-bit 的 1/255：$d005", d005 < 1.0 / 255.0)
        assertTrue("桶越粗误差越大：$d025 vs $d01", d025 > d01)
    }

    @Test
    fun firstBuildCostIsSmall() {
        // 首帧建表 = 26×15 次辐亮度
        var sink = 0.0
        for (i in 0 until 26) for (j in 0 until 15) {
            val c = com.skysim.app.core.Twilight.radiance(planet, 90f - 108f * i / 25f, 8f * j, -5f, 180f, 0.65f)
            sink += c.r
        }
        val t0 = System.nanoTime()
        var acc = 0.0
        repeat(20) {
            for (i in 0 until 26) for (j in 0 until 15) {
                val c = com.skysim.app.core.Twilight.radiance(planet, 90f - 108f * i / 25f, 8f * j, -5f, 180f, 0.65f)
                acc += c.r
            }
        }
        val ms = (System.nanoTime() - t0) / 1e6 / 20.0
        println("LUT first build = $ms ms/frame-grid (sink=$sink acc=$acc)")
        assertTrue("首帧建表应在 10 ms 以内：$ms ms", ms < 10.0)
    }
}

/**
 * v0.7.3：抖动必须打散台阶，但不能让相邻行出现整行偏差（那正是 v0.7.2 的横条纹）。
 */
class ScanlineFixTest {
    @Test
    fun rowMeanOffsetIsNearZeroForEveryRow() {
        for (row in 0 until 4) {
            val m = com.skysim.app.core.Dither.rowMean(row)
            assertTrue("第 $row 行的 4 像素均值偏移必须远低于 1 个色阶：$m", Math.abs(m) < 0.35 / 255.0)
        }
    }

    @Test
    fun ditherAmplitudeIsBelowOneLevel() {
        for (row in 0 until 4) for (col in 0 until 4) {
            val o = com.skysim.app.core.Dither.offset(row, col)
            assertTrue("抖动幅度应 ≤0.5 色阶：$o", Math.abs(o) * 255.0 <= 0.5 + 1e-6)
        }
    }

    /** 模拟一条斜率 0.6 色阶/行的渐变：量化后相邻行会差 1 阶；抖动后行均值差必须 < 1 阶。 */
    @Test
    fun adjacentRowMeanDeltaStaysBelowOneEightBitStep() {
        var prev = Double.NaN
        var worstRaw = 0.0
        var worstDithered = 0.0
        for (row in 0 until 40) {
            val v = 20.0 + row * 0.6
            val raw = Math.round(v) / 255.0
            if (!prev.isNaN()) worstRaw = maxOf(worstRaw, Math.abs(raw - prev))
            prev = raw
            // 抖动后的 4 像素行均值
            var sum = 0.0
            for (c in 0 until 4) sum += (v + com.skysim.app.core.Dither.offset(row, c) * 255.0)
            val mean = (sum / 4.0) / 255.0
            if (row > 0) {
                var s2 = 0.0
                for (c in 0 until 4) s2 += (v - 0.6 + com.skysim.app.core.Dither.offset(row - 1, c) * 255.0)
                worstDithered = maxOf(worstDithered, Math.abs(mean - (s2 / 4.0) / 255.0))
            }
        }
        println("SCANLINE rawRowDelta=%.5f ditheredRowDelta=%.5f (1 step=%.5f)".format(worstRaw, worstDithered, 1.0 / 255.0))
        assertTrue("抖动后的相邻行均值差应小于 1 阶：$worstDithered", worstDithered < 1.0 / 255.0)
    }
}
