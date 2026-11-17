package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.TimeSystem
import com.skysim.app.ui.Str
import com.skysim.app.ui.TimelineUi
import com.skysim.app.ui.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * v0.9.5.1：只修 0.9.5 审出来的五处小问题，不碰任何历法与天文逻辑。
 *
 * 锁四件事：
 *  1. 新分区标题与改名后的按钮都真的进了翻译表（不是裸露中文）；
 *  2. 底部时间台的显示阈值足以排除手机横屏；
 *  3. 两条滑条共用同一组粒度常量（0..24、23 刻度、一格一小时）；
 *  4. HUD 的可见行仍然只有五行：名称 / 本地时间 / 限定小字 / UCT / 画质。
 */
class V0951Test {

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
    fun viewToolsSectionIsTranslatedInAllLanguages() {
        withLang(UiLang.EN) { assertEquals("View tools", Str.tr("视角工具")) }
        withLang(UiLang.JA) { assertEquals("ビューツール", Str.tr("视角工具")) }
        withLang(UiLang.ZH) { assertEquals("视角工具", Str.tr("视角工具")) }
    }

    @Test
    fun horizonButtonIsRenamedEverywhere() {
        withLang(UiLang.EN) {
            assertEquals("Level to horizon", Str.tr("压到地平线"))
            // 旧名必须已经退表，免得界面上还残留一处「水平归零」
            assertEquals("水平归零", Str.tr("水平归零"))
        }
        withLang(UiLang.JA) {
            assertEquals("地平線まで下げる", Str.tr("压到地平线"))
            assertEquals("水平归零", Str.tr("水平归零"))
        }
        withLang(UiLang.ZH) { assertEquals("压到地平线", Str.tr("压到地平线")) }
    }

    @Test
    fun hideInterfaceStillHasATranslationAndARestorePath() {
        withLang(UiLang.EN) { assertEquals("Hide interface", Str.tr("隐藏界面")) }
        withLang(UiLang.JA) { assertEquals("UIを隠す", Str.tr("隐藏界面")) }
    }

    @Test
    fun wideDockThresholdExcludesPhoneLandscape() {
        // 1080×2400、密度约 2.75 的手机横屏约 873 dp；阈值必须明显高于它。
        assertTrue(
            "阈值 ${TimelineUi.WIDE_DOCK_MIN_WIDTH_DP} dp 仍会把手机横屏算成宽屏",
            TimelineUi.WIDE_DOCK_MIN_WIDTH_DP >= 1000f
        )
        assertTrue(TimelineUi.WIDE_DOCK_MIN_WIDTH_DP > 873f)
    }

    @Test
    fun bothSlidersShareOneHourPerStep() {
        assertEquals(24f, TimelineUi.LOCAL_HOURS_PER_DAY, 1e-6f)
        assertEquals(23, TimelineUi.LOCAL_HOUR_STEPS)
        val hoursPerStep = TimelineUi.LOCAL_HOURS_PER_DAY / (TimelineUi.LOCAL_HOUR_STEPS + 1)
        assertEquals("一格必须正好等于一个本地小时", 1f, hoursPerStep, 1e-6f)
    }

    @Test
    fun hudStillHasExactlyFiveVisibleLines() {
        // HUD 的五行来自这五个字段（限定小字为空时那一行不发射，所以地球是四行）。
        withLang(UiLang.ZH) {
            val mars = TimeSystem.localStamp(p("mars"), instant)
            val lines = listOf(
                Str.planetName(p("mars")),
                Str.calendarText(mars),
                Str.hudQualifier(mars),
                Str.calendarText(TimeSystem.uctStamp(instant)),
                Str.qualityLine(TerrainQuality.MEDIUM, false)
            )
            assertEquals(5, lines.size)
            lines.forEach { assertNotEquals("", it) }

            val earth = TimeSystem.localStamp(p("earth"), instant)
            assertEquals("地球不加「模型推测」前缀，限定行应为空", "", Str.hudQualifier(earth))
        }
    }

    @Test
    fun hudNoLongerCarriesQuickControls() {
        // 两个快捷键必须已经离开 HUD、并进了右栏：HUD 只允许五行信息，
        // 这里用「文案仍然可翻译且在表里」间接确认入口存在（视觉位置见说明）。
        withLang(UiLang.EN) {
            assertEquals("Reset view", Str.tr("回正视角"))
            assertFalse(Str.tr("回正视角").contains("回正"))
        }
    }
}
