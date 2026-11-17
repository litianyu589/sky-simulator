package com.skysim.app.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.skysim.app.core.Planet
import com.skysim.app.core.Star
import com.skysim.app.core.StarData
import com.skysim.app.core.LocalStamp
import com.skysim.app.core.CalendarChain
import com.skysim.app.core.DayScheme
import com.skysim.app.core.PhaseSource

/** App-wide UI language. Chinese is the source language; English and Japanese are fully wired UI locales. */
enum class UiLang(val code: String, val labelNative: String) {
    ZH("zh", "简体中文"),
    EN("en", "English"),
    JA("ja", "日本語");

    companion object {
        fun fromCode(code: String): UiLang = values().firstOrNull { it.code == code } ?: ZH
    }
}

object Str {
    var lang by mutableStateOf(UiLang.ZH)

    private val en = mapOf(
        "倍速" to "Speed", "日期时间" to "Date & time", "🌏 语言" to "🌏 Language", "数据说明" to "Data notes", "显示" to "View",
        "显示与场景" to "View & scene", "当前：%s · %s" to "Current: %s · %s", "关闭" to "Close", "语言" to "Language",
        "语言设置会立即生效，并在下次启动时保留。" to "Language changes apply immediately and are saved for the next launch.",
        "本地时间轴（%s · 1 格 = 1 本地小时 = %s SI 秒）" to "Local timeline (%s · 1 step = 1 local hour = %s SI seconds)",
        "日期（UCT）" to "Date (UCT)", "日出" to "Sunrise", "正午" to "Noon", "日落" to "Sunset", "午夜" to "Midnight",
        "◀ 前一天" to "◀ Previous day", "现在" to "Now", "后一天 ▶" to "Next day ▶", "实时获取当前时间" to "Follow real time",
        "回到今天" to "Today", "此刻的天空" to "Sky right now", "太阳高度" to "Solar altitude", "太阳时角" to "Solar hour angle",
        "太阳赤纬" to "Solar declination", "白昼长度" to "Daylight duration", "日出 / 日落" to "Sunrise / sunset",
        "本地日长" to "Local day length", "本地年长" to "Local year length", "一年结构" to "Year structure", "进制" to "Time system",
        "相位" to "Phase", "轨道相位" to "Orbital phase", "向阳面 / 背阳面" to "Day side / night side", "本地昼夜" to "Local day/night",
        "倍速只影响模拟时间的推进速度，不改变任何历法、相位或日长常数。" to "Playback speed changes only how quickly simulated time advances; it does not alter calendars, phases, or day-length constants.",
        "日期与时间" to "Date & time", "显示与场景" to "View & scene", "语言" to "Language",
        "范围 %s · 万年后位置仅作示意" to "Range %s · positions far in the future are illustrative",
        "无：太阳固定在天空，时角不变" to "None: the Sun is fixed in the sky; hour angle does not change",
        "发散（同步锁定，无昼夜循环）" to "Divergent (synchronous lock; no day/night cycle)",
        "不存在：晨昏线固定在一条经线上" to "Unavailable: the terminator is fixed to one meridian",
        "永久正午 / 永久午夜" to "Permanent noon / permanent midnight", "极昼 24 小时" to "Polar day (24 hours)",
        "极夜 0 小时" to "Polar night (0 hours)", "太阳不落（极昼）" to "Sun does not set (polar day)",
        "太阳不升（极夜）" to "Sun does not rise (polar night)", "无解" to "No solution", "本地" to "local",
        "实测锚定（UT1 定义，相位 0）" to "Measured anchor (UT1 definition, phase 0)", "实测（2015 自转要素，纪元瞬间本地时 %.5f 日）" to "Measured (2015 rotation elements; local time at epoch %.5f days)",
        "约定值：按 IAU 2015 平均自转速率外推，非实测，相位实质不可定" to "Convention: extrapolated from IAU 2015 mean rotation rate; not measured, absolute phase is undetermined",
        "未定：没有可用的自转要素，读数按相位 0 起算" to "Undetermined: no usable rotation elements; readout starts at phase 0",
        "模型推测" to "Model estimate", "约定相位（设定值，非实测）" to "Convention phase (assumed, not measured)",
        "相位未定" to "Phase undetermined", "相位实测" to "Measured phase", "相位实测锚定" to "Measured phase anchor",
        "暂停" to "Pause", "播放" to "Play", "推进中" to "Advancing", "已暂停" to "Paused",
        "日出锚定" to "Sunrise-anchored", "物理轨迹" to "Physical path", "Debug 读数" to "Debug readout",
        "恒星目录" to "Star catalog", "关闭" to "Close", "← 恒星" to "← Stars", "行星" to "Planets", "卫星" to "Moons",
        "矮行星与小行星" to "Dwarf planets & asteroids", "推断 · 无大气实测" to "Inferred · no measured atmosphere",
        "完整翻译" to "Complete", "界面语言" to "UI language", "日长 %s" to "Day length %s", "恒星" to "Stars", "个天体" to "bodies", "颗恒星" to "stars",
        "仅恒星 · 空间站" to "Star only · station", "太阳系" to "Solar System", "光年" to "light-years",
        "半径" to "Radius", "参考层重力" to "Reference-level gravity", "表面重力" to "Surface gravity", "自转周期" to "Rotation period",
        "公转周期" to "Orbital period", "转轴倾角" to "Axial tilt", "参考层气压" to "Reference-level pressure", "气压" to "Pressure",
        "真空" to "Vacuum", "距母星" to "Distance from host star", "母星视直径" to "Host star angular diameter",
        "母星" to "Host star", "母星温度" to "Host star temperature", "母星视半径" to "Host star apparent radius",
        "伴星视直径" to "Companion angular diameter", "距地球" to "Distance from Earth", "平衡温度" to "Equilibrium temperature",
        "半径 / 质量" to "Radius / mass", "潮汐锁定" to "Tidally locked", "站位距离" to "Station distance",
        "站内时钟" to "Station clock", "本地历法" to "Local calendar", "本地秒长" to "Local second length",
        "日长" to "Day length", "小时" to "hours", "24 等分" to "24 equal divisions", "十进制粗分" to "decimal subdivisions", "SI 秒" to "SI seconds", "地球日" to "Earth days", "光年" to "light-years",
        "云量" to "Cloud cover", "降水" to "Precipitation", "自动演变" to "Auto evolve", "随本地时间缓慢变化" to "Changes slowly with local time",
        "手动设定" to "Manual", "效果" to "Effects", "当前条件不满足：" to "Conditions not met: ",
        "提示：云、雨、极光都随模拟时间推进；倍速播放时它们也按倍速加快。" to "Clouds, rain, and auroras evolve with simulated time and accelerate with playback speed.",
        "晨昏与光影（实验性）" to "Twilight & lighting (experimental)", "晨昏分层" to "Twilight layers", "对比：简化" to "Compare: simple",
        "对比：分层" to "Compare: layered", "地形光影" to "Terrain lighting", "植被" to "Vegetation", "画质" to "Quality",
        "低" to "Low", "中" to "Medium", "高" to "High", "无大气：太阳落山即夜，没有晨昏分层" to "No atmosphere: night begins at sunset; no twilight layers",
        "性能提示：画质档决定高度场列数与射线步数；低档在 3600× 播放时最稳。" to "Performance: quality controls terrain columns and ray-march steps; Low is most stable at 3600×.",
        "参考纬度（仅用于星空方位）" to "Reference latitude (sky orientation only)", "观测纬度" to "Observer latitude",
        "数据与规则" to "Data & model", "已回到今天 00:00:00 UCT" to "Returned to today at 00:00:00 UCT",
        "公元前" to "BCE ", "年" to " yr ", "月" to " mo ", "日" to " day ", "时" to "h ", "分" to "m ", "秒" to "s",
        "红矮星" to "red dwarf", "橙矮星" to "orange dwarf", "黄矮星" to "yellow dwarf", "白矮星" to "white dwarf",
        "蓝白星" to "blue-white star", "蓝巨星" to "blue giant", "红巨星" to "red giant", "超巨星" to "supergiant",
        "24 等分" to "24 equal divisions", "SI 秒" to "SI seconds", "十进制粗分" to "decimal subdivisions",
        "类地行星" to "terrestrial planet", "气态巨行星" to "gas giant", "冰巨星" to "ice giant", "矮行星" to "dwarf planet",
        "地球卫星" to "Earth moon", "木星卫星" to "Jupiter moon", "土星卫星" to "Saturn moon", "海王星卫星" to "Neptune moon",
        "空间站 · 观察点" to "Space station · observation point", "模型推测" to "Model estimate"
    )

