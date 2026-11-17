package com.skysim.app

import com.skysim.app.core.Cal
import com.skysim.app.core.DebugReadout
import com.skysim.app.core.Planets
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.TimeAxis
import com.skysim.app.core.TimeSystem
import com.skysim.app.core.UctDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * v0.8.5：Debug 读数开关 + UCT 日期可调 + 播放态手动定位回归。
 *
 * 断言都落在「实际数值」上：日期范围要逐个核对年月日（而不是只看没抛异常），
 * 钳制要核对钳到哪个边界，播放回归要核对下一帧确实从新锚点继续而不是回到旧值。
 */
class V085Test {

    private val dayMs = 86_400_000L

    private fun at(y: Int, m: Int, d: Int, h: Int = 0, mi: Int = 0, s: Int = 0): Long =
        Cal.toEpochDay(y, m, d) * dayMs + h * 3_600_000L + mi * 60_000L + s * 1000L

    // ---------- 一、Debug 开关 ----------

    @Test
    fun debugOffDrawsNoReadoutAndOccupiesNoSpace() {
        val lines = DebugReadout.debugLines(
            enabled = false, frameMs = 16.5, p95Ms = 33.2,
            stageLine = "天空2.7 地面2.7 霾带0.0 树木0.0 其它0.0 HUD0.0", residualMs = 11.1
        )
        assertTrue("Debug 关闭时不能发射任何调试行", lines.isEmpty())
        assertFalse("Debug 关闭时不占布局", DebugReadout.occupiesSpace(false))
    }

    @Test
    fun debugOnRestoresBothOriginalLines() {
        val lines = DebugReadout.debugLines(
            enabled = true, frameMs = 16.5, p95Ms = 33.2,
            stageLine = "天空2.7 地面2.7 霾带0.0 树木0.0 其它0.0 HUD0.0", residualMs = 11.1
        )
        assertEquals("打开后应恢复两行调试读数", 2, lines.size)
        assertEquals("帧间隔 16.5 ms · P95 33.2 ms", lines[0])
        assertTrue("第二行必须带分段耗时原文", lines[1].contains("天空2.7"))
        assertTrue("第二行必须带合成耗时", lines[1].contains("合成11.1"))
        assertTrue(DebugReadout.occupiesSpace(true))
    }

    @Test
    fun qualityLineStaysVisibleRegardlessOfDebugSwitch() {
        // 画质与「已自动降档」是用户可见状态，不受 Debug 开关影响（签名里根本没有这个开关）
        assertEquals("画质 低（已自动降档）", DebugReadout.qualityLine(TerrainQuality.LOW, true))
        assertEquals("画质 中", DebugReadout.qualityLine(TerrainQuality.MEDIUM, false))
        assertEquals("画质 高", DebugReadout.qualityLine(TerrainQuality.HIGH, false))
    }

    // ---------- 二、默认日期与范围 ----------

    @Test
    fun defaultDateIsFixedDecemberFirst2025() {
        assertEquals("默认日期必须是 2025-12-01 00:00:00 UCT", at(2025, 12, 1), UctDate.DEFAULT_MILLIS)
        val s = TimeSystem.uctStamp(UctDate.DEFAULT_MILLIS / 1000L)
        assertEquals(2025, s.year)
        assertEquals(12, s.month)
        assertEquals(1, s.day)
        assertEquals(0, s.hour)
        assertEquals(0, s.minute)
        assertEquals(0, s.second)
        assertEquals("默认不应依赖系统时间", at(2025, 12, 1), UctDate.DEFAULT_MILLIS)
    }

    @Test
    fun rangeBoundsAreExactIsoExtendedYears() {
        assertEquals(at(-100_000, 1, 1), UctDate.MIN_MILLIS)
        assertEquals(at(100_000, 12, 31) + dayMs - 1, UctDate.MAX_MILLIS)
        assertEquals("-100000-01-01", UctDate.dateText(-100_000, 1, 1))
        assertEquals("100000-12-31", UctDate.dateText(100_000, 12, 31))
        assertEquals("-0001-01-01", UctDate.dateText(-1, 1, 1))
        assertEquals("2025-12-01", UctDate.dateText(2025, 12, 1))
        assertTrue(UctDate.inRange(UctDate.MIN_MILLIS))
        assertTrue(UctDate.inRange(UctDate.MAX_MILLIS))
        assertFalse(UctDate.inRange(UctDate.MIN_MILLIS - 1))
        assertFalse(UctDate.inRange(UctDate.MAX_MILLIS + 1))
    }

