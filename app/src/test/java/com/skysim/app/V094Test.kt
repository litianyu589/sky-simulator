package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import com.skysim.app.ui.InfoLineMetrics
import com.skysim.app.ui.Str
import com.skysim.app.ui.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.4：英文把「星（假月）」定名为 Starn，并修复信息栏/工具栏的排版。
 *
 * 锁三件事：
 *  1. 星制天体在英文下处处写 Starn / starns，绝不再用 Star；
 *  2. 真恒星（Star catalog / Host star / ← Stars）仍然是 Star，别被一刀切改掉；
 *  3. 信息栏的标签列宽与间距是布局常量，不是靠译文手打空格。
 */
class V094Test {

    private fun p(id: String) = Planets.byId(id)
    private val instant = 1_700_000_000L

    private fun withLang(lang: UiLang, body: () -> Unit) {
        val previous = Str.lang
        try {
            Str.lang = lang
            body()
        } finally {
            Str.lang = previous
        }
    }

    @Test
    fun englishStarChainSaysStarnEverywhere() = withLang(UiLang.EN) {
        val stamp = TimeSystem.localStamp(p("mars"), instant)
        val calendar = Str.calendarText(stamp)
        assertTrue("HUD 日期行必须用 Starn：$calendar", calendar.contains("Starn"))
        assertFalse("HUD 日期行不得再用 Star 当单位：$calendar", Regex("\\bStar\\b").containsMatchIn(calendar))

        val structure = Str.yearStructure(stamp)
        assertTrue("一年结构必须用 starns：$structure", structure.contains("starns"))
        assertFalse("一年结构不得再用 stars：$structure", Regex("\\bstars\\b").containsMatchIn(structure))

        assertEquals("Starn", Str.middleUnitLabel(stamp))
        assertEquals("−1 starn", Str.middleUnitDelta(stamp, -1))
        assertEquals("+1 starn", Str.middleUnitDelta(stamp, 1))
    }

    @Test
    fun monthChainAndRealStarsAreNotRenamed() = withLang(UiLang.EN) {
        val earthStamp = TimeSystem.localStamp(p("earth"), instant)
        assertEquals("月制天体仍是 Month", "Month", Str.middleUnitLabel(earthStamp))

        // 真恒星用词必须保持原样
        assertEquals("Star catalog", Str.tr("恒星目录"))
        assertTrue("Host star 不该被改成 Host starn", Str.tr("所属恒星：").startsWith("Host star"))
    }

    @Test
    fun chineseAndJapaneseKeepTheStarCharacter() {
        withLang(UiLang.ZH) {
            val stamp = TimeSystem.localStamp(p("mars"), instant)
            assertEquals("星", Str.middleUnitLabel(stamp))
            assertEquals("+1星", Str.middleUnitDelta(stamp, 1))
        }
        withLang(UiLang.JA) {
            val stamp = TimeSystem.localStamp(p("mars"), instant)
            assertEquals("星", Str.middleUnitLabel(stamp))
            assertEquals("+1星", Str.middleUnitDelta(stamp, 1))
        }
    }

    @Test
    fun noBodyStillUsesStarAsCalendarUnitInEnglish() = withLang(UiLang.EN) {
        for (planet in Planets.all) {
            val stamp = TimeSystem.localStamp(planet, instant)
            val calendar = Str.calendarText(stamp)
            val structure = Str.yearStructure(stamp)
            assertFalse("${planet.id} 的日历行仍把单位写成 Star：$calendar", Regex("\\bStar\\b").containsMatchIn(calendar))
            assertFalse("${planet.id} 的一年结构仍把单位写成 stars：$structure", Regex("\\bstars\\b").containsMatchIn(structure))
        }
    }

    @Test
    fun infoLineUsesLayoutSpacingInsteadOfManualSpaces() {
        assertTrue("标签列必须有固定宽度", InfoLineMetrics.LABEL_WIDTH_DP >= 62f)
        assertTrue("标签与数值之间必须留间距，不能紧贴", InfoLineMetrics.LABEL_VALUE_GAP_DP > 0f)
        assertEquals("工具栏两行两列，避免按钮文案被截", 2, InfoLineMetrics.TOOLBAR_BUTTONS_PER_ROW)
    }
}
