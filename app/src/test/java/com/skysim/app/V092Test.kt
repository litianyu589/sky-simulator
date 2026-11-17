package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.PlaybackClock
import com.skysim.app.core.TimeSystem
import com.skysim.app.ui.Str
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.2：只锁两处。
 *
 *  1. 首次安装的默认倍速必须落在 1× 原速上——不是「下标等于某个数字」，
 *     而是「那个下标在档位表里取出来的值就是 1.0」，这样以后扩档也不会再错位；
 *  2. 时间轴滑条一格等于「日长 ÷ 24 SI 秒」，文案也必须是「1 本地小时」口径。
 */
class V092Test {

    private fun p(id: String) = Planets.byId(id)

    @Test
    fun defaultSpeedIndexLandsOnRealTime() {
        val idx = PlaybackClock.DEFAULT_SPEED_INDEX
        assertTrue("默认下标必须落在档位表内", idx in PlaybackClock.SPEEDS.indices)
        assertEquals("默认倍速必须是 1× 原速", 1.0, PlaybackClock.SPEEDS[idx], 0.0)
        // 反证：0.9.1 及以前写死的 2 现在是慢放档，若有人把它写回去，这条会挂
        if (PlaybackClock.SPEEDS.size > 2) {
            assertTrue("下标 2 不该正好是 1×（否则本用例失去意义）", PlaybackClock.SPEEDS[2] != 1.0)
        }
    }

    @Test
    fun sliderStepIsOneLocalHourOfTheOwnDayLength() {
        for (id in listOf("earth", "mars", "mercury", "venus")) {
            val planet = p(id)
            val day = TimeSystem.localDayLengthSeconds(planet)
            val step = day / 24.0
            // 用相位换算验证：相位推进 1/24 本地日，UCT 必须推进「日长 ÷ 24」秒
            val phase = 0.3
            val t0 = TimeSystem.uctSecondsForLocalPhase(planet, 0L, phase)
            val t1 = TimeSystem.uctSecondsForLocalPhase(planet, 0L, phase + 1.0 / 24.0)
            // uctSecondsForLocalPhase 返回整秒（便于 seek），两次取整会带来 ±1 s 的量化误差，
            // 所以容差取 1 s；比例本身（日长 ÷ 24）是按 Double 算的，没有别的水分。
            assertEquals("$id 一格必须等于日长/24", step, (t1 - t0).toDouble(), 1.0)
        }
    }

    @Test
    fun sliderLabelSaysOneLocalHour() {
        val text = Str.t("time.slider", "12时00分", "3698.97")
        assertTrue("滑条文案必须写「1 本地小时」：$text", text.contains("1 本地小时"))
        assertTrue("不得再出现「1/24 本地日」的说法：$text", !text.contains("1/24 本地日"))
    }
}
