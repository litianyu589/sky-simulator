package com.skysim.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skysim.app.core.EffectId
import com.skysim.app.core.EffectState
import com.skysim.app.core.FxRules
import com.skysim.app.core.FxSetting
import com.skysim.app.core.Planet
import com.skysim.app.core.SkyGeo
import com.skysim.app.core.WeatherState
import kotlin.math.roundToInt

/** 每个效果在当前星球 / 时间 / 天气下是否真的可能被看到。 */
internal fun effectApplicable(
    id: EffectId,
    planet: Planet,
    latitudeDeg: Float,
    geo: SkyGeo,
    weather: WeatherState
): Boolean = when (id) {
    EffectId.CLOUDS -> FxRules.cloudCapability(planet) > 0f
    EffectId.RAIN -> FxRules.rainCapability(planet) > 0f && weather.precipLevel > 0
    EffectId.RAINBOW -> FxRules.rainbowVisible(planet, geo.sunAltDeg, weather.precipIntensity)
    EffectId.HALO -> FxRules.haloVisible(planet, geo.sunAltDeg, weather.cloudCover)
    EffectId.AURORA -> FxRules.auroraVisible(planet, latitudeDeg, geo.sunAltDeg)
    EffectId.HAZE -> FxRules.hazeCapability(planet) > 0.2f
    EffectId.LIGHTNING -> FxRules.lightningVisible(planet, weather.precipLevel)
}

/**
 * 效果面板：天气分档 + 每个效果一个开关和强度滑杆。
 * 条件不满足时给出「当前不可见」提示，但开关仍然保留（切到合适的星球/时刻就会显示）。
 */
@Composable
fun EffectsSection(
    planet: Planet,
    latitudeDeg: Float,
    geo: SkyGeo,
    fx: EffectState,
    onChange: (EffectState) -> Unit
) {
    val weather = fx.weather
    SectionLabel("效果（${fx.enabledCount()} 个已开）")

    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Str.tr("云量"), color = TextDim, fontSize = 10.sp, modifier = Modifier.width(30.dp))
        WeatherState.CLOUD_NAMES.forEachIndexed { i, name ->
            Chip(name, weather.cloudLevel == i, Modifier.weight(1f)) {
                onChange(fx.withWeather(weather.copy(cloudLevel = i, auto = false)))
            }
            if (i < 3) Spacer(Modifier.width(4.dp))
        }
    }
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(Str.tr("降水"), color = TextDim, fontSize = 10.sp, modifier = Modifier.width(30.dp))
        WeatherState.PRECIP_NAMES.forEachIndexed { i, name ->
            Chip(name, weather.precipLevel == i, Modifier.weight(1f)) {
                onChange(fx.withWeather(weather.copy(precipLevel = i, auto = false)))
            }
            if (i < 3) Spacer(Modifier.width(4.dp))
        }
    }
    Spacer(Modifier.height(5.dp))
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Chip("自动演变", weather.auto, Modifier.weight(1f)) {
            onChange(fx.withWeather(weather.copy(auto = !weather.auto)))
        }
        Spacer(Modifier.width(6.dp))
        Text(
            Str.tr(if (weather.auto) "随本地时间缓慢变化" else "手动设定"),
            color = TextDim, fontSize = 9.sp
        )
    }

    Spacer(Modifier.height(8.dp))
    for (id in EffectId.values()) {
        val s = fx.setting(id)
        val ok = effectApplicable(id, planet, latitudeDeg, geo, weather)
        Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Chip(id.labelZh, s.enabled, Modifier.width(74.dp)) {
                    onChange(fx.with(id, s.copy(enabled = !s.enabled)))
                }
                Spacer(Modifier.width(6.dp))
                Slider(
                    value = s.strengthClamped(),
                    onValueChange = { v -> onChange(fx.with(id, s.copy(strength = v))) },
                    valueRange = 0f..1f,
                    modifier = Modifier.weight(1f),
                    colors = SliderDefaults.colors(
                        thumbColor = if (s.enabled) Accent else Color(0xFF66707C),
                        activeTrackColor = if (s.enabled) Accent else Color(0xFF3A434E),
                        inactiveTrackColor = Color(0xFF2A323B)
                    )
                )
                Spacer(Modifier.width(6.dp))
                Text("${(s.strengthClamped() * 100).roundToInt()}%", color = TextDim, fontSize = 9.sp)
            }
            if (s.enabled && !ok) {
                Text("${Str.tr("当前条件不满足：")}${Str.effectHint(id)}", color = Color(0x88FFB37A), fontSize = 8.sp)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    Text(
        Str.tr("提示：云、雨、极光都随模拟时间推进；倍速播放时它们也按倍速加快。"),
        color = Color(0x66FFFFFF), fontSize = 8.sp
    )
}
