package com.skysim.app.core

/**
 * 自动播放用的时间累加器。
 *
 * 设计要点：
 * 1. 按"真实帧间时间差"推进，而不是按帧计数，因此 30fps 和 120fps 下同样的真实时长会推进同样的模拟时间；
 * 2. 每一帧的小数部分会被 carry 保留，1× 倍速下即使每帧只前进 16.7ms 也不会因为取整而丢时间、越播越慢；
 * 3. 单帧真实时间差被限制在 [maxStepSeconds] 以内：应用被切到后台、系统卡顿或断点调试后回来时不会突然跳几十个小时；
 * 4. 毫秒累加做溢出保护，长时间高速播放（86400× 连播数天）不会回绕成负数。
 */
class PlaybackClock(
    startMillis: Long = 0L,
    private val maxStepSeconds: Double = 0.25
) {
    var speed: Double = 1.0

    var millis: Long = startMillis
        private set

    private var carry: Double = 0.0

    /** 手动拖动时间轴时把时钟直接对齐到目标时刻，并丢弃未结算的小数。 */
    fun setTime(m: Long) {
        millis = m
        carry = 0.0
    }

    /**
     * 推进一帧。
     * @param deltaNanos 距离上一帧的真实纳秒数（来自 withFrameNanos 的时间戳差）
     * @return 本次实际推进的模拟毫秒数
     */
    fun advance(deltaNanos: Long): Long {
        if (deltaNanos <= 0L) return 0L
        val dtSec = (deltaNanos / 1_000_000_000.0).coerceAtMost(maxStepSeconds)
        val addMs = dtSec * 1000.0 * speed + carry
        val whole = Math.floor(addMs)
        carry = (addMs - whole).coerceIn(0.0, 1.0)
        if (whole < 1.0) return 0L
        val step = whole.toLong()
        val next = millis + step
        millis = if (next < millis) Long.MAX_VALUE else next
        return step
    }

    companion object {
        /**
         * 可选的播放倍速档位（v0.9.0 扩展）。
         *
         * 慢速 0.1× / 0.25× / 0.5×：主要给地球这类「平时看着没什么」的天体，
         * 以及想慢放看云、极光、日落的场合。
         * 高速 172800× / 432000× / 864000×：给本地日极长的天体——
         * 水星本地日 15 203 808 s，864000× 下一圈约 17.6 s；
         * 金星本地日 10 087 292 s，约 11.7 s。86400× 对它们要几分钟到几十分钟，所以加档。
         *
         * 上限依据：单帧增量 = 帧间隔(≤0.25 s) × 倍速，864000× 时约 5.76 小时/帧，
         * 毫秒累加仍在 Long 安全范围内（UctDate 还会做年份钳制），不会溢出或倒退。
         */
        val SPEEDS = listOf(0.1, 0.25, 0.5, 1.0, 10.0, 60.0, 600.0, 3600.0, 86400.0, 172800.0, 432000.0, 864000.0)
        val SPEED_LABELS = listOf(
            "0.1× 慢放", "0.25×", "0.5×", "1× 原速", "10×", "60×", "600×",
            "3600×", "86400×", "172800×", "432000×", "864000×"
        )

        /**
         * 默认倍速所在下标 = 「1× 原速」在 [SPEEDS] 里的位置。
         * v0.9.2：以前 EffectPrefs 把默认值写死成 2，扩到 12 档后 2 正好是 0.5×，
         * 于是首次安装默认落在慢放档。这里改成按值查表，以后再加档也不会错位。
         */
        val DEFAULT_SPEED_INDEX: Int = SPEEDS.indexOf(1.0).coerceAtLeast(0)

        /** 倍速的人类可读形式：3600× 等于"1 秒 = 1 小时"，0.1× 等于"10 秒 = 1 秒"。 */
        fun describe(speed: Double): String {
            fun n(v: Double): String =
                if (kotlin.math.abs(v - kotlin.math.round(v)) < 1e-9) kotlin.math.round(v).toLong().toString()
                else "%.2f".format(v)
            return when {
                speed >= 86_400.0 -> "1 秒 = ${n(speed / 86_400.0)} 天"
                speed >= 3_600.0 -> "1 秒 = ${n(speed / 3_600.0)} 小时"
                speed >= 60.0 -> "1 秒 = ${n(speed / 60.0)} 分钟"
                speed >= 1.0 -> "1 秒 = ${n(speed)} 秒"
                else -> "${n(1.0 / speed)} 秒 = 1 秒"
            }
        }
    }
}
