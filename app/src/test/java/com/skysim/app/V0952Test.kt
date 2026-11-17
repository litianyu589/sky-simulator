package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import com.skysim.app.ui.HudMetrics
import com.skysim.app.ui.Str
import com.skysim.app.ui.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil

/**
 * v0.9.5.2：HUD 卡片宽度自适应 + 不再截断长文案。
 *
 * 卡片宽度 = min(内容宽, 屏幕宽 − 右栏宽 − 间隔)，文本超宽时在卡片内换行（最多两行）。
 * 这里用「字符 em 宽度 + 字号」估算行数，逐个天体、三种语言核对不会出现被裁的字。
 * 估算口径：CJK 与全角符号按 1 em，ASCII 按 0.6 em（等宽字体的保守估计）。
 */
class V0952Test {

    // 用户这台设备：1080×2400、密度约 2.75 → 横屏可用宽约 873 dp；宽屏右栏 326 dp。
    private val phoneWidthDp = 873f
    private val panelWidthDp = 326f
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

    private fun emWidth(text: String): Float = text.sumOf { ch ->
        when {
            ch.code in 0x2E80..0x9FFF -> 1.0   // CJK 统一表意文字
            ch.code in 0xFF00..0xFF60 -> 1.0   // 全角标点与全角字母
            else -> 0.6
        }
    }.toFloat()

    private fun lineCount(text: String, fontSizeSp: Float, innerWidthDp: Float): Int {
        if (text.isEmpty()) return 0
        val widthDp = emWidth(text) * fontSizeSp
        return ceil(widthDp / innerWidthDp).toInt().coerceAtLeast(1)
    }

    private class HudText(val label: String, val text: String, val fontSizeSp: Float)

    private fun hudTexts(planet: com.skysim.app.core.Planet): List<HudText> {
        val stamp = TimeSystem.localStamp(planet, instant)
        val out = mutableListOf<HudText>()
        out += HudText("本地时间", Str.calendarText(stamp), 14f)
        val qualifier = Str.hudQualifier(stamp)
        if (qualifier.isNotEmpty()) out += HudText("限定小字", Str.tr(qualifier), 9f)
        out += HudText("UCT", Str.t("hud.uct", Str.calendarText(TimeSystem.uctStamp(instant))), 10f)
        return out
    }

    private fun assertAllBodiesFit(maxLines: Int) {
        val inner = HudMetrics.innerWidthDp(phoneWidthDp, panelWidthDp)
        for (planet in Planets.all) {
            for (item in hudTexts(planet)) {
                val lines = lineCount(item.text, item.fontSizeSp, inner)
                assertTrue(
                    "${planet.id} 的${item.label}在 ${inner} dp 内需要 $lines 行（上限 $maxLines）：${item.text}",
                    lines <= maxLines
                )
            }
        }
    }

    @Test
    fun cardWidthAdaptsToContentButStaysLeftOfThePanel() {
        // 873 − 326 − 46 = 501 dp：足够放下英文最长的一行，又不会顶到右栏。
        assertEquals(501f, HudMetrics.maxCardWidthDp(phoneWidthDp, panelWidthDp), 0.01f)
        assertEquals(
            HudMetrics.maxCardWidthDp(phoneWidthDp, panelWidthDp) - HudMetrics.CARD_PADDING_DP,
            HudMetrics.innerWidthDp(phoneWidthDp, panelWidthDp),
            0.01f
        )
        // 窄屏不能算出负数或零宽：最低宽度兜底。
        assertTrue(HudMetrics.maxCardWidthDp(320f, 232f) >= HudMetrics.MIN_WIDTH_DP)
        // 封顶必须比右栏窄，否则会和右栏重叠。
        assertTrue(HudMetrics.maxCardWidthDp(phoneWidthDp, panelWidthDp) < phoneWidthDp - panelWidthDp)
    }

    @Test
    fun englishHudFitsWithinTwoLinesForEveryBody() = withLang(UiLang.EN) {
        assertAllBodiesFit(HudMetrics.MAX_TEXT_LINES)
    }

    @Test
    fun japaneseHudFitsWithinTwoLinesForEveryBody() = withLang(UiLang.JA) {
        assertAllBodiesFit(HudMetrics.MAX_TEXT_LINES)
    }

    @Test
    fun chineseHudStillFitsOnOneLine() = withLang(UiLang.ZH) {
        assertAllBodiesFit(1)
    }

    @Test
    fun englishNoLongerNeedsTheOldFixedCap() {
        // 0.9.5.1 的硬上限是 286 dp，去掉内边距只有 258 dp；火星那行英文超过它，正是被裁的原因。
        val mars = Planets.byId("mars")
        withLang(UiLang.EN) {
            val local = hudTexts(mars).first { it.label == "本地时间" }
            val oldInner = 286f - HudMetrics.CARD_PADDING_DP
            assertTrue(
                "英文本地时间行应当超过旧的 258 dp 上限，否则这条回归没有意义：${local.text}",
                lineCount(local.text, local.fontSizeSp, oldInner) >= 2
            )
            val newInner = HudMetrics.innerWidthDp(phoneWidthDp, panelWidthDp)
            assertTrue(lineCount(local.text, local.fontSizeSp, newInner) <= 1)
        }
    }

    @Test
    fun wrappingIsAllowedInsteadOfEllipsis() {
        assertTrue("三行文本至少允许两行，否则长文案只能截断", HudMetrics.MAX_TEXT_LINES >= 2)
        assertTrue("卡片必须留出与右栏的间隔", HudMetrics.PANEL_GAP_DP > 0f)
        assertTrue("卡片内边距参与可用宽度计算", HudMetrics.CARD_PADDING_DP > 0f)
    }
}
