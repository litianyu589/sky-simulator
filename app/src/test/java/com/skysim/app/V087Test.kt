package com.skysim.app

import com.skysim.app.core.PlaybackClock
import com.skysim.app.core.TimeAxis
import com.skysim.app.core.TimeSystem
import com.skysim.app.core.UctDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.8.7：点播放后时间轴掉到 1970 的阻断级缺陷回归护栏。
 *
 * 根因（已在代码里核实）：TimeAxis.advanceFrame 以前返回**本帧推进量**（step），
 * 而 MainScreen 的播放循环写的是 `uctMillis = timeAxis.advanceFrame(...)`，把增量当成了绝对时刻。
 * 于是点播放后界面显示的是「一帧推进了多少毫秒」——1× 时 16 ms、3600× 时约一分钟，
 * 换到公历就是 1970-01-01 00:00:16 / 00:01:00 一带，随帧间隔抖动，看起来像「涨一点又落回来」。
 * 内部时钟 clock.millis 一直是对的，所以旧测试只看 axis.millis 时全绿。
 *
 * 本文件把「UI 侧真正拿到的值」写成断言锁住，并把 MainScreen 的循环语义复刻成 playFrames()。
 */
class V087Test {

    private val frameNanos = 16_666_667L // 60 fps 的一帧

    /** 复刻 MainScreen 的播放循环：起播时把时钟对齐当前时刻，之后每帧写回 advanceFrame 的返回值。 */
    private fun playFrames(axis: TimeAxis, frames: Int, speed: Double): List<Long> {
        val shown = ArrayList<Long>(frames)
        axis.seekTo(axis.millis)
        repeat(frames) {
            shown.add(axis.advanceFrame(frameNanos, speed))
        }
        return shown
    }

    /** 2000-01-01 的毫秒值。1970 附近（≤ 十亿级）的数字都远小于这个下限，正常时刻都大于它。 */
    private val saneFloor = 946_684_800_000L

    @Test
    fun firstFrameFromDefaultAnchorIsAnchorPlusOneFrame() {
        val axis = TimeAxis(UctDate.DEFAULT_MILLIS)
        val anchor = axis.millis
        assertEquals("默认锚点必须是 2025-12-01", 2025, TimeSystem.uctStamp(anchor / 1000L).year)
        val shown = playFrames(axis, 1, 1.0)
        val value = shown.single()
        assertTrue("返回值必须是绝对时刻，不是本帧增量：$value", value > saneFloor)
        assertTrue("一帧之后必须严格递增", value > anchor)
        assertEquals("1× 播放一帧的绝对时刻 = 锚点 + 帧增量", anchor + axis.lastStepMillis, value)
        assertEquals("1× 60fps 一帧 = 16 ms", 16.0, axis.lastStepMillis.toDouble(), 1.0)
        assertEquals("界面时间必须等于时钟内部时刻", axis.millis, value)
    }

    @Test
    fun playingFromARealInstantNeverLandsNearTheEpoch() {
        val start = com.skysim.app.core.Cal.toEpochDay(2026, 11, 8) * 86_400_000L + 12 * 3_600_000L
        val axis = TimeAxis(start)
        for (speed in listOf(1.0, 10.0, 60.0, 600.0, 3600.0, 86400.0)) {
            val shown = playFrames(axis, 60, speed)
            for (v in shown) {
                assertTrue("${speed}× 播放时出现了 epoch 附近的取值：$v", v > saneFloor)
                assertTrue("${speed}× 播放时时间回退了：$v", v >= start)
            }
            assertTrue("${speed}× 播放 60 帧必须严格推进", shown.last() > shown.first())
            axis.seekTo(start)
        }
    }

    @Test
    fun highSpeedNeverShowsAFrameAsAbsoluteTime() {
        val axis = TimeAxis(com.skysim.app.core.Cal.toEpochDay(2026, 11, 8) * 86_400_000L)
        val shown = playFrames(axis, 30, 3600.0)
        for (v in shown) {
            assertTrue("3600× 下返回值仍是绝对时刻：$v", v > saneFloor)
        }
        // 旧缺陷的特征值：本帧增量在 3600× 下约 60 秒，远小于 saneFloor；这里显式锁住「不能是这个量级」
        assertTrue("本帧增量必须单独取 lastStepMillis", axis.lastStepMillis in 1..1_000_000L)
        assertTrue("本帧增量绝不能被当成绝对时刻：${axis.lastStepMillis}", axis.lastStepMillis < saneFloor)
    }

    @Test
    fun fiftyPausePlayCyclesNeverGoBackwardsOrDrift() {
        val start = com.skysim.app.core.Cal.toEpochDay(2026, 11, 8) * 86_400_000L
        val axis = TimeAxis(start)
        var previous = axis.millis
        var total = 0L
        repeat(50) {
            // 每次「播放」都像 MainScreen 那样先对齐一次，模拟 key 变化导致的重启
            axis.seekTo(axis.millis)
            repeat(5) {
                val v = axis.advanceFrame(frameNanos, 60.0)
                total += axis.lastStepMillis
                assertTrue("第 $it 轮出现回退：$v <= $previous", v > previous)
                previous = v
            }
            // 暂停时什么都不写，界面停在上一帧的值
            assertEquals("暂停后界面值必须停在时钟值", axis.millis, previous)
        }
        assertEquals("50 轮 × 5 帧不得有任何累积漂移", start + total, axis.millis)
        assertTrue("最终时刻必须远在 2026 年之后而不是 epoch 附近", axis.millis > start)
    }

    @Test
    fun manualSeekDuringPlaybackStillWinsAndKeepsAbsoluteTime() {
        val axis = TimeAxis(UctDate.DEFAULT_MILLIS)
        playFrames(axis, 3, 600.0)
        val target = com.skysim.app.core.Cal.toEpochDay(2026, 3, 1) * 86_400_000L + 12 * 3_600_000L
        axis.seekTo(target)
        val shown = playFrames(axis, 3, 600.0)
        assertTrue("seek 之后必须从新目标继续", shown.first() > target)
        for (v in shown) assertTrue("seek 之后不得回到 epoch：$v", v > saneFloor)
        assertTrue("seek 之后不得回到旧锚点", abs(shown.first() - target - 10_000L) <= 200L)
    }

    @Test
    fun speedChangeMidPlaybackKeepsTimeContinuous() {
        val start = com.skysim.app.core.Cal.toEpochDay(2026, 11, 8) * 86_400_000L
        val axis = TimeAxis(start)
        axis.seekTo(axis.millis)
        // v0.9.0：倍速档位表扩展到 12 档，这里按值取「1×」而不是按下标，避免档位一改就测到别的倍速
        axis.advanceFrame(frameNanos, 1.0)
        val afterSlow = axis.millis
        axis.advanceFrame(frameNanos, 86_400.0)
        assertTrue("切到 86400× 之后必须继续前进，不能跳回 epoch", axis.millis > afterSlow)
        // 86400× × 16.7 ms 一帧 = 1,440,000 ms（24 分钟）；maxStepSeconds 只限制真实帧差超过 0.25 s 的情况
        assertEquals("86400× 下每帧约 24 分钟", 1_440_000.0, axis.lastStepMillis.toDouble(), 2_000.0)
    }
}
