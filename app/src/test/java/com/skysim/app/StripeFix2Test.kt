package com.skysim.app

import com.skysim.app.core.Dither
import com.skysim.app.core.Planets
import com.skysim.app.core.PseudoGroundClip
import com.skysim.app.core.Rgb
import com.skysim.app.core.SkyGrid
import com.skysim.app.core.SkyModel
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.Twilight
import com.skysim.app.core.TwilightModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.tan

/**
 * v0.8.3 竖条纹修复版2 的验收断言（单列，不改动既有 247 项）。
 *
 * 背景：0.8.3「竖条纹修复版」把可见列判据改成 cosD > 0，但同版把天空位图列数从 64
 * 改成 w/6（宽屏约 400 列），每帧填充像素 5,888 → 36,800（6.25 倍），真机上天空耗时
 * 12.3 → 257.7 ms。本版只改判据、位图列数保持 64。
 *
 * 这里的断言分四类：
 *  1) 折叠分支必须消失（用旧判据复算证明缺陷真实存在，防止有人改回去还绿）；
 *  2) 任意仰角 0–88° 全屏宽被有效列覆盖，且停点单调、无 NaN、颜色不透明；
 *  3) 整帧平均亮度锁死在基线附近（锁死「永夜」）；
 *  4) 计算量：HIGH 档仍为 962 采样，停点投影不超线性，实测耗时不超过基线 2 倍。
 */
class StripeFix2Test {

    private val w = 2400f
    private val h = 1080f
    private val focal = (w / 2f) / tan(80.0 * PI / 360.0).toFloat()
    private val rows = SkyGrid.ROWS
    private val earth = Planets.byId("earth")
    private val tw = Twilight.Setting(true, 0.65f)

    // ---------- 与 SkyView.drawSkyRows 相同的采样管线 ----------

    private fun grid(cols: Int, sunAlt: Float, sunAz: Float): FloatArray {
        val out = FloatArray(rows * cols * 3)
        var k = 0
        for (i in 0 until rows) {
            val alt = SkyGrid.ALT_TOP_DEG + (SkyGrid.ALT_BOTTOM_DEG - SkyGrid.ALT_TOP_DEG) * i / (rows - 1f)
            for (j in 0 until cols) {
                val az = sunAz - 180f + 360f * j / (cols - 1f)
                val lin = SkyModel.applyWeather(
                    SkyModel.radiance(earth, alt, sunAlt, az, sunAz, tw), earth, 0f, 0f, sunAlt
                )
                out[k++] = lin.r
                out[k++] = lin.g
                out[k++] = lin.b
            }
        }
        return out
    }

    private fun rowStops(flat: FloatArray, cols: Int, altDeg: Float): IntArray {
        val t = ((SkyGrid.ALT_TOP_DEG - altDeg) / (SkyGrid.ALT_TOP_DEG - SkyGrid.ALT_BOTTOM_DEG))
            .coerceIn(0f, 1f) * (rows - 1)
        val i0 = t.toInt().coerceIn(0, rows - 1)
        val i1 = (i0 + 1).coerceAtMost(rows - 1)
        val f = (t - i0).coerceIn(0f, 1f)
        fun gv(i: Int, j: Int, c: Int) = flat[(i * cols + j) * 3 + c]
        return IntArray(cols) { j ->
            val r = gv(i0, j, 0) + (gv(i1, j, 0) - gv(i0, j, 0)) * f
            val g = gv(i0, j, 1) + (gv(i1, j, 1) - gv(i0, j, 1)) * f
            val b = gv(i0, j, 2) + (gv(i1, j, 2) - gv(i0, j, 2)) * f
            val s = SkyModel.toSrgb(SkyModel.softClip(Rgb(r, g, b)))
            val rr = (s.r.coerceIn(0f, 1f) * 255f).toInt()
            val gg = (s.g.coerceIn(0f, 1f) * 255f).toInt()
            val bb = (s.b.coerceIn(0f, 1f) * 255f).toInt()
            (0xFF shl 24) or (rr shl 16) or (gg shl 8) or bb
        }
    }

    /** 生产路径：SkyGrid.buildStops（本版） */
    private data class StopSet(val n: Int, val x: FloatArray, val a: IntArray)

    private fun stopsNow(altDeg: Float, cols: Int, relAz: FloatArray, sunMinusLook: Float, colours: IntArray): StopSet {
        val x = FloatArray(cols + 2)
        val a = IntArray(cols + 2)
        val n = SkyGrid.buildStops(altDeg, relAz, sunMinusLook, focal, w, colours, x, a)
        return StopSet(n, x, a)
    }

