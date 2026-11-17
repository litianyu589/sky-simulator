package com.skysim.app

import com.skysim.app.core.Cal
import com.skysim.app.core.Planets
import com.skysim.app.core.PlaybackClock
import com.skysim.app.core.TimeAxis
import com.skysim.app.core.TimeSystem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.0：倍速档位扩展 + HUD 精简后的读数口径。
 *
 * 锁三件事：
 *  1. 档位表本身（慢放三档、高速三档，单调递增，标签一一对应）；
 *  2. 最高档 864000× 下时间仍单调、本地日序号不倒退、相位与 UCT 仍自洽（不溢出、不跳号）；
 *  3. HUD 的行结构：本地时间行不带前缀、限定语单独一行、地球没有限定语。
 */
class V090Test {

    private val epochSeconds: Long = TimeSystem.EPOCH_EPOCH_DAY * 86_400L
    private fun p(id: String) = Planets.byId(id)

    @Test
    fun speedPresetsCoverSlowAndExtremeFast() {
        val speeds = PlaybackClock.SPEEDS
        assertEquals("标签必须与档位一一对应", speeds.size, PlaybackClock.SPEED_LABELS.size)
        assertTrue("档位必须严格递增", speeds.zipWithNext().all { (a, b) -> b > a })
        for (v in listOf(0.1, 0.25, 0.5, 1.0, 86_400.0, 172_800.0, 432_000.0, 864_000.0)) {
            assertTrue("缺少档位 $v", speeds.contains(v))
        }
    }

    @Test
    fun speedDescriptionReadsNaturally() {
        assertEquals("1 秒 = 10 天", PlaybackClock.describe(864_000.0))
        assertEquals("1 秒 = 5 天", PlaybackClock.describe(432_000.0))
        assertEquals("1 秒 = 2 天", PlaybackClock.describe(172_800.0))
        assertEquals("1 秒 = 1 天", PlaybackClock.describe(86_400.0))
        assertEquals("1 秒 = 1 小时", PlaybackClock.describe(3_600.0))
        assertEquals("10 秒 = 1 秒", PlaybackClock.describe(0.1))
    }

    @Test
    fun topSpeedLetsALocalDayPassInSeconds() {
        val mercury = TimeSystem.localDayLengthSeconds(p("mercury")) / 864_000.0
        val venus = TimeSystem.localDayLengthSeconds(p("venus")) / 864_000.0
        assertTrue("水星一圈应在 20 s 内看完，实际 $mercury s", mercury < 20.0)
        assertTrue("金星一圈应在 13 s 内看完，实际 $venus s", venus < 13.0)
    }

    @Test
    fun topSpeedStaysMonotonicAndKeepsPhaseConsistent() {
        val axis = TimeAxis(Cal.toEpochDay(2025, 12, 1) * 86_400_000L)
        var last = axis.millis
        repeat(600) {
            axis.advanceFrame(16_700_000L, 864_000.0)
            assertTrue("时间必须严格前进（不得溢出或回绕）", axis.millis > last)
            assertTrue("单帧增量必须为正", axis.lastStepMillis > 0L)
            last = axis.millis
        }
        val t = axis.millis / 1000L
        for (id in listOf("mars", "mercury", "venus")) {
            val planet = p(id)
            assertEquals(
                "$id 的日相位必须仍由 UCT 唯一决定",
                TimeSystem.localPhaseOf(planet, t),
                TimeSystem.localStamp(planet, t).dayFraction,
                2.0e-5
            )
        }
    }

    @Test
    fun topSpeedNeverMakesLocalDaysGoBackwards() {
        val axis = TimeAxis(Cal.toEpochDay(2025, 12, 1) * 86_400_000L)
        var prev = Long.MIN_VALUE
        repeat(300) {
            axis.advanceFrame(16_700_000L, 864_000.0)
            for (id in listOf("mercury", "mars")) {
                val d = TimeSystem.localStamp(p(id), axis.millis / 1000L).localDayNumber
                assertTrue("$id 的本地日序号不得倒退：$prev → $d", d >= prev)
            }
            prev = TimeSystem.localStamp(p("mercury"), axis.millis / 1000L).localDayNumber
        }
    }

    @Test
    fun slowestSpeedAdvancesLessThanRealTime() {
        val axis = TimeAxis(Cal.toEpochDay(2025, 12, 1) * 86_400_000L)
        var moved = 0L
        repeat(60) {
            axis.advanceFrame(16_700_000L, 0.1)
            if (axis.lastStepMillis > 0) moved += axis.lastStepMillis
        }
        // 60 帧 ≈ 1 s 真实时间，0.1× 下应只走约 100 ms 模拟时间（累加取整会有 ±1 ms/帧的残差）
        assertTrue("0.1× 下 1 秒真实时间应只推进约 100 ms，实际 $moved ms", moved in 60..140)
    }

    @Test
    fun hudLinesFollowTheNewFormat() {
        val earth = TimeSystem.localStamp(p("earth"), Cal.toEpochDay(2025, 12, 1) * 86_400L)
        assertFalse("地球本地时间行不得带前缀", earth.hudTimeText().startsWith("（模型推测）"))
        assertEquals("地球是基准真值，不该有限定语", "", earth.hudQualifierText())

        val mars = TimeSystem.localStamp(p("mars"), epochSeconds)
        assertTrue("火星必须有「模型推测」限定语", mars.hudQualifierText().startsWith("模型推测"))
        val jupiter = TimeSystem.localStamp(p("jupiter"), epochSeconds)
        assertTrue("气巨必须标约定相位", jupiter.hudQualifierText().contains("约定相位"))

        for (id in listOf("mars", "venus", "trappist1e", "kepler22b")) {
            val s = TimeSystem.localStamp(p(id), epochSeconds)
            assertFalse("$id 的本地时间行不得含前缀", s.hudTimeText().contains("模型推测"))
            assertFalse("$id 的本地时间行不得含 UCT 字样", s.hudTimeText().contains("UCT"))
            assertFalse("$id 的本地时间行不得含历法名", s.hudTimeText().contains("历"))
        }
    }

    @Test
    fun infoPanelRowsAreFilledForEveryBody() {
        for (planet in Planets.all) {
            val s = TimeSystem.localStamp(planet, epochSeconds)
            assertTrue("${planet.id} 的一年结构为空", s.yearStructureText().isNotEmpty())
            assertTrue("${planet.id} 的进制行为空", s.schemeShortText().isNotEmpty())
            assertTrue("${planet.id} 的相位说明为空", s.phaseDetailText().isNotEmpty())
            assertTrue("${planet.id} 的本地日长必须为正", TimeSystem.localDayLengthSeconds(planet) > 0.0)
        }
    }
}