    private val ja = mapOf(
        "倍速" to "再生速度", "日期时间" to "日時", "🌏 语言" to "🌏 言語", "数据说明" to "データ情報", "显示" to "表示",
        "显示与场景" to "表示とシーン", "当前：%s · %s" to "現在：%s · %s", "关闭" to "閉じる", "语言" to "言語",
        "语言设置会立即生效，并在下次启动时保留。" to "言語の変更はすぐに反映され、次回起動時にも保持されます。",
        "本地时间轴（%s · 1 格 = 1 本地小时 = %s SI 秒）" to "ローカル時間軸（%s · 1目盛 = ローカル1時間 = %s SI秒）",
        "日期（UCT）" to "日付（UCT）", "日出" to "日の出", "正午" to "正午", "日落" to "日の入り", "午夜" to "真夜中",
        "◀ 前一天" to "◀ 前日", "现在" to "現在", "后一天 ▶" to "翌日 ▶", "实时获取当前时间" to "現在時刻に追従",
        "回到今天" to "今日に戻る", "此刻的天空" to "現在の空", "太阳高度" to "太陽高度", "太阳时角" to "太陽時角",
        "太阳赤纬" to "太陽赤緯", "白昼长度" to "昼の長さ", "日出 / 日落" to "日の出 / 日の入り",
        "本地日长" to "現地の1日の長さ", "本地年长" to "現地の1年の長さ", "一年结构" to "1年の構造", "进制" to "時刻体系",
        "相位" to "位相", "轨道相位" to "軌道位相", "向阳面 / 背阳面" to "昼側 / 夜側", "本地昼夜" to "現地の昼夜",
        "倍速只影响模拟时间的推进速度，不改变任何历法、相位或日长常数。" to "再生速度はシミュレーション時間の進み方だけを変え、暦・位相・日の長さは変更しません。",
        "范围 %s · 万年后位置仅作示意" to "範囲 %s · 遠い未来の位置は模式図です",
        "无：太阳固定在天空，时角不变" to "なし：太陽は空に固定され、時角は変化しません",
        "推断 · 无大气实测" to "推定・大気の実測なし",
        "发散（同步锁定，无昼夜循环）" to "発散（同期固定・昼夜サイクルなし）",
        "不存在：晨昏线固定在一条经线上" to "該当なし：昼夜境界線は同じ経線上に固定",
        "无：太阳固定在天空，时角不变" to "なし：太陽は空に固定され、時角は変化しません",
        "推断 · 无大气实测" to "推定・大気の実測なし",
        "发散（同步锁定，无昼夜循环）" to "発散（同期固定・昼夜サイクルなし）",
        "不存在：晨昏线固定在一条经线上" to "該当なし：昼夜境界線は同じ経線上に固定",
        "永久正午 / 永久午夜" to "永久の正午 / 永久の真夜中", "极昼 24 小时" to "白夜（24時間）", "极夜 0 小时" to "極夜（0時間）",
        "太阳不落（极昼）" to "太陽が沈まない（白夜）", "太阳不升（极夜）" to "太陽が昇らない（極夜）", "无解" to "解なし", "本地" to "現地",
        "模型推测" to "モデル推定", "约定相位（设定值，非实测）" to "規約位相（仮定値・実測ではありません）", "相位未定" to "位相未確定",
        "相位实测" to "実測位相", "相位实测锚定" to "実測基準位相", "暂停" to "一時停止", "播放" to "再生",
        "推进中" to "進行中", "已暂停" to "一時停止中", "日出锚定" to "日の出基準", "物理轨迹" to "物理軌道",
        "完整翻译" to "完全対応", "界面语言" to "UI言語", "恒星目录" to "恒星カタログ", "← 恒星" to "← 恒星", "行星" to "惑星", "卫星" to "衛星", "矮行星与小行星" to "準惑星・小惑星",
        "推断 · 无大气实测" to "推定・大気の実測なし", "日长 %s" to "1日の長さ %s", "个天体" to "天体", "颗恒星" to "恒星",
        "仅恒星 · 空间站" to "恒星のみ・宇宙ステーション", "太阳系" to "太陽系", "光年" to "光年",
        "半径" to "半径", "参考层重力" to "基準高度の重力", "表面重力" to "表面重力", "自转周期" to "自転周期",
        "公转周期" to "公転周期", "转轴倾角" to "自転軸傾斜", "参考层气压" to "基準高度の気圧", "气压" to "気圧",
        "真空" to "真空", "距母星" to "主星からの距離", "母星视直径" to "主星の視直径", "母星" to "主星",
        "母星温度" to "主星の温度", "母星视半径" to "主星の見かけの半径", "伴星视直径" to "伴星の視直径",
        "距地球" to "地球からの距離", "平衡温度" to "平衡温度", "半径 / 质量" to "半径 / 質量", "潮汐锁定" to "潮汐固定",
        "站位距离" to "ステーション軌道距離", "站内时钟" to "ステーション時計", "本地历法" to "現地暦", "本地秒长" to "現地の1秒の長さ",
        "日长" to "1日の長さ", "小时" to "時間", "地球日" to "地球日", "云量" to "雲量", "降水" to "降水",
        "自动演变" to "自動変化", "随本地时间缓慢变化" to "現地時間に合わせてゆっくり変化", "手动设定" to "手動設定",
        "晨昏与光影（实验性）" to "薄明と光影（実験機能）", "晨昏分层" to "薄明の層", "对比：简化" to "比較：簡易",
        "对比：分层" to "比較：多層", "地形光影" to "地形の光と影", "植被" to "植生", "画质" to "画質", "低" to "低", "中" to "中", "高" to "高",
        "参考纬度（仅用于星空方位）" to "基準緯度（星空の方位のみ）", "观测纬度" to "観測緯度", "数据与规则" to "データと規則",
        "公元前" to "紀元前", "年" to "年", "月" to "月", "日" to "日", "时" to "時", "分" to "分", "秒" to "秒",
        "24 等分" to "24等分", "SI 秒" to "SI秒", "十进制粗分" to "十進の簡易分割",
        "実測锚定（UT1 定义，相位 0）" to "実測基準（UT1定義・位相0）", "类地行星" to "地球型惑星", "气态巨行星" to "巨大ガス惑星", "冰巨星" to "巨大氷惑星", "矮行星" to "準惑星",
        "地球卫星" to "地球の衛星", "木星卫星" to "木星の衛星", "土星卫星" to "土星の衛星", "海王星卫星" to "海王星の衛星",
        "模型推测" to "モデル推定"
    )

    private val enExtra = mapOf(
        "切换 ▾" to "Change ▾", "完整翻译" to "Complete", "界面语言" to "UI language",
        "提示：在天空区域拖动可平移视角，点按天空可隐藏 / 唤出界面；隐藏后自动播放继续推进。" to "Drag the sky to pan the view. Tap the sky to hide or show the interface; playback continues while hidden.",
        "空间站视角" to "Station view", "天空采样密度（画质档）" to "Sky sampling density (quality)", "自动" to "Auto", "行 " to "Rows ", " 列 " to " · columns ",
        " 个采样点 · 覆盖 ±180°" to " samples · coverage ±180°", " · 已锁低档" to " · locked to Low", "潮汐锁定：观察面" to "Tidally locked: viewing side",
        "永昼面" to "Day side", "永夜面" to "Night side", "太阳轨迹" to "Solar path", "隐藏界面" to "Hide interface",
        "极昼" to "Polar day", "极夜" to "Polar night", "−1年" to "−1 yr", "−1月" to "−1 mo", "−1日" to "−1 day",
        "+1日" to "+1 day", "+1月" to "+1 mo", "+1年" to "+1 yr", "年" to "Year", "月" to "Month", "日" to "Day",
        "慢放" to "Slow", "原速" to "Real time", "1 秒 = " to "1 s = ", " 秒" to " s", " 分钟" to " min", " 小时" to " h", " 天" to " days",
        "太阳时角" to "Solar hour angle", "正午" to "noon", "午夜" to "midnight", "日长 " to "Day length ",
        "已钳制到 " to "Clamped to ", "无地面、无大气：拖动画面即可自由旋转观察恒星与星空，时间仍按 UCT 推进" to "No surface or atmosphere. Drag to freely rotate the view of the star and sky; time still advances in UCT.",
        "所属恒星：" to "Host star: ", "（" to " (", "）" to ")", "仅恒星 · 空间站" to "Star only · station",
        "太阳系：行星、卫星、矮行星三类用上面的标签切换" to "Solar System: switch between planets, moons, and dwarf planets using the tabs above",
        "该系统目前只有恒星、没有已知行星：提供空间站观察点（位于等效应日照距离）" to "This system has no known planets; a station observation point is provided at an equivalent-insolation distance.",
        "该系统有 " to "This system has ", " 个天体：多行星系统会全部列在这里" to " bodies; all bodies in multi-planet systems are listed here",
        "完整翻译" to "Complete", "界面语言" to "UI language", "切换 ▾" to "Change ▾", "参考层重力" to "Reference-level gravity", "表面重力" to "Surface gravity", "自转周期" to "Rotation period", "公转周期" to "Orbital period",
        "转轴倾角" to "Axial tilt", "参考层气压" to "Reference-level pressure", "气压" to "Pressure", "距母星" to "Distance from host star",
        "母星视直径" to "Host star angular diameter", "母星温度" to "Host star temperature", "母星视半径" to "Host star apparent radius",
        "双星系统 · " to "Binary system · ", "站位距离" to "Station distance", "直接使用 UCT（站不随行星自转）" to "Uses UCT directly (the station does not rotate with a planet)",
        "自转 = 公转，一面永昼一面永夜" to "Rotation = revolution; one hemisphere has permanent day and the other permanent night",
        "无大气：纯黑，只有恒星本体与星空" to "No atmosphere: a black sky with only the star and background stars",
        """时间换算说明
UCT（宇宙协调时间）是给人操作的统一刻度：一天 24 小时、60 分 60 秒，日期用公历。
星球本地时间用各自历法：一年包含的本地日数 = 公转周期 ÷ 真太阳日，取整；月份自动平均分配。
时间轴以“本地日”为刻度：一格 = 一个本地小时 = 日长 ÷ 24 个 SI 秒，拖完整个滑条正好看完该星球一次完整的昼夜循环；底层仍然只有一个 UCT 主变量，切换星球不会改变当前瞬间。
本地读数前面的“（模型推测）”表示这条读数的历法结构、进制与自转周期含自定约定（地球公历除外）；气态巨行星的相位是按 IAU 2015 平均自转速率外推的约定值，不是实测。
“物理轨迹”模式下，太阳时角随时角均匀变化，日出日落时刻随季节和纬度改变（这是极昼极夜的来源）；
“日出锚定”模式下，太阳的周日运动被压缩，使 UCT 06:00 / 18:00 恰好为日出 / 日落。
天空颜色由简化散射模型推导：瑞利散射（∝1/λ⁴）+ 气溶胶散射与消光，并针对部分天体加入视觉近似。它不是完整辐射传输模拟，也不代表每颗星球都有直接实测的地面天空照片。气态/冰巨行星采用云顶参考层，不存在可站立的固体地面。""" to """Time model notes
UCT (Universal Coordinated Time) is the shared human-readable clock: 24 hours per day, 60 minutes per hour, 60 seconds per minute, using the Gregorian calendar.
Each world uses its own local calendar. Local days per year are derived from orbital period divided by the true solar day and rounded; months are distributed automatically.
The timeline is divided into local days: each step is one local hour (day length ÷ 24 SI seconds). A full slider sweep covers one complete day/night cycle. Internally there is still one UCT time variable, so switching worlds does not change the current instant.
The “Model estimate” qualifier means the calendar structure, time system, or rotation phase includes assumptions (except Earth’s Gregorian calendar). Giant-planet phases are extrapolated from IAU 2015 mean rotation rates and are not direct measurements.
In “Physical path” mode, solar hour angle changes uniformly and sunrise/sunset vary with season and latitude, producing polar day and night.
In “Sunrise-anchored” mode, daily solar motion is compressed so that UCT 06:00 and 18:00 correspond to sunrise and sunset.
Sky colors use a simplified scattering model (Rayleigh scattering ∝1/λ⁴ plus aerosol scattering and extinction), with visual approximations for some worlds. This is not full radiative-transfer modeling or a claim that every world has directly observed ground-sky imagery. Gas and ice giants use a cloud-top reference level and have no solid surface to stand on."""
    )

