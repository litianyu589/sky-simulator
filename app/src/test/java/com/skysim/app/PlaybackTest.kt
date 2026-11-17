package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.PlaybackClock
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PlaybackTest {

    /** 同一段真实时长、不同帧率，推进的模拟时间必须一致（按时间差累加，而非按帧计数）。 */
    @Test
    fun frameRateIndependence() {
        val realSeconds = 2.0
        val speed = 600.0
        fun simulate(fps: Int): Long {
            val clock = PlaybackClock(0L)
            clock.speed = speed
            val frameNanos = (1_000_000_000.0 / fps).toLong()
            val frames = (realSeconds * fps).toInt()
            repeat(frames) { clock.advance(frameNanos) }
            return clock.millis
        }
        val at60 = simulate(60)
        val at30 = simulate(30)
        val at120 = simulate(120)
        val expected = realSeconds * speed * 1000.0
        assertTrue("60fps 偏差过大: $at60", abs(at60 - expected) < 5.0)
        assertEquals("30fps 与 60fps 应一致", at60.toDouble(), at30.toDouble(), 5.0)
        assertEquals("120fps 与 60fps 应一致", at60.toDouble(), at120.toDouble(), 5.0)
    }

    /** 亚毫秒的小数部分必须被 carry 保留，1× 播放 10 秒不能因为逐帧取整而丢时间。 */
    @Test
    fun subMillisecondCarry() {
        val clock = PlaybackClock(0L)
        clock.speed = 1.0
        repeat(600) { clock.advance(16_666_667L) } // 60fps × 10 秒
        assertTrue("10 秒播放应推进约 10000ms，实际 ${clock.millis}", abs(clock.millis - 10000L) < 20L)
    }

    /** 卡顿/切后台后的超大帧差要被钳制，避免时间瞬移。 */
    @Test
    fun stallIsClamped() {
        val clock = PlaybackClock(0L)
        clock.speed = 3600.0
        clock.advance(5_000_000_000L) // 假装卡了 5 秒
        assertTrue("单帧最多推进 0.25 秒真实时间 × 3600 = 900 秒，实际 ${clock.millis}", clock.millis <= 900_000L)
        assertTrue(clock.millis > 0L)
    }

    /** 倍速中途切换不会产生跳变，切换后按新倍速推进。 */
    @Test
    fun speedSwitchIsContinuous() {
        val clock = PlaybackClock(0L)
        clock.speed = 60.0
        repeat(60) { clock.advance(16_666_667L) } // 1 秒 × 60x ≈ 60000ms
        val afterFirst = clock.millis
        assertTrue(abs(afterFirst - 60_000L) < 20L)
        clock.speed = 600.0
        repeat(60) { clock.advance(16_666_667L) } // 1 秒 × 600x ≈ 600000ms
        assertTrue("切换倍速后应继续推进", clock.millis - afterFirst > 590_000L)
    }

    /** 长时间高速播放不能溢出成负数。 */
    @Test
    fun noOverflow() {
        val clock = PlaybackClock(Long.MAX_VALUE - 1000L)
        clock.speed = 86400.0
        repeat(600) { clock.advance(100_000_000L) }
        assertTrue("不能变成负数: ${clock.millis}", clock.millis > 0L)
        assertEquals(Long.MAX_VALUE, clock.millis)
    }

    /** 播放跨越年末时，UCT 与各星球本地时间要正确进位。 */
    @Test
    fun crossYearRollover() {
        val earth = Planets.byId("earth")
        val mars = Planets.byId("mars")
        val dec31 = Cal_yearEndUtc()
        val before = TimeSystem.uctStamp(dec31)
        assertEquals(2026, before.year)
        assertEquals(12, before.month)
        assertEquals(31, before.day)

        val clock = PlaybackClock(dec31 * 1000L)
        clock.speed = 3600.0
        repeat(120) { clock.advance(16_666_667L) } // 2 秒 × 3600 = 2 小时
        val after = TimeSystem.uctStamp(clock.millis / 1000L)
        assertEquals("跨年后应为次年 1 月 1 日", 2027, after.year)
        assertEquals(1, after.month)
        assertEquals(1, after.day)
        assertEquals(1, after.hour)

        val earthLocal = TimeSystem.localStamp(earth, clock.millis / 1000L)
        assertEquals(2027, earthLocal.year)
        val marsBefore = TimeSystem.localStamp(mars, dec31 * 1000L)
        val marsAfter = TimeSystem.localStamp(mars, clock.millis / 1000L)
        assertTrue("火星中间单位不应越界", marsAfter.month in 1..marsAfter.middleCount)
        assertTrue("火星日期不应越界", marsAfter.day in 1..marsAfter.daysInMiddleUnit)
        assertTrue("火星年内日序不应越界", marsAfter.dayOfYearLocal in 1..marsAfter.daysInYear)
        assertTrue("火星本地时间应被推进或进位", marsAfter.day != marsBefore.day || marsAfter.year != marsBefore.year || marsAfter.hour != marsBefore.hour)
    }

    /** 播放跨过一整天，本地时间必须回到同一时刻但日期 +1（24 小时制进位正确）。 */
    @Test
    fun crossDayRollover() {
        val start = Cal_yearEndUtc() // 2026-12-31 23:59:00 UTC 附近
        val clock = PlaybackClock(start * 1000L)
        clock.speed = 86400.0
        repeat(60) { clock.advance(16_666_667L) } // 1 秒 × 86400 = 1 天
        val after = TimeSystem.uctStamp(clock.millis / 1000L)
        assertEquals("跨日：应为次日", 1, after.day)
        assertEquals("跨日：时刻保持不变（23:59 起 1 天后仍是 23:59）", 23, after.hour)
    }

    @Test
    fun speedTable() {
        assertTrue(PlaybackClock.SPEEDS.contains(1.0))
        assertTrue(PlaybackClock.SPEEDS.contains(60.0))
        assertTrue(PlaybackClock.SPEEDS.contains(600.0))
        assertTrue(PlaybackClock.SPEEDS.contains(3600.0))
        assertEquals(PlaybackClock.SPEEDS.size, PlaybackClock.SPEED_LABELS.size)
    }

    /** 2026-12-31 23:59:00 UTC 的秒级时间戳 */
    private fun Cal_yearEndUtc(): Long =
        TimeSystemTime.timeOfUtc(2026, 12, 31, 23, 59, 0)
}

private object TimeSystemTime {
    fun timeOfUtc(y: Int, m: Int, d: Int, h: Int, mi: Int, s: Int): Long {
        val days = com.skysim.app.core.Cal.toEpochDay(y, m, d)
        return days * 86400L + h * 3600L + mi * 60L + s
    }
}
