package com.skysim.app.core

/**
 * v0.8.2：画质三档的自动升降（带回滞）。
 *
 * v0.7.1–v0.8.1 只有降档：连续 3 帧超过 20 ms 就降一档，降到底就永久停在低档。
 * 一次偶发尖峰（例如刚进全屏、刚切天体）会把画质钉死。本版补上回升：
 *
 * - 降档：连续 [dropFrames] 帧 > [slowMs]，降一档（高→中→低）。
 * - 升档：连续 [upgradeCalmSeconds] 秒没有任何超时帧，升一档（低→中→高）。
 * - 回滞锁：升档后 [relockSeconds] 秒内又出现超时降档，视为「升不上去」，上锁不再自动升；
 *   手动改画质可解锁。
 * - 手动：任何一次手动选择画质都退出自动逻辑（[manual]），不会再被自动改回去。
 *
 * 纯状态机，不依赖 Android，可单测。
 */
class QualityGovernor(
    private val slowMs: Double = 20.0,
    private val dropFrames: Int = 3,
    private val upgradeCalmSeconds: Double = 12.0,
    private val relockSeconds: Double = 20.0
) {
    var auto: Boolean = true
        private set
    var locked: Boolean = false
        private set
    var lastReason: String = "启动"
        private set
    var drops: Int = 0
        private set
    var upgrades: Int = 0
        private set

    private var over = 0
    private var calm = 0.0
    private var sinceUpgrade = Double.MAX_VALUE

    /** 手动改画质：退出自动逻辑。 */
    fun manual() {
        auto = false
        locked = false
        over = 0
        calm = 0.0
        lastReason = "手动接管"
    }

    /** 切回自动：清空回滞状态。 */
    fun enableAuto() {
        auto = true
        locked = false
        over = 0
        calm = 0.0
        sinceUpgrade = Double.MAX_VALUE
        lastReason = "恢复自动"
    }

    /** 每绘制帧调用一次；返回本帧之后的画质档。 */
    fun frame(dtSeconds: Double, frameMs: Double, current: TerrainQuality): TerrainQuality {
        if (!auto) return current
        val dt = dtSeconds.coerceIn(0.0, 1.0)
        sinceUpgrade += dt
        if (frameMs > slowMs) {
            over++
            calm = 0.0
            if (over < dropFrames || current == TerrainQuality.LOW) return current
            over = 0
            val next = if (current == TerrainQuality.HIGH) TerrainQuality.MEDIUM else TerrainQuality.LOW
            drops++
            if (sinceUpgrade < relockSeconds) {
                locked = true
                lastReason = "升档后 ${sinceUpgrade.toInt()} s 内又掉帧，锁定自动升档"
            } else {
                lastReason = "连续 $dropFrames 帧 > ${slowMs.toInt()} ms"
            }
            return next
        }
        if (over > 0) over--
        calm += dt
        if (!locked && current != TerrainQuality.HIGH && calm >= upgradeCalmSeconds) {
            calm = 0.0
            sinceUpgrade = 0.0
            upgrades++
            lastReason = "连续 ${upgradeCalmSeconds.toInt()} s 无超时帧"
            return if (current == TerrainQuality.LOW) TerrainQuality.MEDIUM else TerrainQuality.HIGH
        }
        return current
    }
}