    @Test
    fun boundaryDatesConvertWithoutOverflowOrNaN() {
        for (ms in listOf(UctDate.MIN_MILLIS, UctDate.MAX_MILLIS)) {
            val s = TimeSystem.uctStamp(ms / 1000L)
            assertTrue("边界年份必须落在范围内", s.year in UctDate.MIN_YEAR..UctDate.MAX_YEAR)
            assertTrue("月份必须合法", s.month in 1..12)
            assertTrue("日号必须合法", s.day in 1..Cal.daysInMonth(s.year, s.month))
            assertTrue("日相位不能是 NaN", !s.dayFraction.isNaN())
            assertTrue("日相位必须落在 [0,1)", s.dayFraction >= 0.0 && s.dayFraction < 1.0)
            assertTrue("本地日序必须为正", s.dayOfYearLocal >= 1)
        }
    }

    @Test
    fun outOfRangeYearIsClampedToBoundaryNotThrown() {
        val low = UctDate.withDate(UctDate.DEFAULT_MILLIS, -200_000, 5, 5)
        assertTrue("越界必须标记为已钳制", low.clamped)
        val ls = TimeSystem.uctStamp(low.millis / 1000L)
        assertEquals(UctDate.MIN_YEAR, ls.year)
        assertEquals(5, ls.month)
        assertEquals(5, ls.day)
        assertTrue("钳制后的时刻必须落在范围内", UctDate.inRange(low.millis))

        val high = UctDate.withDate(UctDate.DEFAULT_MILLIS, 200_000, 5, 5)
        assertTrue(high.clamped)
        // 年越界钳到范围年，月日按输入各自钳制（不跳到 12-31，避免用户输入被悄悄改成别的日子）
        val hs = TimeSystem.uctStamp(high.millis / 1000L)
        assertEquals(100_000, hs.year)
        assertEquals(5, hs.month)
        assertEquals(5, hs.day)
    }

    @Test
    fun outOfRangeMonthAndDayAreClampedNotOverflowed() {
        val m = UctDate.withDate(at(2025, 6, 15), 2025, 13, 1)
        assertTrue(m.clamped)
        assertEquals(2025, TimeSystem.uctStamp(m.millis / 1000L).year)
        assertEquals("13 月钳到 12 月，不溢出到下一年", 12, TimeSystem.uctStamp(m.millis / 1000L).month)

        val feb30 = UctDate.withDate(at(2025, 6, 15), 2025, 2, 30)
        assertTrue(feb30.clamped)
        val s = TimeSystem.uctStamp(feb30.millis / 1000L)
        assertEquals(2, s.month)
        assertEquals("2025 不是闰年，2 月 30 日应钳到 28 日", 28, s.day)

        val leap = UctDate.withDate(at(2024, 6, 15), 2024, 2, 30)
        assertEquals("2024 是闰年，2 月 30 日钳到 29 日", 29, TimeSystem.uctStamp(leap.millis / 1000L).day)

        val ok = UctDate.withDate(at(2025, 6, 15), 2025, 12, 1)
        assertFalse("范围内输入不应被标记钳制", ok.clamped)
        assertEquals(at(2025, 12, 1), ok.millis)
    }

    @Test
    fun dateEditKeepsTimeOfDay() {
        val base = at(2025, 12, 1, 18, 30, 15)
        val edited = UctDate.withDate(base, 2026, 3, 7).millis
        assertEquals(at(2026, 3, 7, 18, 30, 15), edited)
        assertEquals("只改时刻不能动日期", at(2026, 3, 7, 6, 0, 0), UctDate.withTime(edited, 6, 0, 0))
    }

    @Test
    fun steppingNeverEscapesRange() {
        assertEquals(UctDate.MIN_MILLIS, UctDate.plusDays(UctDate.MIN_MILLIS, -1))
        assertEquals(UctDate.MAX_MILLIS, UctDate.plusDays(UctDate.MAX_MILLIS, 1))
        assertEquals(UctDate.MIN_MILLIS, UctDate.plusMonths(UctDate.MIN_MILLIS, -1))
        assertEquals(UctDate.MIN_YEAR + 1, TimeSystem.uctStamp(UctDate.plusYears(UctDate.MIN_MILLIS, 1) / 1000L).year)
        assertEquals(UctDate.MAX_YEAR - 1, TimeSystem.uctStamp(UctDate.plusYears(UctDate.MAX_MILLIS, -1) / 1000L).year)
        assertEquals("月末 +1 月应收到目标月末", at(2025, 2, 28, 12, 0, 0),
            UctDate.plusMonths(at(2025, 1, 31, 12, 0, 0), 1))
    }

    @Test
    fun backToTodayUsesSystemDateMidnight() {
        val now = at(2026, 11, 8, 16, 4, 0)
        val today = UctDate.todayMidnight(now)
        assertEquals(at(2026, 11, 8), today)
        assertEquals(0, TimeSystem.uctStamp(today / 1000L).hour)
    }

    // ---------- 三、播放态手动定位（旧缺陷回归） ----------