    private val jaExtra = mapOf(
        "切换 ▾" to "切替 ▾", "完整翻译" to "対応済み", "语言设置会立即生效，并在下次启动时保留。" to "言語の変更はすぐに反映され、次回起動時にも保持されます。", "空间站视角" to "ステーション視点",
        "天空采样密度（画质档）" to "空のサンプリング密度（画質）", "自动" to "自動", "永昼面" to "昼側", "永夜面" to "夜側",
        "太阳轨迹" to "太陽の軌跡", "隐藏界面" to "UIを隠す", "极昼" to "白夜", "极夜" to "極夜",
        "年" to "年", "月" to "月", "日" to "日", "慢放" to "スロー", "原速" to "通常速度", "日长 " to "1日の長さ ",
        "无地面、无大气：拖动画面即可自由旋转观察恒星与星空，时间仍按 UCT 推进" to "地表も大気もありません。画面をドラッグして恒星と星空を自由に回転して観察できます。時刻はUCTで進みます。",
        "提示：在天空区域拖动可平移视角，点按天空可隐藏 / 唤出界面；隐藏后自动播放继续推进。" to "空をドラッグすると視点を移動できます。空をタップするとUIを表示・非表示にします。非表示でも再生は続きます。",
        "太阳轨迹" to "太陽の軌跡", "潮汐锁定：观察面" to "潮汐固定：観測面", "对比：简化" to "比較：簡易", "对比：分层" to "比較：多層",
        "晨昏与光影（实验性）" to "薄明と光影（実験機能）", "性能提示：画质档决定高度场列数与射线步数；低档在 3600× 播放时最稳。" to "性能：画質設定は地形列数とレイマーチ回数を制御します。3600倍再生では低設定が最も安定します。",
        "−1年" to "−1年", "−1月" to "−1か月", "−1日" to "−1日", "+1日" to "+1日", "+1月" to "+1か月", "+1年" to "+1年",
        """时间换算说明
UCT（宇宙协调时间）是给人操作的统一刻度：一天 24 小时、60 分 60 秒，日期用公历。
星球本地时间用各自历法：一年包含的本地日数 = 公转周期 ÷ 真太阳日，取整；月份自动平均分配。
时间轴以“本地日”为刻度：一格 = 一个本地小时 = 日长 ÷ 24 个 SI 秒，拖完整个滑条正好看完该星球一次完整的昼夜循环；底层仍然只有一个 UCT 主变量，切换星球不会改变当前瞬间。
本地读数前面的“（模型推测）”表示这条读数的历法结构、进制与自转周期含自定约定（地球公历除外）；气态巨行星的相位是按 IAU 2015 平均自转速率外推的约定值，不是实测。
“物理轨迹”模式下，太阳时角随时角均匀变化，日出日落时刻随季节和纬度改变（这是极昼极夜的来源）；
“日出锚定”模式下，太阳的周日运动被压缩，使 UCT 06:00 / 18:00 恰好为日出 / 日落。
天空颜色由简化散射模型推导：瑞利散射（∝1/λ⁴）+ 气溶胶散射与消光，并针对部分天体加入视觉近似。它不是完整辐射传输模拟，也不代表每颗星球都有直接实测的地面天空照片。气态/冰巨行星采用云顶参考层，不存在可站立的固体地面。""" to """時刻モデルについて
UCT（協定宇宙時）は操作用の共通時刻です。1日は24時間、1時間は60分、1分は60秒で、日付にはグレゴリオ暦を使います。
各天体の現地時刻はそれぞれの暦を使います。1年の現地日数は公転周期を真太陽日で割って丸め、月は自動的に配分されます。
タイムラインは現地日を基準とし、1目盛は現地1時間（日の長さ÷24 SI秒）です。スライダー全体で昼夜1周期を確認できます。内部ではUCT変数を1つだけ使うため、天体を切り替えても同じ瞬間を保ちます。
「モデル推定」は暦・時刻体系・自転位相に仮定が含まれることを示します（地球のグレゴリオ暦を除く）。巨大惑星の位相はIAU 2015平均自転速度からの外挿で、直接測定値ではありません。
「物理軌道」モードでは太陽時角が均等に変化し、日の出・日の入りは季節と緯度によって変わります。これが白夜と極夜の原因です。
「日の出基準」モードでは日周運動を圧縮し、UCT 06:00 / 18:00を日の出・日の入りに合わせます。
空の色は簡略化した散乱モデル（レイリー散乱 ∝1/λ⁴ とエアロゾル散乱・消光）から導出し、一部の天体には視覚的近似を加えています。完全な放射輸送シミュレーションではなく、すべての天体で地表の空が直接観測されたことを意味しません。巨大ガス惑星・巨大氷惑星は雲頂を基準面とし、立てる固体地表はありません。"""
    )