    /**
     * 对照路径：v0.8.3-修复版（缺陷版）的判据 forward > 0.02，两个分支都投影。
     * 只用于证明缺陷存在与对比亮度，不参与生产代码。
     */
    private fun stopsLegacy(altDeg: Float, cols: Int, relAz: FloatArray, sunMinusLook: Float, colours: IntArray): Pair<FloatArray, IntArray> {
        val cosP = cos(altDeg * PI.toFloat() / 180f)
        val sinP = sin(altDeg * PI.toFloat() / 180f)
        val ox = FloatArray(cols + 2)
        val oa = IntArray(cols + 2)
        var n = 0
        for (j in 0 until cols) {
            val dRad = (relAz[j] + sunMinusLook) * PI.toFloat() / 180f
            val sd = sin(dRad)
            val cd = cos(dRad)
            val forward = cosP * cd * cosP + sinP * sinP
            if (forward <= 0.02f) continue
            ox[n] = (w * 0.5f + focal * (cosP * sd) / forward).coerceIn(-w, 2f * w)
            oa[n] = colours[j]
            n++
        }
        return finish(ox, oa, n)
    }

    /** 旧判据下有几个 |D| ≥ 90° 的列被投影（缺陷计数） */
    private fun countBackBranch(altDeg: Float, cols: Int, relAz: FloatArray, sunMinusLook: Float): Int {
        val cosP = cos(altDeg * PI.toFloat() / 180f)
        val sinP = sin(altDeg * PI.toFloat() / 180f)
        var c = 0
        for (j in 0 until cols) {
            val dRad = (relAz[j] + sunMinusLook) * PI.toFloat() / 180f
            val forward = cosP * cos(dRad) * cosP + sinP * sinP
            if (forward > 0.02f && cos(dRad) <= 0f) c++
        }
        return c
    }

    private fun finish(ox: FloatArray, oa: IntArray, n0: Int): Pair<FloatArray, IntArray> {
        var n = n0
        if (n == 0) return FloatArray(0) to IntArray(0)
        for (a in 1 until n) {
            val kx = ox[a]
            val ka = oa[a]
            var b = a - 1
            while (b >= 0 && ox[b] > kx) { ox[b + 1] = ox[b]; oa[b + 1] = oa[b]; b-- }
            ox[b + 1] = kx
            oa[b + 1] = ka
        }
        var m = 0
        for (a in 0 until n) {
            if (m == 0 || ox[a] > ox[m - 1] + 0.01f) { ox[m] = ox[a]; oa[m] = oa[a]; m++ }
        }
        if (ox[0] > 0f) {
            for (i in m downTo 1) { ox[i] = ox[i - 1]; oa[i] = oa[i - 1] }
            ox[0] = 0f
            m++
        }
        if (ox[m - 1] < w) { ox[m] = w; oa[m] = oa[m - 1]; m++ }
        return FloatArray(m) { ox[it] } to IntArray(m) { oa[it] }
    }

    private fun luma(c: Int): Float =
        0.2126f * ((c shr 16) and 0xFF) + 0.7152f * ((c shr 8) and 0xFF) + 0.0722f * (c and 0xFF)

