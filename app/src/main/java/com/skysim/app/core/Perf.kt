package com.skysim.app.core

/**
 * v0.7.1 性能计量与缓存。
 *
 * 这里只做两件事：
 *  1. 把「上一帧算过的、这一帧没变的东西」留下来，稳态帧不再重算；
 *  2. 把每帧真正做了多少活计下来，好在 HUD 与测试里给出数字，而不是靠感觉。
 *
 * 注意：所有数字都是**模型层**耗时（JVM 上测的），不是真机 GPU 绘制耗时。
 */
object Perf {
    /** 本帧调用辐亮度模型的次数 */
    var radianceCalls: Long = 0; private set
    /** 本帧重建天空网格的次数（稳态应为 0） */
    var gridRebuilds: Long = 0; private set
    /** 本帧新建 Path/Color 以外的可复用对象次数（目标 0） */
    var allocations: Long = 0; private set
    var frames: Long = 0; private set

    private var frameStartNs = 0L
    var lastFrameMs: Double = 0.0; private set
    var lastModelMs: Double = 0.0; private set

    fun beginFrame() { frames++; radianceCalls = 0; gridRebuilds = 0; allocations = 0; frameStartNs = System.nanoTime() }
    fun endFrame(modelNs: Long) {
        lastModelMs = modelNs / 1_000_000.0
        lastFrameMs = (System.nanoTime() - frameStartNs) / 1_000_000.0
    }
    fun countedRadiance() { radianceCalls++ }
    fun countedRebuild() { gridRebuilds++ }
    fun countedAlloc() { allocations++ }
    // ---- v0.7.2：分段耗时（真机 HUD 直接显示这些数）----
    const val ST_SKY = 0
    const val ST_GROUND = 1
    const val ST_HAZE = 2
    const val ST_TREES = 3
    const val ST_OTHER = 4
    const val ST_HUD = 5
    val stageNames = arrayOf("天空", "地面", "霾带", "树木", "其它", "HUD")
    val stageMs = FloatArray(6)
    val stageMsP95 = FloatArray(6)
    private val stageHist = Array(6) { ArrayDeque<Float>() }

    fun beginStages() { for (i in 0..5) stageMs[i] = 0f }
    fun mark(stage: Int, ns: Long) {
        val ms = (ns / 1_000_000.0).toFloat()
        stageMs[stage] = ms
        val d = stageHist[stage]
        if (d.size >= 60) d.removeFirst()
        d.add(ms)
    }
    fun computeP95() {
        for (i in 0..5) {
            val d = stageHist[i]
            stageMsP95[i] = if (d.isEmpty()) 0f else d.sorted()[(d.size * 0.95).toInt().coerceAtMost(d.size - 1)]
        }
    }
    /** 帧间隔里没被任何阶段解释掉的部分：重组、位图上传、present/vsync。 */
    fun residualMs(frameMs: Double): Double {
        var sum = 0.0
        for (i in 0..5) sum += stageMs[i]
        return (frameMs - sum).coerceAtLeast(0.0)
    }

    fun stageLine(): String = (0..5).joinToString(" ") { "${stageNames[it]}${"%.1f".format(stageMs[it])}" }

    private val samples = ArrayDeque<Double>()

    /** 记录一次真实的帧间隔（真机上才有意义；JVM 测试里由微基准直接调用） */
    fun recordFrame(ms: Double) {
        if (samples.size >= 120) samples.removeFirst()
        samples.add(ms)
    }

    /** 最近 120 帧的 P95 帧间隔（毫秒）；样本不足返回 0 */
    fun p95Ms(): Double {
        if (samples.isEmpty()) return 0.0
        val sorted = samples.sorted()
        return sorted[((sorted.size - 1) * 0.95).toInt()]
    }

    fun frameCount(): Int = samples.size

    fun resetAll() { radianceCalls = 0; gridRebuilds = 0; allocations = 0; frames = 0 }
}

/** 天空粗网格缓存：键为量化后的输入，稳态命中率 100 %。 */
class SkyGridCache(private var rows: Int, private var cols: Int) {
    private var data = FloatArray(rows * cols * 3)
    private var key: Long = Long.MIN_VALUE

    /**
     * v0.8.2：画质三档的列数不同，缓存要能跟着变。
     * 只在切换画质档时调用（很少发生），切换后键失效，下一帧整片重算。
     */
    fun ensure(r: Int, c: Int) {
        if (r == rows && c == cols) return
        rows = r
        cols = c
        data = FloatArray(r * c * 3)
        key = Long.MIN_VALUE
    }

    /** 当前缓冲的采样点数（行 × 列）。 */
    val sampleCount: Int get() = rows * cols
    var hits: Long = 0; private set
    var misses: Long = 0; private set

    fun get(keyNow: Long, fill: (FloatArray) -> Unit): FloatArray {
        if (keyNow != key) {
            fill(data)
            key = keyNow
            misses++
            Perf.countedRebuild()
        } else hits++
        return data
    }

    fun invalidate() { key = Long.MIN_VALUE }

    companion object {
        /** 把一组量化后的整数混成一个键 */
        fun mix(vararg v: Int): Long {
            var h = 1125899906842597L
            for (x in v) { h = h * 31 + x }
            return h
        }
        fun q(x: Float, step: Float): Int = Math.round(x / step)
    }
}