    private val enUiExtra = mapOf(
        "云量" to "Cloud cover", "降水" to "Precipitation", "效果" to "Effects", "个已开" to " enabled",
        "晴" to "Clear", "少云" to "Few clouds", "多云" to "Cloudy", "阴" to "Overcast", "无" to "None", "小雨" to "Light rain", "中雨" to "Moderate rain", "暴雨" to "Heavy rain",
        "云层" to "Clouds", "需要大气；气态巨行星当作云顶" to "Requires an atmosphere; gas giants use a cloud-top reference", "彩虹" to "Rainbow",
        "需要降水 + 太阳高度 2°–42°" to "Requires precipitation and solar altitude of 2°–42°", "日晕/幻日" to "Halo / sundogs",
        "需要薄云（卷云）+ 太阳在地平线上" to "Requires thin cirrus and the Sun above the horizon", "极光" to "Aurora",
        "需要磁场 + 高纬度 + 夜空" to "Requires a magnetic field, high latitude, and night", "霾/沙尘" to "Haze / dust",
        "需要气溶胶或尘暴大气" to "Requires aerosols or a dusty atmosphere", "闪电" to "Lightning", "需要暴雨级降水" to "Requires heavy precipitation",
        "晨昏分层" to "Twilight layers", "地形光影" to "Terrain lighting", "橙红→橙黄→粉紫→深蓝的四段分层" to "Four layers: orange-red → amber → pink-purple → deep blue",
        "v0.6.3 的整体渐变（基线）" to "v0.6.3 overall gradient (baseline)", "无大气：太阳落山即夜，没有晨昏分层" to "No atmosphere: night begins at sunset; no twilight layers",
        "云顶天体：改用半透明伪地面（云带），不绘制固体地面" to "Cloud-top world: uses a translucent pseudo-surface (cloud bands), not solid ground",
        "该天体没有固体地面，地形与阴影不适用" to "This body has no solid surface; terrain and shadows do not apply",
        "植被" to "Vegetation", "山脉 + 海岸线" to "Mountains + coastlines", "坑洼粗糙" to "Cratered and rough", "高原 + 峡谷" to "Plateaus + canyons",
        "平缓" to "Smooth", "冰原起伏" to "Icy terrain", "不适用（无固体地面）" to "Not applicable (no solid surface)",
        "北 N" to "North N", "东 E" to "East E", "南 S" to "South S", "西 W" to "West W",
        "效果（" to "Effects (", " 个已开）" to " enabled)", "地平辉光带" to "Horizon glow band", "中层余晖带" to "Mid-level afterglow band", "暗蓝尾带" to "Deep-blue tail band", "整片云顶变暗" to "Cloud deck darkens as a whole",
        "自转与公转同步：太阳（母星）永远停在同一个位置，本地时刻冻结" to "Rotation and revolution are synchronized: the Sun (host star) stays fixed in the same position and local time is frozen",
        "观察基准" to "Observation reference", "真太阳日" to "True solar day", "大气" to "Atmosphere", "天空模型" to "Sky model", "天空" to "Sky",
        "恒星系统" to "Stellar system", "轨道周期" to "Orbital period", "所属恒星" to "Host star",
        "M 型红矮星" to "M-type red dwarf", "K 型橙矮星" to "K-type orange dwarf", "G 型黄矮星（太阳型）" to "G-type yellow dwarf (Sun-like)",
        "F 型白色主序星" to "F-type white main-sequence star", "A 型白色主序星" to "A-type white main-sequence star", "B 型蓝白星" to "B-type blue-white star",
        "大气：模型推测" to "Atmosphere: modeled", "大气：未知（按无大气处理）" to "Atmosphere: unknown (treated as airless)",
        "实测锚定（纪元瞬间本地时 %.5f 日）" to "Measured anchor (local time at epoch %.5f days)",
        "约定值：按 IAU 2015 平均自转速率外推，非实测，相位实质不可定" to "Convention: extrapolated from IAU 2015 mean rotation rate; not measured, absolute phase is undetermined",
        "未定：没有可用的自转要素，读数按相位 0 起算" to "Undetermined: no usable rotation elements; readout starts at phase 0",
        "双星说明" to "Binary-star note", "伴星位置为示意值，尚未进行实时双星轨道积分" to "Companion position is illustrative; real-time binary orbital integration is not implemented",
        "数据可信度" to "Data confidence", "大气证据" to "Atmosphere evidence", "补充推导依据" to "Additional model basis",
        "观察点" to "Observation point", "空间站（虚构观察点，非真实航天器）" to "Space station (fictional observation point, not a real spacecraft)",
        "本地历法" to "Local calendar", "本地秒长" to "Local second length", "1 年 = " to "1 year = ", " 个月" to " months",
        "太阳在地平线上" to "Sun above the horizon", "当前太阳高度适合观察维纳斯带与地影" to "Current solar altitude is suitable for observing the Belt of Venus and Earth's shadow",
        "　当前太阳高度适合观察维纳斯带与地影" to "Current solar altitude is suitable for observing the Belt of Venus and Earth's shadow",
        "　空间站：无地面，纯黑天空" to "Station: no surface; black sky", "　云顶天体：改用半透明伪地面（云带），不绘制固体地面" to "Cloud-top world: translucent pseudo-surface (cloud bands), no solid ground",
        "　该天体没有固体地面，地形与阴影不适用" to "No solid surface; terrain and shadows do not apply",
        "低" to "Low", "中" to "Medium", "高" to "High", "赤道" to "Equator", "北极" to "North Pole", "南极" to "South Pole",
        "观测控制台" to "OBSERVATORY CONSOLE", "视角工具" to "View tools", "回正视角" to "Reset view",
        "压到地平线" to "Level to horizon", "实时" to "Live",
        "空间站观察点（虚构）" to "Space-station observation point (fictional)", "云顶参考层（约 1 bar 示意，不是固体地面）" to "Cloud-top reference level (illustrative ~1 bar; not a solid surface)",
        "固体表面 / 近表面（视天体而定）" to "Solid surface / near-surface (depending on body)",
        "氮 78% · 氧 21%" to "Nitrogen 78% · Oxygen 21%", "几乎真空（外逸层）" to "Near vacuum (exosphere)",
        "二氧化碳 96.5% · 硫酸云" to "Carbon dioxide 96.5% · sulfuric-acid clouds", "二氧化碳 95% · 尘" to "Carbon dioxide 95% · dust",
        "氢 90% · 氦 10%" to "Hydrogen 90% · Helium 10%", "氢 96% · 氦 3%" to "Hydrogen 96% · Helium 3%",
        "氢 · 氦 · 甲烷" to "Hydrogen · helium · methane", "氮 95% · 甲烷 5%（橙色雾霾）" to "Nitrogen 95% · methane 5% (orange haze)",
        "氮 · 甲烷（极稀薄）" to "Nitrogen · methane (very tenuous)", "稀薄二氧化硫" to "Thin sulfur dioxide", "极稀薄氧气" to "Extremely tenuous oxygen",
        "稀薄氧气" to "Thin oxygen", "极稀薄大气" to "Extremely tenuous atmosphere", "稀薄氮气" to "Thin nitrogen",
        "大气：有观测证据" to "Atmosphere: observational evidence", "推断，无大气实测（模型假设）" to "Inferred; no measured atmosphere (model assumption)",
        "推断，无大气实测（按无大气处理）" to "Inferred; no measured atmosphere (treated as airless)",
        "几乎无大气：不形成蓝天；天空近黑，太阳盘按真实视直径缩放。" to "Almost no atmosphere: no blue sky; the sky is nearly black and the solar disc uses its true angular size.",
        "局部、瞬变 SO₂ 外逸层：不形成稳定全球天光，天空近黑。" to "Local, transient SO₂ exosphere: no stable global sky glow; the sky is nearly black.",
        "高压 CO₂ 与硫酸云：黄橙色厚雾的多次散射近似；太阳表现为低对比漫射光斑。" to "High-pressure CO₂ and sulfuric-acid clouds: multiple scattering in thick yellow-orange haze; the Sun appears as a low-contrast diffuse spot.",
        "氮氧大气：瑞利散射为主，叠加气溶胶与太阳高度角变化。" to "Nitrogen-oxygen atmosphere: dominated by Rayleigh scattering, with aerosols and solar-altitude effects.",
        "稀薄 CO₂ 与矿物尘埃：黄褐色天空；日落附近加入蓝色前向散射示意。" to "Thin CO₂ and mineral dust: yellow-brown sky, with illustrative blue forward scattering near sunset.",
        "氢氦大气与云顶气溶胶：乳白至浅黄云顶色调近似；无固体地面。" to "Hydrogen-helium atmosphere and cloud-top aerosols: off-white to pale-yellow cloud-top approximation; no solid surface.",
        "氢氦大气与高层雾霾：淡金色云顶近似；无固体地面。" to "Hydrogen-helium atmosphere and high-altitude haze: pale-gold cloud-top approximation; no solid surface.",
        "氢氦与甲烷大气：浅青色雾霾近似，未完整求解甲烷光谱吸收。" to "Hydrogen-helium and methane atmosphere: pale-cyan haze approximation; methane spectral absorption is not fully solved.",
        "氢氦与甲烷大气：较深蓝色云顶近似，未完整求解甲烷光谱吸收。" to "Hydrogen-helium and methane atmosphere: deep-blue cloud-top approximation; methane spectral absorption is not fully solved.",
        "氮甲烷厚雾：橙棕色多次散射与低空增暖近似。" to "Thick nitrogen-methane haze: orange-brown multiple scattering and near-horizon warming approximation.",
        "极稀薄外逸层：不形成普通天空散射，背景近黑。" to "Extremely tenuous exosphere: no ordinary sky scattering; nearly black background.",
        "大气性质未知：按保守参数进行示意，不代表已确认大气。" to "Atmosphere unknown: conservative illustrative parameters; not a confirmed atmosphere.",
        "根据当前大气参数进行简化散射计算；不是完整辐射传输模型。" to "Simplified scattering from current atmospheric parameters; not a full radiative-transfer model."
    )

