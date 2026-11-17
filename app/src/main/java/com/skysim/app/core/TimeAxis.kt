package com.skysim.app.core

/**
 * 播放时间轴的**唯一**写入者。
 *
 * 旧缺陷一（v0.8.3 起记录、v0.8.4 仍在）：MainScreen 的播放循环每帧把时钟值写回 `uctMillis`，
 * 于是「日出 / 正午 / 日落 / 午夜」「前一天 / 后一天」以及日期调节在播放态下会在下一帧被旧值冲掉。
 * 修法：手动定位统一走 [seekTo]，它同时移动时钟本体，下一帧从新目标继续推进；[seekCount] 供 Compose
 * 侧当 LaunchedEffect 的 key，让帧循环在新锚点上重启一次。
 *
 * 旧缺陷二（v0.8.7 修）：[advanceFrame] 以前返回的是**本帧推进量**（step），而 MainScreen 把它当成
 * 绝对时刻赋给 `uctMillis`，于是点播放后界面直接跳到 1970-01-01 附近（1× 时是 16 ms、3600× 时是
 * 一分钟上下，随帧间隔抖动，看起来就是「秒数涨一点又落回来」）。内部时钟 `clock.millis` 一直是对的，
 * 错的是喂给 UI 的那个值，所以单测只看 `axis.millis` 时全绿。
 * 现在 [advanceFrame] 返回**绝对时刻**，`step` 单独用 [lastStepMillis] 取，二者不再混用。
 */
class TimeAxis(startMillis: Long = UctDate.DEFAULT_MILLIS) {

    private val clock = PlaybackClock(UctDate.clampMillis(startMillis))

    var millis: Long = UctDate.clampMillis(startMillis)
        private set

    /** 最近一帧推进了多少毫秒（只做展示与诊断，绝不能当绝对时刻用）。 */
    var lastStepMillis: Long = 0L
        private set

    /** 手动定位次数。Compose 侧用它当 key 重启帧循环。 */
    var seekCount: Int = 0
        private set

    /** 手动定位到某个时刻（日期输入、时刻按钮、前后一天、回到今天、实时同步）。 */
    fun seekTo(target: Long): Long {
        millis = UctDate.clampMillis(target)
        clock.setTime(millis)
        lastStepMillis = 0L
        seekCount++
        return millis
    }

    /**
     * 播放一帧。
     * @param deltaNanos 距上一帧的真实纳秒数
     * @param speed 当前倍速
     * @return **推进后的绝对时刻**（毫秒）。UI 直接用它赋给 uctMillis；本帧增量见 [lastStepMillis]。
     */
    fun advanceFrame(deltaNanos: Long, speed: Double): Long {
        clock.speed = speed
        lastStepMillis = clock.advance(deltaNanos)
        millis = UctDate.clampMillis(clock.millis)
        return millis
    }
}
