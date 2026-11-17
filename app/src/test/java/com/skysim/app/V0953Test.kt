package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.ui.PickerMetrics
import com.skysim.app.ui.Str
import com.skysim.app.ui.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.5.3：切换星球弹窗的宽度、卡片列数与关闭按钮尺寸。
 *
 * 目标：三种语言都不出现单词内折行（Mercury、terrestrial、Dwarf planet 这类），
 * 关闭按钮固定 48 dp 方块，弹窗不超出屏幕可用宽度。
 * 宽度估算沿用 V0952Test 的口径：CJK 1 em、ASCII 0.6 em。
 */
class V0953Test {

    private val phoneWidthDp = 873f   // 1080×2400、密度约 2.75 的横屏可用宽度

    private fun withLang(lang: UiLang, body: () -> Unit) {
        val previous = Str.lang
        try {
            Str.lang = lang
            body()
        } finally {
            Str.lang = previous
        }
    }

    private fun emWidth(text: String, fontSizeSp: Float): Float = text.sumOf { ch ->
        when {
            ch.code in 0x2E80..0x9FFF -> 1.0
            ch.code in 0xFF00..0xFF60 -> 1.0
            else -> 0.6
        } * fontSizeSp
    }.toFloat()

    @Test
    fun dialogWidthFollowsLanguageAndStaysOnScreen() {
        assertEquals(760f, PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.EN), 0.01f)
        assertEquals(660f, PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.ZH), 0.01f)
        assertEquals(660f, PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.JA), 0.01f)
        // 英文区域必须比中日宽，才放得下 terrestrial / Day length 这类长词。
        assertTrue(PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.EN) > PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.ZH))
        for (lang in UiLang.values()) {
            val width = PickerMetrics.dialogWidthDp(phoneWidthDp, lang)
            assertTrue("$lang 弹窗宽度 $width 超出屏幕", width <= phoneWidthDp - PickerMetrics.SCREEN_MARGIN_DP * 2f + 0.01f)
        }
        // 窄屏只能给到可用宽度，不能撑破屏幕。
        val narrow = PickerMetrics.dialogWidthDp(600f, UiLang.EN)
        assertEquals(600f - 48f, narrow, 0.01f)
    }

    @Test
    fun columnsDropBeforeAnyWordBreaks() {
        val enContent = PickerMetrics.contentWidthDp(PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.EN))
        assertEquals(3, PickerMetrics.columnsFor(enContent))
        // 窄屏只放得下两列，宁可少一列也不让单词从中间断开。
        assertEquals(2, PickerMetrics.columnsFor(524f))
        assertTrue(PickerMetrics.columnsFor(200f) >= PickerMetrics.MIN_COLUMNS)
        assertTrue(PickerMetrics.columnsFor(2000f) <= PickerMetrics.MAX_COLUMNS)
    }

    @Test
    fun planetNamesNeverBreakMidWordInAnyLanguage() {
        for (lang in UiLang.values()) {
            withLang(lang) {
                val content = PickerMetrics.contentWidthDp(PickerMetrics.dialogWidthDp(phoneWidthDp, lang))
                val columns = PickerMetrics.columnsFor(content)
                val textWidth = PickerMetrics.cardTextWidthDp(content, columns)
                for (planet in Planets.all) {
                    val name = Str.planetName(planet)
                    assertTrue(
                        "$lang 下天体名「$name」在 ${textWidth} dp 卡片里放不下，会被折断",
                        emWidth(name, 12f) <= textWidth
                    )
                }
            }
        }
    }

    @Test
    fun kindAndDayLengthLinesFitOnOneLine() {
        for (lang in UiLang.values()) {
            withLang(lang) {
                val content = PickerMetrics.contentWidthDp(PickerMetrics.dialogWidthDp(phoneWidthDp, lang))
                val columns = PickerMetrics.columnsFor(content)
                val textWidth = PickerMetrics.cardTextWidthDp(content, columns)
                for (planet in Planets.all) {
                    val kind = Str.planetKind(planet)
                    assertTrue("$lang 类型「$kind」超出卡片宽度", emWidth(kind, 9f) <= textWidth)
                    val day = "${Str.tr("日长")} ${Str.durationShort(planet.localDayHours)}"
                    assertTrue("$lang 日长「$day」超出卡片宽度", emWidth(day, 9f) <= textWidth)
                }
            }
        }
    }

    @Test
    fun longestEnglishStringsAreCoveredExplicitly() {
        withLang(UiLang.EN) {
            val content = PickerMetrics.contentWidthDp(PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.EN))
            val textWidth = PickerMetrics.cardTextWidthDp(content, PickerMetrics.columnsFor(content))
            // 截图里被折断、折行不齐的三个词，逐个锁住。
            assertTrue(emWidth("Mercury", 12f) <= textWidth)
            assertTrue(emWidth("terrestrial", 9f) <= textWidth)
            assertTrue(emWidth("Dwarf planet", 9f) <= textWidth)
            assertTrue(emWidth("Gas giant", 9f) <= textWidth)
            // 分类标签同样不能被压扁。
            val tabWidth = (content - 12f) / 3f
            assertTrue(emWidth("Dwarf planets & asteroids", 9.5f) <= tabWidth)
        }
    }

    @Test
    fun closeButtonKeepsAFullTouchTarget() {
        assertTrue("关闭按钮触控区不得小于 48 dp", PickerMetrics.CLOSE_BUTTON_DP >= 48f)
        // 标题区必须能拿到剩余宽度：48 dp 方块 + 8 dp 间隔之后还有空间放标题。
        val content = PickerMetrics.contentWidthDp(PickerMetrics.dialogWidthDp(phoneWidthDp, UiLang.EN))
        assertTrue(content - PickerMetrics.CLOSE_BUTTON_DP - 8f > 400f)
    }
}