    private val jaUiExtra = mapOf(
        "云量" to "雲量", "降水" to "降水", "效果" to "効果", "晴" to "快晴", "少云" to "晴れ時々曇り", "多云" to "曇り", "阴" to "曇天",
        "无" to "なし", "小雨" to "小雨", "中雨" to "雨", "暴雨" to "大雨", "云层" to "雲層", "彩虹" to "虹", "日晕/幻日" to "暈・幻日",
        "极光" to "オーロラ", "霾/沙尘" to "霞・砂塵", "闪电" to "雷", "晨昏分层" to "薄明の層", "地形光影" to "地形の光と影",
        "植被" to "植生", "山脉 + 海岸线" to "山脈 + 海岸線", "坑洼粗糙" to "クレーター地形", "高原 + 峡谷" to "高原 + 渓谷",
        "观测控制台" to "観測コンソール", "视角工具" to "ビューツール", "回正视角" to "視点をリセット",
        "压到地平线" to "地平線まで下げる", "实时" to "リアルタイム",
        "平缓" to "平坦", "冰原起伏" to "氷原の起伏", "不适用（无固体地面）" to "該当なし（固体地表なし）",
        "观察基准" to "観測基準", "真太阳日" to "真太陽日", "大气" to "大気", "天空模型" to "空のモデル", "天空" to "空",
        "恒星系统" to "恒星系", "轨道周期" to "公転周期", "所属恒星" to "主星",
        "双星说明" to "連星について", "伴星位置为示意值，尚未进行实时双星轨道积分" to "伴星の位置は模式値で、連星軌道のリアルタイム積分は行っていません",
        "数据可信度" to "データの確度", "大气证据" to "大気の根拠", "补充推导依据" to "追加の推定根拠", "观察点" to "観測地点",
        "空间站（虚构观察点，非真实航天器）" to "宇宙ステーション（架空の観測地点で、実在の宇宙機ではありません）",
        "本地历法" to "現地暦", "本地秒长" to "現地の1秒の長さ", "自转与公转同步：太阳（母星）永远停在同一个位置，本地时刻冻结" to "自転と公転が同期しているため、太陽（主星）は同じ位置に固定され、現地時刻は停止します",
        "效果（" to "効果（", " 个已开）" to "件有効）", "地平辉光带" to "地平線の光帯", "中层余晖带" to "中層の残光帯", "暗蓝尾带" to "濃い青の尾光帯", "整片云顶变暗" to "雲頂全体が暗くなる", "低" to "低", "中" to "中", "高" to "高",
        "M 型红矮星" to "M型赤色矮星", "K 型橙矮星" to "K型橙色矮星", "G 型黄矮星（太阳型）" to "G型黄色矮星（太陽型）",
        "F 型白色主序星" to "F型白色主系列星", "A 型白色主序星" to "A型白色主系列星", "B 型蓝白星" to "B型青白色星",
        "大气：模型推测" to "大気：モデル推定", "大气：未知（按无大气处理）" to "大気：不明（大気なしとして扱う）",
        "空间站观察点（虚构）" to "宇宙ステーション観測点（架空）", "云顶参考层（约 1 bar 示意，不是固体地面）" to "雲頂基準層（約1 barの模式値・固体地表ではありません）",
        "固体表面 / 近表面（视天体而定）" to "固体表面 / 表面付近（天体による）",
        "氮 78% · 氧 21%" to "窒素 78% · 酸素 21%", "几乎真空（外逸层）" to "ほぼ真空（外気圏）",
        "二氧化碳 96.5% · 硫酸云" to "二酸化炭素 96.5% · 硫酸雲", "二氧化碳 95% · 尘" to "二酸化炭素 95% · ダスト",
        "氢 90% · 氦 10%" to "水素 90% · ヘリウム 10%", "氢 96% · 氦 3%" to "水素 96% · ヘリウム 3%",
        "氢 · 氦 · 甲烷" to "水素 · ヘリウム · メタン", "氮 95% · 甲烷 5%（橙色雾霾）" to "窒素 95% · メタン 5%（橙色の霞）",
        "氮 · 甲烷（极稀薄）" to "窒素 · メタン（非常に希薄）", "稀薄二氧化硫" to "希薄な二酸化硫黄", "极稀薄氧气" to "極めて希薄な酸素",
        "稀薄氧气" to "希薄な酸素", "极稀薄大气" to "極めて希薄な大気", "稀薄氮气" to "希薄な窒素",
        "大气：有观测证据" to "大気：観測証拠あり", "推断，无大气实测（模型假设）" to "推定・大気の実測なし（モデル仮定）",
        "推断，无大气实测（按无大气处理）" to "推定・大気の実測なし（大気なしとして扱う）"
    )

    private val keyToZh = mapOf(
        "hud.uct" to "UCT: %s", "app.speed" to "倍速", "app.datetime" to "日期时间", "app.lang" to "🌏 语言",
        "app.info" to "数据说明", "app.view" to "显示", "panel.view.title" to "显示与场景", "panel.speed.title" to "倍速",
        "panel.speed.current" to "当前：%s · %s", "panel.speed.note" to "倍速只影响模拟时间的推进速度，不改变任何历法、相位或日长常数。",
        "panel.datetime.title" to "日期与时间", "panel.lang.title" to "语言", "panel.lang.note" to "语言设置会立即生效，并在下次启动时保留。",
        "panel.close" to "关闭", "time.slider" to "本地时间轴（%s · 1 格 = 1 本地小时 = %s SI 秒）",
        "time.dateSection" to "日期（UCT）", "btn.sunrise" to "日出", "btn.noon" to "正午", "btn.sunset" to "日落",
        "btn.midnight" to "午夜", "btn.prevDay" to "◀ 前一天", "btn.now" to "现在", "btn.nextDay" to "后一天 ▶",
        "btn.live" to "实时获取当前时间", "btn.today" to "回到今天", "msg.range" to "范围 %s · 万年后位置仅作示意",
        "info.title" to "此刻的天空", "info.alt" to "太阳高度", "info.hourAngle" to "太阳时角", "info.decl" to "太阳赤纬",
        "info.dayLen" to "白昼长度", "info.riseSet" to "日出 / 日落", "info.localDay" to "本地日长", "info.localYear" to "本地年长",
        "info.yearStruct" to "一年结构", "info.radix" to "进制", "info.phase" to "相位", "info.orbitPhase" to "轨道相位",
        "info.polar" to "向阳面 / 背阳面", "info.localNight" to "本地昼夜"
    )

    private val planetJa = mapOf(
        "earth" to "地球", "moon" to "月", "mercury" to "水星", "venus" to "金星", "mars" to "火星", "jupiter" to "木星",
        "saturn" to "土星", "uranus" to "天王星", "neptune" to "海王星", "titan" to "タイタン", "io" to "イオ",
        "europa" to "エウロパ", "ganymede" to "ガニメデ", "callisto" to "カリスト", "pluto" to "冥王星", "triton" to "トリトン",
        "enceladus" to "エンケラドゥス", "titania" to "ティターニア", "miranda" to "ミランダ", "charon" to "カロン",
        "ceres" to "ケレス", "vesta" to "ベスタ", "eris" to "エリス", "makemake" to "マケマケ", "haumea" to "ハウメア", "iapetus" to "イアペトゥス"
    )
    private val starJa = mapOf("sun" to "太陽", "proxima" to "プロキシマ・ケンタウリ", "sirius" to "シリウス A", "vega" to "ベガ", "regulus" to "レグルス", "arcturus" to "アークトゥルス")

    fun t(key: String, vararg args: Any?): String {
        val chinese = keyToZh[key] ?: key
        val source = when (lang) {
            UiLang.ZH -> chinese
            UiLang.EN -> (en + enExtra + enUiExtra)[chinese] ?: chinese
            UiLang.JA -> (ja + jaExtra + jaUiExtra)[chinese] ?: chinese
        }
        return if (args.isEmpty()) source else String.format(java.util.Locale.ROOT, source, *args)
    }

    /** Translate exact UI phrases and a small set of explicitly recognized dynamic formats. */
    fun tr(source: String): String {
        if (lang == UiLang.ZH) return source
        val table = if (lang == UiLang.EN) en + enExtra + enUiExtra else ja + jaExtra + jaUiExtra
        table[source]?.let { return it }

        if (lang == UiLang.EN) {
            Regex("^([0-9]+) 个天体$").matchEntire(source)?.let { return "${it.groupValues[1]} bodies" }
            Regex("^([0-9]+) 颗恒星 · ([0-9]+) 个天体$").matchEntire(source)?.let { return "${it.groupValues[1]} stars · ${it.groupValues[2]} bodies" }
            Regex("^([0-9.]+) 光年$").matchEntire(source)?.let { return "${it.groupValues[1]} light-years" }
            Regex("^效果（([0-9]+) 个已开）$").matchEntire(source)?.let { return "Effects (${it.groupValues[1]} enabled)" }
            Regex("^范围 (.+) · 万年后位置仅作示意$").matchEntire(source)?.let { return "Range ${it.groupValues[1]} · positions far in the future are illustrative" }
            Regex("^该系统有 ([0-9]+) 个天体：多行星系统会全部列在这里$").matchEntire(source)?.let { return "This system has ${it.groupValues[1]} bodies; all bodies are listed here." }
            Regex("^1 年 = ([0-9]+) 本地日，([0-9]+) 个月$").matchEntire(source)?.let { return "1 year = ${it.groupValues[1]} local days, ${it.groupValues[2]} months" }
            if (source.startsWith("行 26 × 列 ")) return source.replace("行 26 × 列 ", "Rows 26 × columns ").replace(" 个采样点 · 覆盖 ±180°", " samples · coverage ±180°").replace(" · 已锁低档", " · locked to Low")
            if (source.startsWith("日长 ")) return "Day length " + source.removePrefix("日长 ")
            if (source.startsWith("已钳制到 ")) return "Clamped to " + source.removePrefix("已钳制到 ").replace("（", " (").replace("）", ")")
            if (source == "北") return "North"
            if (source == "东北") return "Northeast"
            if (source == "东") return "East"
            if (source == "东南") return "Southeast"
            if (source == "南") return "South"
            if (source == "西南") return "Southwest"
            if (source == "西") return "West"
            if (source == "西北") return "Northwest"
        } else {
            Regex("^([0-9]+) 個の天体$").matchEntire(source)?.let { return "${it.groupValues[1]}個の天体" }
            Regex("^([0-9]+) 颗恒星 · ([0-9]+) 个天体$").matchEntire(source)?.let { return "${it.groupValues[1]}個の恒星 · ${it.groupValues[2]}個の天体" }
            Regex("^([0-9.]+) 光年$").matchEntire(source)?.let { return "${it.groupValues[1]}光年" }
            Regex("^该系统有 ([0-9]+) 个天体：多行星系统会全部列在这里$").matchEntire(source)?.let { return "この系には${it.groupValues[1]}個の天体があります。複数惑星系の天体はすべてここに表示されます。" }
            if (source == "太阳系：行星、卫星、矮行星三类用上面的标签切换") return "太陽系：上のタブで惑星・衛星・準惑星を切り替えます"
            if (source == "该系统目前只有恒星、没有已知行星：提供空间站观察点（位于等效应日照距离）") return "この系には既知の惑星がないため、等価日射距離に宇宙ステーション観測点を用意しています"
            Regex("^1 年 = ([0-9]+) 本地日，([0-9]+) 个月$").matchEntire(source)?.let { return "1年 = ${it.groupValues[1]} 現地日、${it.groupValues[2]}か月" }
            if (source == "东北") return "北東"
            if (source == "东") return "東"
            if (source == "东南") return "南東"
            if (source == "西南") return "南西"
            if (source == "西北") return "北西"
        }
        // Unknown catalog prose is intentionally preserved rather than partially mangled.
        return source
    }

