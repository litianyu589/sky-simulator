package com.skysim.app.data

import android.content.Context
import com.skysim.app.core.EffectId
import com.skysim.app.core.EffectState
import com.skysim.app.core.FxSetting
import com.skysim.app.core.PlaybackClock
import com.skysim.app.core.SceneOptions
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.WeatherState

/**
 * 效果开关、强度、天气档位与播放倍速的持久化（SharedPreferences，完全本地）。
 * 键名保持稳定，方便以后版本继续读旧设置。
 */
class EffectPrefs(context: Context) {

    private val sp = context.applicationContext.getSharedPreferences("skysim.prefs", Context.MODE_PRIVATE)

    fun load(): EffectState {
        val settings = EffectId.values().associateWith { id ->
            val def = EffectState.defaults()[id] ?: FxSetting()
            FxSetting(
                enabled = sp.getBoolean("fx.$id.enabled", def.enabled),
                strength = sp.getFloat("fx.$id.strength", def.strength).coerceIn(0f, 1f)
            )
        }
        val weather = WeatherState(
            cloudLevel = sp.getInt("wx.cloud", 1).coerceIn(0, 3),
            precipLevel = sp.getInt("wx.precip", 0).coerceIn(0, 3),
            auto = sp.getBoolean("wx.auto", false)
        )
        return EffectState(settings = settings, weather = weather)
    }

    fun save(state: EffectState) {
        sp.edit().apply {
            state.settings.forEach { (id, s) ->
                putBoolean("fx.$id.enabled", s.enabled)
                putFloat("fx.$id.strength", s.strengthClamped())
            }
            putInt("wx.cloud", state.weather.cloudLevel.coerceIn(0, 3))
            putInt("wx.precip", state.weather.precipLevel.coerceIn(0, 3))
            putBoolean("wx.auto", state.weather.auto)
        }.apply()
    }

    fun loadScene(): SceneOptions {
        val q = sp.getInt("scene.quality", TerrainQuality.MEDIUM.ordinal)
        return SceneOptions(
            twilightOn = sp.getBoolean("scene.twilight", true),
            twilightStrength = sp.getFloat("scene.twilightStrength", 0.65f).coerceIn(0f, 1f),
            terrainOn = sp.getBoolean("scene.terrain", true),
            shadowStrength = sp.getFloat("scene.shadow", 0.65f).coerceIn(0f, 1f),
            vegetation = sp.getFloat("scene.veg", 0.35f).coerceIn(0f, 1f),
            quality = TerrainQuality.values()[q.coerceIn(0, TerrainQuality.values().size - 1)]
        )
    }

    fun saveScene(s: SceneOptions) {
        val c = s.clamped()
        sp.edit().apply {
            putBoolean("scene.twilight", c.twilightOn)
            putFloat("scene.twilightStrength", c.twilightStrength)
            putBoolean("scene.terrain", c.terrainOn)
            putFloat("scene.shadow", c.shadowStrength)
            putFloat("scene.veg", c.vegetation)
            putInt("scene.quality", c.quality.ordinal)
        }.apply()
    }

    // v0.9.2：默认值改为「1× 原速」的实际下标（不再写死 2，避免扩档后默认落到慢放）
    fun loadSpeedIndex(): Int = sp.getInt("play.speedIdx", PlaybackClock.DEFAULT_SPEED_INDEX)
        .coerceIn(0, PlaybackClock.SPEEDS.lastIndex)
    fun saveSpeedIndex(idx: Int) = sp.edit().putInt("play.speedIdx", idx).apply()

    fun loadLanguageCode(): String = sp.getString("ui.language", "zh") ?: "zh"

    fun saveLanguageCode(code: String) = sp.edit().putString("ui.language", code).apply()

    // v0.8.5：HUD 调试读数（帧间隔 / P95 / 分段耗时）收进 Debug 开关。
    // 默认必须是关闭 —— 正式版不需要这些橙字。
    fun loadDebugReadout(): Boolean = sp.getBoolean("hud.debug", false)
    fun saveDebugReadout(on: Boolean) = sp.edit().putBoolean("hud.debug", on).apply()
}
