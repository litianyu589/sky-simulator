package com.skysim.app

import com.skysim.app.core.Planets
import com.skysim.app.core.TimeSystem
import com.skysim.app.ui.Str
import com.skysim.app.ui.UiLang
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class V093LanguageTest {
    @Test
    fun englishUiAndAstronomicalNamesAreLocalized() {
        val previous = Str.lang
        try {
            Str.lang = UiLang.EN
            assertEquals("Speed", Str.t("app.speed"))
            assertTrue(Str.t("time.slider", "04:00:00", "3600").contains("1 local hour"))
            assertEquals("Mars", Str.planetName(Planets.byId("mars")))
            assertEquals("Clear", Str.tr("晴"))
            assertEquals("1 s = 1 minute", Str.speedDescription(60.0))
            assertTrue(Str.timeNotes().startsWith("Time model notes"))
        } finally {
            Str.lang = previous
        }
    }

    @Test
    fun japaneseUiAndPlanetNamesAreLocalized() {
        val previous = Str.lang
        try {
            Str.lang = UiLang.JA
            assertEquals("再生速度", Str.t("app.speed"))
            assertEquals("火星", Str.planetName(Planets.byId("mars")))
            assertEquals("日の出", Str.t("btn.sunrise"))
            assertEquals("1秒 = 1分", Str.speedDescription(60.0))
            assertTrue(Str.timeNotes().startsWith("時刻モデルについて"))
        } finally {
            Str.lang = previous
        }
    }

    @Test
    fun localCalendarFormattingChangesWithLanguage() {
        val previous = Str.lang
        try {
            val mars = Planets.byId("mars")
            val stamp = TimeSystem.localStamp(mars, 1_700_000_000L)
            Str.lang = UiLang.EN
            assertTrue(Str.calendarText(stamp).startsWith("Year "))
            Str.lang = UiLang.JA
            assertTrue(Str.calendarText(stamp).contains("年"))
        } finally {
            Str.lang = previous
        }
    }

    @Test
    fun unknownSpecializedTermsRemainVisibleInsteadOfBlank() {
        val previous = Str.lang
        try {
            Str.lang = UiLang.EN
            val original = "未收录的专有名词"
            assertEquals(original, Str.tr(original))
            assertFalse(Str.tr("日出锚定").isBlank())
        } finally {
            Str.lang = previous
        }
    }
}