    /** 与 SkyRaster.fillRow 相同的填充（位图列数 64，本版不变） */
    private fun fill(n: Int, xs: FloatArray, argb: IntArray, width: Int, row: Int): IntArray {
        val out = IntArray(width) { Int.MIN_VALUE }
        if (n <= 0) return out
        if (n == 1) { for (k in 0 until width) out[k] = argb[0]; return out }
        var seg = 0
        for (k in 0 until width) {
            val x = (k + 0.5f) / width * w
            while (seg < n - 2 && x > xs[seg + 1]) seg++
            val x0 = xs[seg]
            val x1 = xs[seg + 1]
            val t = if (x1 > x0) ((x - x0) / (x1 - x0)).coerceIn(0f, 1f) else 0f
            val c0 = argb[seg]
            val c1 = argb[seg + 1]
            val d = Dither.offset(row, k) * 255f
            val r = (((c0 shr 16) and 0xFF) * (1 - t) + ((c1 shr 16) and 0xFF) * t + d).toInt().coerceIn(0, 255)
            val g = (((c0 shr 8) and 0xFF) * (1 - t) + ((c1 shr 8) and 0xFF) * t + d).toInt().coerceIn(0, 255)
            val b = ((c0 and 0xFF) * (1 - t) + (c1 and 0xFF) * t + d).toInt().coerceIn(0, 255)
            out[k] = (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }
        return out
    }

    /** 停点颜色序列的折返次数（相邻颜色差 > 1 色阶的符号翻转数） */
    private fun zigzag(oa: IntArray, n: Int): Int {
        var zz = 0
        var last = 0
        for (i in 1 until n) {
            val d = luma(oa[i]) - luma(oa[i - 1])
            val s = when { d > 1f -> 1; d < -1f -> -1; else -> 0 }
            if (s != 0 && last != 0 && s != last) zz++
            if (s != 0) last = s
        }
        return zz
    }

    private fun rowAltitudes(quality: TerrainQuality): FloatArray {
        val (baseStep, cap) = when (quality) {
            TerrainQuality.LOW -> 4f to 90
            TerrainQuality.MEDIUM -> 3f to 170
            TerrainQuality.HIGH -> 2f to 320
        }
        val step = maxOf(baseStep, h / cap.toFloat())
        val rowsToDraw = ((h / step).toInt() + 2).coerceIn(1, 400)
        val rowY = h / rowsToDraw
        return FloatArray(rowsToDraw) { i -> i * rowY }
    }

    private fun altOfRow(lookAlt: Float, y: Float): Float =
        lookAlt + (kotlin.math.atan(((h * 0.5f - y) / focal).toDouble()) * 180.0 / PI).toFloat()

    /** 整帧平均亮度（三档画质、给定太阳高度与视线方位），按位图列数 64 统计 */
    private fun frameAvgLuma(lookAlt: Float, sunAlt: Float, sunMinusLook: Float, quality: TerrainQuality, legacy: Boolean): Double {
        val cols = quality.skyColumns
        val flat = grid(cols, sunAlt, 200f)
        val relAz = SkyGrid.relAzDegrees(cols)
        val ys = rowAltitudes(quality)
        var sum = 0.0
        for ((idx, y) in ys.withIndex()) {
            val altDeg = altOfRow(lookAlt, y)
            val stops = rowStops(flat, cols, altDeg)
            val ox = FloatArray(cols + 2)
            val oa = IntArray(cols + 2)
            val n = if (legacy) {
                val p = stopsLegacy(altDeg, cols, relAz, sunMinusLook, stops)
                val (fx, fa) = p
                for (i in fx.indices) { ox[i] = fx[i]; oa[i] = fa[i] }
                fx.size
            } else {
                SkyGrid.buildStops(altDeg, relAz, sunMinusLook, focal, w, stops, ox, oa)
            }
            val px = fill(n, ox, oa, 64, idx)
            var s = 0f
            for (c in px) s += luma(c)
            sum += s / px.size
        }
        return sum / ys.size
    }

    // ---------- 1. 折叠分支必须消失 ----------

    @Test
    fun foldingBranchIsNotProjected() {
        var legacyBack = 0
        var maxLegacyBack = 0
        for (q in TerrainQuality.values()) {
            val cols = q.skyColumns
            val flat = grid(cols, 30f, 200f)
            val relAz = SkyGrid.relAzDegrees(cols)
            for (lookAlt in 0..88 step 4) {
                for (dAz in intArrayOf(0, 45, 90, 135, 180)) {
                    val stops = rowStops(flat, cols, lookAlt.toFloat())
                    val ss = stopsNow(lookAlt.toFloat(), cols, relAz, dAz.toFloat(), stops)
                    val nNow = ss.n
                    // 生产判据：投影列数必须等于「|D|<90° 且 forward>0.02」的解析列数
                    val cosP = cos(lookAlt * PI.toFloat() / 180f)
                    val sinP = sin(lookAlt * PI.toFloat() / 180f)
                    var analytic = 0
                    for (j in 0 until cols) {
                        val dRad = (relAz[j] + dAz) * PI.toFloat() / 180f
                        if (cos(dRad) <= 0f) continue
                        if (cosP * cos(dRad) * cosP + sinP * sinP <= 0.02f) continue
                        analytic++
                    }
                    // 停点数 = 解析列数 + 必要的两端延伸（最多 2）
                    // 停点数不得超过「单调分支列数 + 两端延伸」：一旦有人把折叠分支放回来，
                    // 高仰角时 nNow 会接近 2×analytic，这条会立刻变红。
                    assertTrue(
                        "q=${q.name} alt=$lookAlt dAz=$dAz 停点数 $nNow 超过单调分支上界 $analytic+2",
                        nNow in 2..(analytic + 2)
                    )
                    for (i in 1 until nNow) {
                        assertTrue("停点必须严格递增 alt=$lookAlt dAz=$dAz i=$i", ss.x[i] > ss.x[i - 1])
                        assertTrue("停点不得为 NaN", ss.x[i].isFinite())
                    }
                    val back = countBackBranch(lookAlt.toFloat(), cols, relAz, dAz.toFloat())
                    legacyBack += back
                    if (back > maxLegacyBack) maxLegacyBack = back
                }
            }
        }
        // 旧判据在同一批状态下确实会投影折叠分支（缺陷真实存在），且高仰角时多达半数以上列
        assertTrue("旧判据必须能复现折叠分支，否则这条断言失去意义", legacyBack > 0)
        assertTrue("高仰角时旧判据投影的折叠列应达到 7 列以上，实测 $maxLegacyBack", maxLegacyBack >= 7)
    }

    // ---------- 2. 全屏宽覆盖、无未覆盖像素列 ----------

    @Test
    fun fullWidthCoverageAtEveryAltitude() {
        for (q in TerrainQuality.values()) {
            val cols = q.skyColumns
            val flat = grid(cols, 20f, 200f)
            val relAz = SkyGrid.relAzDegrees(cols)
            for (lookAlt in 0..88 step 2) {
                for (dAz in intArrayOf(0, 90, 180, 270)) {
                    val stops = rowStops(flat, cols, lookAlt.toFloat())
                    val ss = stopsNow(lookAlt.toFloat(), cols, relAz, dAz.toFloat(), stops)
                    val n = ss.n
                    val x = ss.x
                    val a = ss.a
                    assertTrue("q=${q.name} alt=$lookAlt dAz=$dAz 停点太少 $n", n >= 2)
                    assertTrue("左端必须覆盖到 x=0（alt=$lookAlt dAz=$dAz, x0=${x[0]}）", x[0] <= 0.01f)
                    assertTrue("右端必须覆盖到 x=w（alt=$lookAlt dAz=$dAz, xn=${x[n - 1]}）", x[n - 1] >= w - 0.01f)
                    for (i in 0 until n) {
                        assertEquals("停点颜色必须不透明 alt=$lookAlt", 0xFF, (a[i] ushr 24) and 0xFF)
                    }
                    // 逐像素：64 列位图的每一个像素都必须落在某个停点区间内、且不透明
                    val px = fill(n, x, a, 64, 0)
                    for (c in px) {
                        assertEquals("存在未填充像素列 alt=$lookAlt dAz=$dAz", 0xFF, (c ushr 24) and 0xFF)
                    }
                }
            }
        }
    }

    // ---------- 3. 整帧平均亮度锁死（防「永夜」） ----------

    @Test
    fun frameBrightnessStaysAtBaseline() {
        var worst = 0.0
        var worstTag = ""
        var worstDark = 0.0
        for (q in TerrainQuality.values()) {
            for (lookAlt in floatArrayOf(0f, 30f, 56f, 70f, 80f, 84f, 88f)) {
                for (sunAlt in floatArrayOf(60f, 45f, 20f, 8f, 2f, -4f, -12f)) {
                    for (dAz in floatArrayOf(0f, 180f)) {
                        val now = frameAvgLuma(lookAlt, sunAlt, dAz, q, legacy = false)
                        val base = frameAvgLuma(lookAlt, sunAlt, dAz, q, legacy = true)
                        val d = abs(now - base)
                        if (d > worst) { worst = d; worstTag = "q=${q.name} alt=$lookAlt sun=$sunAlt dAz=$dAz now=$now base=$base" }
                        if (base - now > worstDark) worstDark = base - now
                    }
                }
            }
        }
        // 实测最差约 4.5/255（1.8%）；阈值取 6/255（2.4%）留余量，并且方向也要受限：
        // 不允许「修复后比基线暗 2.4% 以上」，这正是永夜的量级红线。
        assertTrue("整帧平均亮度偏离基线过大：$worstTag", worst <= 6.0)
        assertTrue("修复后整帧不应比基线更暗超过 6/255（永夜红线），实测 $worstDark", worstDark <= 6.0)
    }

    // ---------- 4. 高仰角行内不再折返 ----------

    @Test
    fun noColorReversalAboveFiftySixDegrees() {
        var worstNow = 0
        var worstLegacy = 0
        for (q in TerrainQuality.values()) {
            val cols = q.skyColumns
            val flat = grid(cols, 30f, 200f)
            val relAz = SkyGrid.relAzDegrees(cols)
            for (lookAlt in 56..88 step 2) {
                for (dAz in intArrayOf(0, 90, 180)) {
                    val stops = rowStops(flat, cols, lookAlt.toFloat())
                    val ss = stopsNow(lookAlt.toFloat(), cols, relAz, dAz.toFloat(), stops)
                    val zz = zigzag(ss.a, ss.n)
                    if (zz > worstNow) worstNow = zz
                    val (lx, la) = stopsLegacy(lookAlt.toFloat(), cols, relAz, dAz.toFloat(), stops)
                    val zzl = zigzag(la, lx.size)
                    if (zzl > worstLegacy) worstLegacy = zzl
                }
            }
        }
        assertTrue("旧判据在 56–88° 必须出现明显折返（缺陷证据），实测 $worstLegacy", worstLegacy >= 5)
        assertTrue("修复后 56–88° 行内折返应 ≤ 2，实测 $worstNow", worstNow <= 2)
    }

    // ---------- 5. 计算量与采样密度不变 ----------

    @Test
    fun samplingAndCostStayAtBaseline() {
        assertEquals("HIGH 档必须仍是 962 采样点", 962, SkyGrid.samples(TerrainQuality.HIGH))
        assertEquals("LOW 档必须仍是 390 采样点", 390, SkyGrid.samples(TerrainQuality.LOW))
        assertEquals("MEDIUM 档必须仍是 650 采样点", 650, SkyGrid.samples(TerrainQuality.MEDIUM))
        // 停点投影的评估列数 = cols（不允许多趟扫描），停点数 ≤ cols + 2
        val cols = 37
        val flat = grid(cols, 30f, 200f)
        val relAz = SkyGrid.relAzDegrees(cols)
        for (lookAlt in 0..88 step 8) {
            val stops = rowStops(flat, cols, lookAlt.toFloat())
            val ss = stopsNow(lookAlt.toFloat(), cols, relAz, 0f, stops)
            assertTrue("停点数 ${ss.n} 超出线性上界 ${cols + 2}", ss.n <= cols + 2)
        }
        // 实测耗时：取中位数，生产实现不得超过旧判据的 2 倍
        fun median(xs: List<Double>): Double = xs.sorted()[xs.size / 2]
        val stops = rowStops(flat, cols, 70f)
        fun timeNow(): Double {
            val ox = FloatArray(cols + 2)
            val oa = IntArray(cols + 2)
            val t0 = System.nanoTime()
            repeat(4000) { SkyGrid.buildStops(70f, relAz, 0f, focal, w, stops, ox, oa) }
            return (System.nanoTime() - t0) / 4000.0
        }
        fun timeLegacy(): Double {
            val ox = FloatArray(cols + 2)
            val oa = IntArray(cols + 2)
            val t0 = System.nanoTime()
            repeat(4000) { stopsLegacy(70f, cols, relAz, 0f, stops) }
            return (System.nanoTime() - t0) / 4000.0
        }
        repeat(2) { timeNow(); timeLegacy() }
        val now = median(List(7) { timeNow() })
        val base = median(List(7) { timeLegacy() })
        assertTrue("停点投影耗时不得超过基线 2 倍：now=${now}ns base=${base}ns", now <= base * 2.0)
    }

    // ---------- 6. 锚点与伪地面修复不得被这次改动带走 ----------

    @Test
    fun anchorsAndPseudoGroundUntouched() {
        assertEquals(16.0, Twilight.EARTH_SCATTER_ALT_DEG, 0.0)
        assertEquals(20.9, Twilight.scatteringAltitudeDeg(Planets.byId("mars")), 0.05)
        assertEquals(29.9, Twilight.scatteringAltitudeDeg(Planets.byId("venus")), 0.05)
        val belt = Twilight.beltColour(earth)
        assertEquals(0.7560f, belt.r, 0.0005f)
        assertEquals(0.4289f, belt.g, 0.0005f)
        assertEquals(0.5809f, belt.b, 0.0005f)
        val st = TwilightModel.stage(earth)
        assertEquals(6.0, st.civilDeg, 0.0001)
        assertEquals(12.0, st.nauticalDeg, 0.0001)
        assertEquals(18.0, st.astronomicalDeg, 0.0001)
        // 伪地面修复（0.8.3-修复版）：86–88° 不可画，5° 可画
        assertTrue("86° 伪地面必须仍不可绘制", !PseudoGroundClip.canProject(0f, 86f, 0f, 0f))
        assertTrue("88° 伪地面必须仍不可绘制", !PseudoGroundClip.canProject(0f, 88f, 0f, 0f))
        assertTrue("5° 伪地面必须仍可绘制", PseudoGroundClip.canProject(0f, 5f, 0f, 0f))
    }
}
