package com.skysim.app.core

/**
 * v0.8.5：UCT 日期可调。
 *
 * 范围取 ISO 8601 扩展年份的 ±100000 年（公元前 100000 年 ~ 公元 100000 年）。
 * 这个范围是「能算不崩」，不是「算得准」：公历换算是精确的整数运算，但行星历表（简化 VSOP 式）
 * 远离 J2000 后误差会迅速放大，万年后只能当示意看。
 *
 * 所有入口都做钳制：越界输入落到边界并回传 clamped=true，由 UI 明确提示，不静默吞掉、不抛异常。
 */
object UctDate {
    const val MIN_YEAR: Int = -100_000
    const val MAX_YEAR: Int = 100_000

    private const val MILLIS_PER_DAY: Long = 86_400_000L

    /** 默认日期：2025-12-01 00:00:00 UCT（固定起点，不再默认取系统当前时间）。 */
    val DEFAULT_MILLIS: Long = Cal.toEpochDay(2025, 12, 1) * MILLIS_PER_DAY

    /** 范围下界：-100000-01-01 00:00:00 UCT。 */
    val MIN_MILLIS: Long = Cal.toEpochDay(MIN_YEAR, 1, 1) * MILLIS_PER_DAY

    /** 范围上界：100000-12-31 23:59:59.999 UCT。 */
    val MAX_MILLIS: Long = Cal.toEpochDay(MAX_YEAR, 12, 31) * MILLIS_PER_DAY + MILLIS_PER_DAY - 1L

    fun clampMillis(millis: Long): Long = millis.coerceIn(MIN_MILLIS, MAX_MILLIS)

    fun inRange(millis: Long): Boolean = millis >= MIN_MILLIS && millis <= MAX_MILLIS

    /** 一次日期编辑的结果：真正生效的时刻 + 是否被钳制（UI 用它给出提示）。 */
    data class Edit(val millis: Long, val clamped: Boolean)

    /** 直接设定年月日，保留当天的时刻（时:分:秒）。越界的年/月/日一律钳到合法值。 */
    fun withDate(millis: Long, year: Int, month: Int, day: Int): Edit {
        val y = year.coerceIn(MIN_YEAR, MAX_YEAR)
        val m = month.coerceIn(1, 12)
        val d = day.coerceIn(1, Cal.daysInMonth(y, m))
        val timeOfDay = Math.floorMod(millis, MILLIS_PER_DAY)
        val raw = Cal.toEpochDay(y, m, d) * MILLIS_PER_DAY + timeOfDay
        val c = clampMillis(raw)
        return Edit(c, y != year || m != month || d != day || c != raw)
    }

    /** 只改时刻，日期保持不变。 */
    fun withTime(millis: Long, hour: Int, minute: Int, second: Int): Long {
        val dayStart = Math.floorDiv(millis, MILLIS_PER_DAY) * MILLIS_PER_DAY
        val sec = hour.coerceIn(0, 23) * 3600L + minute.coerceIn(0, 59) * 60L + second.coerceIn(0, 59)
        return clampMillis(dayStart + sec * 1000L)
    }

    fun plusDays(millis: Long, n: Int): Long = clampMillis(millis + n.toLong() * MILLIS_PER_DAY)

    /** 按月步进：日号超出目标月长度时收到月末（1-31 +1 月 = 2-28/29），不是溢出到下个月。 */
    fun plusMonths(millis: Long, n: Int): Long {
        val s = TimeSystem.uctStamp(Math.floorDiv(millis, 1000L))
        val total = s.year.toLong() * 12L + (s.month - 1) + n.toLong()
        val y = Math.floorDiv(total, 12L)
        // 整年越界时落到边界瞬间，而不是「钳年留月日」——那会让「往前一个月」跳到更晚的日期
        if (y < MIN_YEAR) return MIN_MILLIS
        if (y > MAX_YEAR) return MAX_MILLIS
        val m = Math.floorMod(total, 12L).toInt() + 1
        val d = s.day.coerceAtMost(Cal.daysInMonth(y.toInt(), m))
        return withDate(millis, y.toInt(), m, d).millis
    }

    /** 按年步进：2-29 +1 年落到 2-28；越界落到边界瞬间。 */
    fun plusYears(millis: Long, n: Int): Long {
        val s = TimeSystem.uctStamp(Math.floorDiv(millis, 1000L))
        val y = s.year.toLong() + n
        if (y < MIN_YEAR) return MIN_MILLIS
        if (y > MAX_YEAR) return MAX_MILLIS
        val d = s.day.coerceAtMost(Cal.daysInMonth(y.toInt(), s.month))
        return withDate(millis, y.toInt(), s.month, d).millis
    }

    /** 「回到今天」：系统当前 UCT 日期的 00:00:00（确定性复位，不依赖上一次的时分秒）。 */
    fun todayMidnight(nowMillis: Long): Long =
        clampMillis(Math.floorDiv(nowMillis, MILLIS_PER_DAY) * MILLIS_PER_DAY)

    /** ISO 8601 扩展年份：负数年带符号，年份至少 4 位（-0001 / -100000 / 100000）。 */
    fun dateText(year: Int, month: Int, day: Int): String {
        val y = if (year < 0) String.format("-%04d", -year) else String.format("%04d", year)
        return String.format("%s-%02d-%02d", y, month, day)
    }

    fun describe(millis: Long): String {
        val s = TimeSystem.uctStamp(Math.floorDiv(millis, 1000L))
        return String.format("%s %02d:%02d:%02d", dateText(s.year, s.month, s.day), s.hour, s.minute, s.second)
    }

    fun rangeDateText(): String = "${dateText(MIN_YEAR, 1, 1)} ~ ${dateText(MAX_YEAR, 12, 31)}"

    fun rangeText(): String = "${describe(MIN_MILLIS)} ~ ${describe(MAX_MILLIS)}"
}