    @Test
    fun manualSeekDuringPlaybackSurvivesTheNextFrame() {
        val axis = TimeAxis(UctDate.DEFAULT_MILLIS)
        // 先播放两帧，让时钟自己往前走过
        axis.advanceFrame(16_666_667L, 60.0)
        axis.advanceFrame(16_666_667L, 60.0)
        val beforeSeek = axis.millis
        assertTrue("正常播放必须推进时间", beforeSeek > UctDate.DEFAULT_MILLIS)

        val target = at(2026, 3, 1, 12, 0, 0)
        assertEquals(target, axis.seekTo(target))
        val afterSeek = axis.advanceFrame(16_666_667L, 60.0)
        assertTrue("seek 后下一帧必须从新目标继续，不能回到旧值", axis.millis >= target)
        // v0.8.7：advanceFrame 改为返回绝对时刻（以前返回本帧增量，UI 会把它当绝对时刻用，
        // 于是点播放就跳到 1970 附近）。断言更严：绝对时刻在目标之后约 1 秒，且本帧增量确实是 1000 ms。
        assertEquals("返回值必须是绝对时刻，不是本帧增量", axis.millis, afterSeek)
        assertEquals("1 帧 16.7 ms × 60 倍速 = 约 1 秒", 1_000L, axis.lastStepMillis)
        assertTrue("绝对时刻必须在目标之后约 1 秒", abs(afterSeek - target - 1_000L) <= 1L)
        assertTrue("推进量必须是毫秒级，不是跳回旧值", abs(axis.millis - target - 1_000L) <= 1L)
    }

    @Test
    fun seekTargetsBeyondRangeAreClampedInsidePlayback() {
        val axis = TimeAxis(UctDate.DEFAULT_MILLIS)
        axis.seekTo(UctDate.MAX_MILLIS + 10L * dayMs)
        assertEquals(UctDate.MAX_MILLIS, axis.millis)
        axis.advanceFrame(1_000_000_000L, 86400.0)
        assertEquals("到上界后必须停住，不能回绕成负数", UctDate.MAX_MILLIS, axis.millis)
    }

    @Test
    fun seekCountAdvancesSoComposeCanRestartTheLoop() {
        val axis = TimeAxis(UctDate.DEFAULT_MILLIS)
        assertEquals(0, axis.seekCount)
        axis.seekTo(at(2025, 12, 2))
        assertEquals(1, axis.seekCount)
        axis.advanceFrame(16_666_667L, 1.0)
        assertEquals("播放推进不应触发帧循环重启", 1, axis.seekCount)
    }

    // ---------- 四、其它历法与季节相位随 UCT 一致 ----------

    @Test
    fun localCalendarsFollowTheSameUctInstant() {
        val t = UctDate.DEFAULT_MILLIS / 1000L
        val next = t + 86_400L
        for (id in listOf("earth", "mars", "jupiter", "titan", "triton", "moon")) {
            val p = Planets.byId(id)
            val a = TimeSystem.localStamp(p, t)
            val b = TimeSystem.localStamp(p, next)
            // v0.8.8：本地日按真实物理日长推进。UCT 走 1 日，本地日相位前进 86400/太阳日（对地球恰为 1 天）。
            val shift = (86_400.0 / p.solarDaySeconds) % 1.0
            val expected = (a.dayFraction + shift) % 1.0
            assertEquals("$id 的本地日相位必须按物理日长推进", expected, b.dayFraction, 1e-6)
            assertTrue("$id 本地日序必须在 1..${p.localDaysPerYear}",
                b.dayOfYearLocal in 1..p.localDaysPerYear)
            if (id == "earth") {
                // 地球走公历：本地读数就是 UCT 读数，日相位不受物理日长推导误差影响
                val uct = TimeSystem.uctStamp(next)
                assertEquals("地球本地日必须等于 UCT 日", uct.day, b.day)
                assertEquals("地球本地时必须是 UCT 时", uct.hour, b.hour)
            }
        }
    }

    @Test
    fun localCalendarStaysConsistentAtRangeBoundaries() {
        for (ms in listOf(UctDate.MIN_MILLIS, UctDate.MAX_MILLIS)) {
            for (id in listOf("earth", "mars", "titan")) {
                val s = TimeSystem.localStamp(Planets.byId(id), ms / 1000L)
                assertTrue("$id 在 $ms 的日相位不能是 NaN", !s.dayFraction.isNaN())
                assertTrue("$id 在 $ms 的日相位必须合法", s.dayFraction in 0.0..1.0)
                val body = Planets.byId(id)
                assertTrue("$id 在 $ms 的月必须合法", s.month in 1..s.monthsPerYear)
                // 本地历法的月长由 localDaysPerYear 平均分配，不等于公历月长（火星每月约 56 日）
                assertTrue("$id 在 $ms 的本地日号必须合法", s.day in 1..body.monthLengths.max())
            }
        }
    }
}
