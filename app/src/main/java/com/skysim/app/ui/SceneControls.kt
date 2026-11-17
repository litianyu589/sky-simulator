package com.skysim.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import com.skysim.app.core.EffectState
import com.skysim.app.core.Planet
import com.skysim.app.core.SceneOptions
import com.skysim.app.core.SkyGeo
import com.skysim.app.core.Terrain
import com.skysim.app.core.TerrainQuality
import com.skysim.app.core.TwilightModel
import kotlin.math.roundToInt

/**
 * 场景面板：晨昏分层 + 地形光影（均为 v0.6.4 实验性）。
 * 「对比」按钮一键在分层模型与 v0.6.3 的简化渐变之间切换，方便真机比较。
 */
@Composable
fun SceneSection(
    planet: Planet,
    geo: SkyGeo,
    scene: SceneOptions,
    fx: EffectState,
    onSceneChange: (SceneOptions) -> Unit
) {
    val profile = Terrain.profileOf(planet)
    SectionLabel("晨昏与光影（实验性）")
    Column(Modifier.fillMaxWidth().padding(vertical = 1.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chip("晨昏分层", scene.twilightOn, Modifier.width(74.dp)) {
                onSceneChange(scene.copy(twilightOn = !scene.twilightOn))
            }
            Spacer(Modifier.width(6.dp))
            Slider(
                value = scene.twilightStrength,
                onValueChange = { onSceneChange(scene.copy(twilightStrength = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = if (scene.twilightOn) Accent else Color(0xFF66707C),
                    activeTrackColor = if (scene.twilightOn) Accent else Color(0xFF3A434E),
                    inactiveTrackColor = Color(0xFF2A323B)
                )
            )
            Spacer(Modifier.width(6.dp))
            Text("${(scene.twilightStrength * 100).roundToInt()}%", color = TextDim, fontSize = 9.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chip(
                if (scene.twilightOn) "对比：简化" else "对比：分层",
                false, Modifier.width(96.dp)
            ) { onSceneChange(scene.copy(twilightOn = !scene.twilightOn)) }
            Spacer(Modifier.width(6.dp))
            Text(
                Str.tr(if (scene.twilightOn) "橙红→橙黄→粉紫→深蓝的四段分层" else "v0.6.3 的整体渐变（基线）"),
                color = TextDim, fontSize = 9.sp
            )
        }
        TwilightBandRows(planet, scene.twilightStrength)
        Spacer(Modifier.height(6.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Chip("地形光影", scene.terrainOn && profile != null, Modifier.width(74.dp)) {
                onSceneChange(scene.copy(terrainOn = !scene.terrainOn))
            }
            Spacer(Modifier.width(6.dp))
            Slider(
                value = scene.shadowStrength,
                onValueChange = { onSceneChange(scene.copy(shadowStrength = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                colors = SliderDefaults.colors(
                    thumbColor = if (scene.terrainOn) Accent else Color(0xFF66707C),
                    activeTrackColor = if (scene.terrainOn) Accent else Color(0xFF3A434E),
                    inactiveTrackColor = Color(0xFF2A323B)
                )
            )
            Spacer(Modifier.width(6.dp))
            Text("${(scene.shadowStrength * 100).roundToInt()}%", color = TextDim, fontSize = 9.sp)
        }
        if (profile == null) {
            val note = when {
                planet.isStation -> "　空间站：无地面，纯黑天空"
                com.skysim.app.core.Ground3D.isPseudoGround(planet) ->
                    "　云顶天体：改用半透明伪地面（云带），不绘制固体地面"
                else -> "　该天体没有固体地面，地形与阴影不适用"
            }
            Text(Str.terrainNote(note), color = Color(0x88FFB37A), fontSize = 8.sp)
        } else {
            Text(
                Str.terrainDescription(profile),
                color = Color(0x66FFFFFF), fontSize = 8.sp
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Str.tr("植被"), color = TextDim, fontSize = 10.sp, modifier = Modifier.width(30.dp))
            Slider(
                value = scene.vegetation,
                onValueChange = { onSceneChange(scene.copy(vegetation = it)) },
                valueRange = 0f..1f,
                modifier = Modifier.weight(1f),
                enabled = profile?.trees == true && scene.terrainOn,
                colors = SliderDefaults.colors(
                    thumbColor = if (profile?.trees == true && scene.terrainOn) Accent else Color(0xFF66707C),
                    activeTrackColor = Color(0xFF3A434E),
                    inactiveTrackColor = Color(0xFF2A323B)
                )
            )
            Spacer(Modifier.width(6.dp))
            Text("${(scene.vegetation * 100).roundToInt()}%", color = TextDim, fontSize = 9.sp)
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Str.tr("画质"), color = TextDim, fontSize = 10.sp, modifier = Modifier.width(30.dp))
            TerrainQuality.values().forEachIndexed { i, q ->
                Chip(q.labelZh, scene.quality == q, Modifier.weight(1f)) {
                    onSceneChange(scene.copy(quality = q))
                }
                if (i < TerrainQuality.values().size - 1) Spacer(Modifier.width(4.dp))
            }
        }
        val sug = if (geo.sunAltDeg < -0.5f && geo.sunAltDeg > -12f) "　当前太阳高度适合观察维纳斯带与地影" else ""
        if (sug.isNotEmpty()) Text(sug, color = Color(0x99A8D4FF), fontSize = 8.sp)
        Text(
            Str.tr("性能提示：画质档决定高度场列数与射线步数；低档在 3600× 播放时最稳。"),
            color = Color(0x66FFFFFF), fontSize = 8.sp
        )
    }
}


/**
 * v0.8.0：把该天体的晨昏分层直接列出来。
 *
 * 每一行的色块是该带中点的实际天空色（朝太阳一侧），右边是角度区间与成因，
 * 所以地球、火星、金星的差别是一眼能看出来的数据，而不是「感觉不一样」。
 */
@Composable
private fun TwilightBandRows(planet: Planet, strength: Float) {
    val bands = TwilightModel.bands(planet, strength)
    val stage = TwilightModel.stage(planet)
    if (bands.isEmpty()) {
        Text(
            Str.noAtmosphereTwilight(stage.opacity),
            color = Color(0x88FFB37A), fontSize = 8.sp
        )
        return
    }
    if (!TwilightModel.hasDiscreteBands(planet)) {
        val b = bands.first()
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(ChipColor(b.towardSun)))
            Spacer(Modifier.width(5.dp))
            Text(
                "${Str.tr(b.labelZh)} 0°→${"%.0f".format(java.util.Locale.ROOT, b.toDeg)}°  ${Str.twilightNote(b.noteZh)}",
                color = Color(0x99FFFFFF), fontSize = 8.sp
            )
        }
        return
    }
    for (b in bands) {
        Row(Modifier.fillMaxWidth().padding(vertical = 1.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).background(ChipColor(b.towardSun)))
            Spacer(Modifier.width(5.dp))
            Text(
                "%.1f°→%.1f°　%s　".format(java.util.Locale.ROOT, b.fromDeg, b.toDeg, Str.tr(b.labelZh)),
                color = Color(0xCCE6EEFA), fontSize = 8.sp
            )
            Text(
                Str.twilightNote(b.noteZh),
                color = TextDim, fontSize = 8.sp
            )
        }
    }
}

/** 线性 RGB → Compose Color（[com.skysim.app.core.SkyModel.toSrgb] 之后的值） */
private fun ChipColor(c: com.skysim.app.core.Rgb): Color =
    Color(c.r.coerceIn(0f, 1f), c.g.coerceIn(0f, 1f), c.b.coerceIn(0f, 1f))