    fun duration(seconds: Double): String {
        if (lang == UiLang.ZH) return com.skysim.app.core.TimeSystem.formatDuration(seconds)
        val total = seconds.coerceAtLeast(0.0).toLong()
        val days = total / 86400L
        val hours = total % 86400L / 3600L
        val minutes = total % 3600L / 60L
        val secs = total % 60L
        return when (lang) {
            UiLang.ZH -> com.skysim.app.core.TimeSystem.formatDuration(seconds)
            UiLang.EN -> buildList {
                if (days > 0) add("$days d")
                if (hours > 0) add("$hours h")
                if (minutes > 0) add("$minutes min")
                if (secs > 0 || isEmpty()) add("$secs s")
            }.joinToString(" ")
            UiLang.JA -> buildList {
                if (days > 0) add("${days}日")
                if (hours > 0) add("${hours}時間")
                if (minutes > 0) add("${minutes}分")
                if (secs > 0 || isEmpty()) add("${secs}秒")
            }.joinToString(" ")
        }
    }

    fun durationShort(hours: Double): String = when (lang) {
        UiLang.ZH -> if (hours < 48) String.format(java.util.Locale.ROOT, "%.1f 小时", hours) else String.format(java.util.Locale.ROOT, "%.1f 地球日", hours / 24.0)
        UiLang.EN -> if (hours < 48) String.format(java.util.Locale.ROOT, "%.1f h", hours) else String.format(java.util.Locale.ROOT, "%.1f Earth days", hours / 24.0)
        UiLang.JA -> if (hours < 48) String.format(java.util.Locale.ROOT, "%.1f時間", hours) else String.format(java.util.Locale.ROOT, "%.1f地球日", hours / 24.0)
    }

    fun localYearLength(localDays: Double, earthDays: Double): String = when (lang) {
        UiLang.ZH -> "%.4f 本地日（%.2f 地球日）".format(localDays, earthDays)
        UiLang.EN -> "%.4f local days (%.2f Earth days)".format(localDays, earthDays)
        UiLang.JA -> "%.4f現地日（%.2f地球日）".format(localDays, earthDays)
    }

    fun localCalendarInfo(planet: Planet): String = when (lang) {
        UiLang.ZH -> "1 年 = ${planet.localDaysPerYear} 本地日，${planet.monthsPerYear} 个月"
        UiLang.EN -> "1 year = ${planet.localDaysPerYear} local days, ${planet.monthsPerYear} months"
        UiLang.JA -> "1年 = ${planet.localDaysPerYear} 現地日、${planet.monthsPerYear}か月"
    }

    fun localSecondLength(seconds: Double): String = when (lang) {
        UiLang.ZH -> "%.4f 地球秒".format(seconds)
        UiLang.EN -> "%.4f Earth seconds".format(seconds)
        UiLang.JA -> "%.4f地球秒".format(seconds)
    }

    fun solarAltitude(altitudeDeg: Double, azimuth: String, azimuthDeg: Double): String = when (lang) {
        UiLang.ZH -> "${"%.1f".format(java.util.Locale.ROOT, altitudeDeg)}°（$azimuth ${"%.1f".format(java.util.Locale.ROOT, azimuthDeg)}°）"
        UiLang.EN -> "${"%.1f".format(java.util.Locale.ROOT, altitudeDeg)}° ($azimuth ${"%.1f".format(java.util.Locale.ROOT, azimuthDeg)}° azimuth)"
        UiLang.JA -> "${"%.1f".format(java.util.Locale.ROOT, altitudeDeg)}°（方位 $azimuth ${"%.1f".format(java.util.Locale.ROOT, azimuthDeg)}°）"
    }

    fun solarHourAngle(value: Double): String = when (lang) {
        UiLang.ZH -> "${"%.1f".format(java.util.Locale.ROOT, value)}°（0° 正午、180° 午夜）"
        UiLang.EN -> "${"%.1f".format(java.util.Locale.ROOT, value)}° (0° noon, 180° midnight)"
        UiLang.JA -> "${"%.1f".format(java.util.Locale.ROOT, value)}°（0° 正午、180° 真夜中）"
    }

    fun todayHint(): String = when (lang) {
        UiLang.ZH -> "已回到今天 00:00:00 UCT"
        UiLang.EN -> "Returned to today at 00:00:00 UCT"
        UiLang.JA -> "今日の00:00:00 UCTに戻りました"
    }

    fun clampedDateHint(millis: Long): String {
        val date = com.skysim.app.core.UctDate.describe(millis)
        val range = com.skysim.app.core.UctDate.rangeDateText().replace(" ~ ", " – ")
        return when (lang) {
            UiLang.ZH -> "已钳制到 $date（范围：$range）"
            UiLang.EN -> "Clamped to $date (valid range: $range)"
            UiLang.JA -> "$date に調整しました（有効範囲：$range）"
        }
    }

    fun hours(value: Double): String = when (lang) {
        UiLang.ZH -> "${"%.1f".format(java.util.Locale.ROOT, value)} 小时"
        UiLang.EN -> "${"%.1f".format(java.util.Locale.ROOT, value)} hours"
        UiLang.JA -> "${"%.1f".format(java.util.Locale.ROOT, value)}時間"
    }

    fun earthDays(value: Double): String = when (lang) {
        UiLang.ZH -> "${"%.4f".format(java.util.Locale.ROOT, value)} 地球日"
        UiLang.EN -> "${"%.4f".format(java.util.Locale.ROOT, value)} Earth days"
        UiLang.JA -> "${"%.4f".format(java.util.Locale.ROOT, value)}地球日"
    }

    fun rotationPeriod(hours: Double): String = when (lang) {
        UiLang.ZH -> "${"%.2f".format(java.util.Locale.ROOT, kotlin.math.abs(hours))} 小时${if (hours < 0) "（逆行）" else ""}"
        UiLang.EN -> "${"%.2f".format(java.util.Locale.ROOT, kotlin.math.abs(hours))} hours${if (hours < 0) " (retrograde)" else ""}"
        UiLang.JA -> "${"%.2f".format(java.util.Locale.ROOT, kotlin.math.abs(hours))}時間${if (hours < 0) "（逆行）" else ""}"
    }

    fun angularMinutes(value: Double): String = when (lang) {
        UiLang.ZH -> "${"%.2f".format(java.util.Locale.ROOT, value)} 角分"
        UiLang.EN -> "${"%.2f".format(java.util.Locale.ROOT, value)} arcmin"
        UiLang.JA -> "${"%.2f".format(java.util.Locale.ROOT, value)}分角"
    }

    fun solarRadius(value: Double): String = when (lang) {
        UiLang.ZH -> "${"%.2f".format(java.util.Locale.ROOT, value)} 太阳半径"
        UiLang.EN -> "${"%.2f".format(java.util.Locale.ROOT, value)} solar radii"
        UiLang.JA -> "${"%.2f".format(java.util.Locale.ROOT, value)}太陽半径"
    }

    /** 星（假月）的英文单位：句首/字段标签用 Starn，行文小写，复数按数量。真正的恒星仍写 Star。 */
    private fun starnWord(count: Int): String = if (count == 1) "starn" else "starns"

    /** v0.9.4：日期输入框的中间单位标签。星制天体用 Starn，月制天体仍用 Month，中文日文都是「星」/「月」。 */
    fun middleUnitLabel(stamp: LocalStamp): String = when (lang) {
        UiLang.ZH -> if (stamp.chain == CalendarChain.YEAR_STAR_DAY) "星" else "月"
        UiLang.EN -> if (stamp.chain == CalendarChain.YEAR_STAR_DAY) "Starn" else "Month"
        UiLang.JA -> if (stamp.chain == CalendarChain.YEAR_STAR_DAY) "星" else "月"
    }

    /** v0.9.4：±1 中间单位的按钮文案。行文小写、单数。 */
    fun middleUnitDelta(stamp: LocalStamp, delta: Int): String {
        val sign = if (delta < 0) "−" else "+"
        val star = stamp.chain == CalendarChain.YEAR_STAR_DAY
        return when (lang) {
            UiLang.ZH -> if (star) "$sign${1}星" else "$sign${1}月"
            UiLang.EN -> if (star) "$sign${1} starn" else "$sign${1} mo"
            UiLang.JA -> if (star) "$sign${1}星" else "$sign${1}月"
        }
    }

