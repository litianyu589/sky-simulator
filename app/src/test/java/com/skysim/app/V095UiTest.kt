package com.skysim.app

import com.skysim.app.ui.Str
import com.skysim.app.ui.UiLang
import org.junit.Assert.assertEquals
import org.junit.Test

/** v0.9.5 UI redesign: the new quick-control labels must remain localized. */
class V095UiTest {
    @Test fun newConsoleActionsAreLocalized() {
        val old = Str.lang
        try {
            Str.lang = UiLang.EN
            assertEquals("OBSERVATORY CONSOLE", Str.tr("观测控制台"))
            assertEquals("Reset view", Str.tr("回正视角"))
            // v0.9.5.1：按钮改名「压到地平线」——它只把高度压到 0°，不是回到默认 16°，
            // 原名「水平归零」容易被读成恢复默认，故此处断言同步改名后的译文。
            assertEquals("Level to horizon", Str.tr("压到地平线"))
            assertEquals("Live", Str.tr("实时"))
            assertEquals("UI language", Str.tr("界面语言"))
            Str.lang = UiLang.JA
            assertEquals("観測コンソール", Str.tr("观测控制台"))
            assertEquals("視点をリセット", Str.tr("回正视角"))
            assertEquals("地平線まで下げる", Str.tr("压到地平线"))
            assertEquals("リアルタイム", Str.tr("实时"))
            assertEquals("UI言語", Str.tr("界面语言"))
        } finally {
            Str.lang = old
        }
    }
}