    fun yearStructure(stamp: LocalStamp): String {
        if (lang == UiLang.ZH) return stamp.yearStructureText()
        return when (lang) {
            UiLang.ZH -> stamp.yearStructureText()
            UiLang.EN -> when (stamp.chain) {
                CalendarChain.YEAR_MONTH_DAY -> "Year → 12 months → day (${stamp.daysInYear} days/year)"
                CalendarChain.YEAR_STAR_DAY -> "Year → ${stamp.middleCount} ${starnWord(stamp.middleCount)} → day (${stamp.daysInYear} days/year; leap days at year end)"
                CalendarChain.YEAR_DAY -> "Year → day (${stamp.daysInYear} days/year; no intermediate unit)"
                CalendarChain.DAY_YEAR -> "Day → year (1 year = 1 local day; year index 0/1)"
                CalendarChain.PHASE -> "Phase only (degrees, minutes, seconds; no calendar date)"
            }
            UiLang.JA -> when (stamp.chain) {
                CalendarChain.YEAR_MONTH_DAY -> "年 → 12か月 → 日（${stamp.daysInYear}日/年）"
                CalendarChain.YEAR_STAR_DAY -> "年 → ${stamp.middleCount}星 → 日（${stamp.daysInYear}日/年・年末に閏日）"
                CalendarChain.YEAR_DAY -> "年 → 日（${stamp.daysInYear}日/年・中間単位なし）"
                CalendarChain.DAY_YEAR -> "日 → 年（1年 = 現地1日・年内番号0/1）"
                CalendarChain.PHASE -> "位相のみ（度・分・秒、年月日なし）"
            }
        }
    }

    fun schemeShortText(stamp: LocalStamp): String {
        if (lang == UiLang.ZH) return stamp.schemeShortText()
        return when (lang) {
            UiLang.ZH -> stamp.schemeShortText()
            UiLang.EN -> when (stamp.scheme) {
                DayScheme.EQUAL_24 -> "24 equal divisions · 1 local second = %.5f SI seconds".format(stamp.localSecondRatio)
                DayScheme.SI_SECONDS -> "SI seconds · 1 local second = 1 SI second (hours are uncapped)"
                DayScheme.COARSE_DECIMAL -> "Decimal subdivisions · display 0–1 day fraction; 1 local second = 1 SI second"
            }
            UiLang.JA -> when (stamp.scheme) {
                DayScheme.EQUAL_24 -> "24等分 · 現地1秒 = %.5f SI秒".format(stamp.localSecondRatio)
                DayScheme.SI_SECONDS -> "SI秒 · 現地1秒 = 1 SI秒（時は24で上限なし）"
                DayScheme.COARSE_DECIMAL -> "十進簡易分割 · 0–1の日相位を表示、現地1秒 = 1 SI秒"
            }
        }
    }

    fun phaseDetailText(stamp: LocalStamp): String {
        if (lang == UiLang.ZH) return stamp.phaseDetailText()
        val f = "%.5f".format(java.util.Locale.ROOT, stamp.epochPhaseFraction)
        return when (lang) {
            UiLang.ZH -> stamp.phaseDetailText()
            UiLang.EN -> when (stamp.epochPhaseSource) {
                PhaseSource.MEASURED_ANCHOR -> if (stamp.epochPhaseFraction == 0.0 && !stamp.modelBased) "Measured anchor (UT1 definition, phase 0)" else "Measured anchor (local time at epoch ${f} days)"
                PhaseSource.MEASURED -> "Measured (2015 rotation elements; local time at epoch ${f} days)"
                PhaseSource.CONVENTION -> "Convention: extrapolated from IAU 2015 mean rotation rate; not measured; absolute phase is undetermined"
                PhaseSource.UNDETERMINED -> "Undetermined: no usable rotation elements; readout starts at phase 0"
            }
            UiLang.JA -> when (stamp.epochPhaseSource) {
                PhaseSource.MEASURED_ANCHOR -> if (stamp.epochPhaseFraction == 0.0 && !stamp.modelBased) "実測基準（UT1定義・位相0）" else "実測基準（紀元時の現地時刻 ${f}日）"
                PhaseSource.MEASURED -> "実測（2015年の自転要素・紀元時の現地時刻 ${f}日）"
                PhaseSource.CONVENTION -> "規約値：IAU 2015平均自転速度からの外挿・実測ではなく絶対位相は未確定"
                PhaseSource.UNDETERMINED -> "未確定：利用可能な自転要素がなく、位相0から表示"
            }
        }
    }

    fun qualityLine(quality: com.skysim.app.core.TerrainQuality, autoDowngraded: Boolean): String = when (lang) {
        UiLang.ZH -> com.skysim.app.core.DebugReadout.qualityLine(quality, autoDowngraded)
        UiLang.EN -> "Quality ${when (quality) { com.skysim.app.core.TerrainQuality.LOW -> "Low"; com.skysim.app.core.TerrainQuality.MEDIUM -> "Medium"; else -> "High" }}" + if (autoDowngraded) " (automatically reduced)" else ""
        UiLang.JA -> "画質 ${when (quality) { com.skysim.app.core.TerrainQuality.LOW -> "低"; com.skysim.app.core.TerrainQuality.MEDIUM -> "中"; else -> "高" }}" + if (autoDowngraded) "（自動的に低下）" else ""
    }

    fun terrainDescription(profile: com.skysim.app.core.ReliefProfile): String = when (lang) {
        UiLang.ZH -> "　3D 地形：${profile.kind.labelZh}　起伏 ${profile.amplitudeKm.toInt()} km"
        UiLang.EN -> "3D terrain: ${tr(profile.kind.labelZh)} · relief ${profile.amplitudeKm.toInt()} km"
        UiLang.JA -> "3D地形：${tr(profile.kind.labelZh)}・起伏 ${profile.amplitudeKm.toInt()} km"
    }

    fun twilightNote(source: String): String = scientificNote(source, "twilight")

    fun noAtmosphereTwilight(opacity: Double): String = when (lang) {
        UiLang.ZH -> "　无大气：太阳落山即夜，没有晨昏分层（不透明度 %.2f）".format(java.util.Locale.ROOT, opacity)
        UiLang.EN -> "No atmosphere: night begins at sunset; no twilight layers (opacity %.2f).".format(java.util.Locale.ROOT, opacity)
        UiLang.JA -> "大気なし：日没と同時に夜になり、薄明の層はありません（不透明度 %.2f）。".format(java.util.Locale.ROOT, opacity)
    }

    fun terrainNote(source: String): String = scientificNote(source, "terrain")

    fun debugLine(source: String): String {
        if (lang == UiLang.ZH) return source
        Regex("帧间隔 ([0-9.]+) ms · P95 ([0-9.]+) ms").matchEntire(source)?.let {
            return if (lang == UiLang.EN) "Frame interval ${it.groupValues[1]} ms · P95 ${it.groupValues[2]} ms" else "フレーム間隔 ${it.groupValues[1]} ms · P95 ${it.groupValues[2]} ms"
        }
        return if (lang == UiLang.EN) "Performance detail (Chinese source): $source" else "パフォーマンス詳細（中国語の元データ）：$source"
    }

    fun scientificNote(source: String, kind: String): String {
        if (lang == UiLang.ZH) return source
        val table = if (lang == UiLang.EN) en + enExtra + enUiExtra else ja + jaExtra + jaUiExtra
        table[source]?.let { return it }
        return when (lang) {
            UiLang.ZH -> source
            UiLang.EN -> when (kind) {
                "composition" -> "Detailed atmospheric-composition data is available in the Chinese source text."
                "twilight" -> "Atmospheric scattering shapes this twilight band; detailed note is available in Chinese."
                "terrain" -> "Terrain applicability note is available in the Chinese source text."
                else -> "A body-specific sky-model note is available in the Chinese source text."
            }
            UiLang.JA -> when (kind) {
                "composition" -> "大気組成の詳細は中国語の元データに記載されています。"
                "twilight" -> "大気散乱がこの薄明の色帯を形作ります。詳しい説明は中国語の元データにあります。"
                "terrain" -> "地形の適用条件に関する詳細は中国語の元データにあります。"
                else -> "天体ごとの空モデルの詳細は中国語の元データに記載されています。"
            }
        }
    }

    fun atmosphereEvidence(planet: Planet): String = when (lang) {
        UiLang.ZH -> planet.atmosphereEvidenceZh
        UiLang.EN -> when {
            planet.hasMeasuredAtmosphere -> "Atmosphere: observational evidence (sky has observational basis)"
            planet.confidence == com.skysim.app.core.DataConfidence.MODELED -> "Inferred; no measured atmosphere (sky is model-derived, not an observed scene)"
            else -> "Inferred; no measured atmosphere (treated as airless)"
        }
        UiLang.JA -> when {
            planet.hasMeasuredAtmosphere -> "大気：観測証拠あり（空のモデルにも観測根拠があります）"
            planet.confidence == com.skysim.app.core.DataConfidence.MODELED -> "推定・大気の実測なし（空はモデル値で、実測画像ではありません）"
            else -> "推定・大気の実測なし（大気なしとして扱います）"
        }
    }

    fun starAffiliation(star: Star): String = when (lang) {
        UiLang.ZH -> "${star.nameZh}（${star.spectralTypeZh}，${star.tempK.toInt()} K）"
        UiLang.EN -> "${star.nameEn} (${tr(star.spectralTypeZh)}, ${star.tempK.toInt()} K)"
        UiLang.JA -> "${starName(star)}（${star.spectralTypeZh}・${star.tempK.toInt()} K）"
    }

    fun binarySystem(names: List<String>): String = when (lang) {
        UiLang.ZH -> "双星系统 · ${names.joinToString(" + ")}"
        UiLang.EN -> "Binary system · ${names.joinToString(" + ")}"
        UiLang.JA -> "連星系 · ${names.joinToString(" + ")}"
    }

    fun stationDistance(au: Double): String = when (lang) {
        UiLang.ZH -> "%.3f AU（√L 等效应日照）".format(java.util.Locale.ROOT, au)
        UiLang.EN -> "%.3f AU (equivalent insolation distance, √L)".format(java.util.Locale.ROOT, au)
        UiLang.JA -> "%.3f AU（√L相当日射距離）".format(java.util.Locale.ROOT, au)
    }

    fun lightYears(value: Double): String = when (lang) {
        UiLang.ZH -> "$value 光年"
        UiLang.EN -> "$value light-years"
        UiLang.JA -> "${value}光年"
    }

    fun speedDescription(speed: Double): String {
        if (lang == UiLang.ZH) return com.skysim.app.core.PlaybackClock.describe(speed)
        fun n(v: Double): String = if (kotlin.math.abs(v - kotlin.math.round(v)) < 1e-9) kotlin.math.round(v).toLong().toString() else "%.2f".format(java.util.Locale.ROOT, v)
        return when (lang) {
            UiLang.ZH -> com.skysim.app.core.PlaybackClock.describe(speed)
            UiLang.EN -> when {
                speed >= 86400.0 -> "1 s = ${n(speed / 86400.0)} ${if (speed == 86400.0) "day" else "days"}"
                speed >= 3600.0 -> "1 s = ${n(speed / 3600.0)} ${if (speed == 3600.0) "hour" else "hours"}"
                speed >= 60.0 -> "1 s = ${n(speed / 60.0)} ${if (speed == 60.0) "minute" else "minutes"}"
                speed >= 1.0 -> "1 s = ${n(speed)} s"
                else -> "${n(1.0 / speed)} s = 1 s"
            }
            UiLang.JA -> when {
                speed >= 86400.0 -> "1秒 = ${n(speed / 86400.0)}日"
                speed >= 3600.0 -> "1秒 = ${n(speed / 3600.0)}時間"
                speed >= 60.0 -> "1秒 = ${n(speed / 60.0)}分"
                speed >= 1.0 -> "1秒 = ${n(speed)}秒"
                else -> "${n(1.0 / speed)}秒 = 1秒"
            }
        }
    }

    fun effectHint(id: com.skysim.app.core.EffectId): String = when (lang) {
        UiLang.ZH -> id.hintZh
        UiLang.EN -> when (id) {
            com.skysim.app.core.EffectId.CLOUDS -> "Requires an atmosphere; gas giants use a cloud-top reference"
            com.skysim.app.core.EffectId.RAIN -> "Requires an atmosphere and precipitable material; Mars has no rain"
            com.skysim.app.core.EffectId.RAINBOW -> "Requires precipitation and solar altitude of 2°–42°"
            com.skysim.app.core.EffectId.HALO -> "Requires thin cirrus and the Sun above the horizon"
            com.skysim.app.core.EffectId.AURORA -> "Requires a magnetic field, high latitude, and night"
            com.skysim.app.core.EffectId.HAZE -> "Requires aerosols or a dusty atmosphere"
            com.skysim.app.core.EffectId.LIGHTNING -> "Requires heavy precipitation"
        }
        UiLang.JA -> when (id) {
            com.skysim.app.core.EffectId.CLOUDS -> "大気が必要です。巨大ガス惑星は雲頂を基準にします"
            com.skysim.app.core.EffectId.RAIN -> "大気と降水成分が必要です。火星に雨はありません"
            com.skysim.app.core.EffectId.RAINBOW -> "降水と太陽高度2°～42°が必要です"
            com.skysim.app.core.EffectId.HALO -> "薄い巻雲と地平線上の太陽が必要です"
            com.skysim.app.core.EffectId.AURORA -> "磁場・高緯度・夜空が必要です"
            com.skysim.app.core.EffectId.HAZE -> "エアロゾルまたは砂塵大気が必要です"
            com.skysim.app.core.EffectId.LIGHTNING -> "激しい降水が必要です"
        }
    }

    fun clockText(stamp: LocalStamp): String = when (stamp.scheme) {
        DayScheme.COARSE_DECIMAL -> String.format(java.util.Locale.ROOT, "%.4f %s", stamp.dayFraction, if (lang == UiLang.JA) "日" else "day")
        else -> String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", stamp.hour, stamp.minute, stamp.second)
    }

    fun hudQualifier(stamp: LocalStamp): String {
        if (lang == UiLang.ZH) return stamp.hudQualifierText()
        val parts = mutableListOf<String>()
        if (stamp.modelBased) parts += if (lang == UiLang.EN) "Model estimate" else "モデル推定"
        when (stamp.epochPhaseSource) {
            PhaseSource.CONVENTION -> parts += if (lang == UiLang.EN) "Convention phase (assumed, not measured)" else "規約位相（仮定値・実測ではありません）"
            PhaseSource.UNDETERMINED -> parts += if (lang == UiLang.EN) "Phase undetermined" else "位相未確定"
            PhaseSource.MEASURED -> parts += if (lang == UiLang.EN) "Measured phase" else "実測位相"
            PhaseSource.MEASURED_ANCHOR -> if (stamp.modelBased) parts += if (lang == UiLang.EN) "Measured phase anchor" else "実測基準位相"
        }
        return parts.joinToString(" · ")
    }

    fun calendarText(stamp: LocalStamp): String {
        if (stamp.phaseOnly) return stamp.phaseText()
        val y = when {
            stamp.year > 0 -> stamp.year.toString()
            lang == UiLang.EN -> "BCE ${1 - stamp.year}"
            lang == UiLang.JA -> "紀元前${1 - stamp.year}"
            else -> stamp.yearText()
        }
        val time = clockText(stamp)
        return when (lang) {
            UiLang.ZH -> stamp.calendarText()
            UiLang.EN -> when (stamp.chain) {
                CalendarChain.YEAR_MONTH_DAY -> "Year $y · Month ${stamp.month} · Day ${stamp.day}  $time"
                CalendarChain.YEAR_STAR_DAY -> "Year $y · Starn ${stamp.month} · Day ${stamp.day}  $time"
                CalendarChain.YEAR_DAY -> "Year $y · Day ${stamp.day}  $time"
                CalendarChain.DAY_YEAR -> "Day ${stamp.day} · Year ${stamp.yearInDay}  $time"
                CalendarChain.PHASE -> stamp.phaseText()
            }
            UiLang.JA -> when (stamp.chain) {
                CalendarChain.YEAR_MONTH_DAY -> "${y}年${stamp.month}月${stamp.day}日  $time"
                CalendarChain.YEAR_STAR_DAY -> "${y}年${stamp.month}星${stamp.day}日  $time"
                CalendarChain.YEAR_DAY -> "${y}年${stamp.day}日  $time"
                CalendarChain.DAY_YEAR -> "${stamp.day}日${stamp.yearInDay}年  $time"
                CalendarChain.PHASE -> stamp.phaseText()
            }
        }
    }

    fun speedLabel(label: String): String = when (lang) {
        UiLang.ZH -> label
        UiLang.EN -> label.replace(" 慢放", " Slow").replace(" 原速", " Real time")
        UiLang.JA -> label.replace(" 慢放", " スロー").replace(" 原速", " 通常速度")
    }

    fun starSourceName(chineseName: String): String = when (lang) {
        UiLang.ZH -> chineseName
        UiLang.EN -> StarData.all.firstOrNull { it.nameZh == chineseName }?.nameEn ?: chineseName
        UiLang.JA -> StarData.all.firstOrNull { it.nameZh == chineseName }?.let { starName(it) } ?: chineseName
    }

    fun timeNotes(): String {
        val key = enExtra.keys.first { it.startsWith("时间换算说明") }
        return when (lang) {
            UiLang.ZH -> key
            UiLang.EN -> enExtra[key] ?: key
            UiLang.JA -> jaExtra[key] ?: key
        }
    }

    fun planetName(p: Planet): String = when (lang) {
        UiLang.ZH -> p.nameZh
        UiLang.EN -> p.nameEn.ifBlank { p.nameZh }
        UiLang.JA -> planetJa[p.id] ?: p.nameEn.ifBlank { p.nameZh }
    }

    fun starName(s: Star): String = when (lang) {
        UiLang.ZH -> s.nameZh
        UiLang.EN -> s.nameEn
        UiLang.JA -> starJa[s.id] ?: s.nameEn
    }

    fun planetKind(p: Planet): String {
        val translated = tr(p.kindZh)
        if (lang == UiLang.ZH || translated != p.kindZh) return translated
        return when (lang) {
            UiLang.ZH -> p.kindZh
            UiLang.EN -> when (p.group) {
                com.skysim.app.core.PlanetGroup.EXOPLANET -> "Exoplanet"
                com.skysim.app.core.PlanetGroup.MOON -> "Natural satellite"
                com.skysim.app.core.PlanetGroup.DWARF -> "Dwarf planet / minor body"
                else -> "Planet"
            }
            UiLang.JA -> when (p.group) {
                com.skysim.app.core.PlanetGroup.EXOPLANET -> "系外惑星"
                com.skysim.app.core.PlanetGroup.MOON -> "衛星"
                com.skysim.app.core.PlanetGroup.DWARF -> "準惑星・小天体"
                else -> "惑星"
            }
        }
    }
}
