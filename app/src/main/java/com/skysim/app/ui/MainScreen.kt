package com.skysim.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.skysim.app.core.Astro
import com.skysim.app.core.BodyCatalog
import com.skysim.app.core.DebugReadout
import com.skysim.app.core.Latitudes
import com.skysim.app.core.EffectState
import com.skysim.app.core.FxRules
import com.skysim.app.core.PlaybackClock
import com.skysim.app.core.WeatherState
import com.skysim.app.data.EffectPrefs
import com.skysim.app.core.DataConfidence
import com.skysim.app.core.Planet
import com.skysim.app.core.PlanetGroup
import com.skysim.app.core.Planets
import com.skysim.app.core.Star as CoreStar
import com.skysim.app.core.StarCatalog
import com.skysim.app.core.StarData
import com.skysim.app.core.TimeAxis
import com.skysim.app.core.UctDate
import com.skysim.app.core.TimeSystem
import kotlin.math.abs
import kotlin.math.roundToLong

internal val Accent = Color(0xFF65A7FF)
private val ChipBg = Color(0x1FFFFFFF)
internal val TextDim = Color(0xFF9FB4CE)
private val PanelBorder = Color(0x334E8DCE)
private val PanelGradient = Brush.verticalGradient(
    listOf(Color(0xF51A2A40), Color(0xF20A1322), Color(0xF509111D))
)

@Composable
fun SkyApp() {
    var planetId by remember { mutableStateOf("earth") }
    // 空间站不在 Planets.all 里，必须走 BodyCatalog 解析，否则会静默退回地球（v0.5 的 bug）
    val planet: Planet = remember(planetId) { BodyCatalog.resolve(planetId) }
    val star = remember(planet.hostStarId) { StarData.byId(planet.hostStarId) }
    var latIdx by remember { mutableIntStateOf(3) }
    // v0.8.5：UCT 默认固定在 2025-12-01 00:00:00（UctDate.DEFAULT_MILLIS），不再默认取系统当前时间
    val timeAxis = remember { TimeAxis() }
    var uctMillis by remember { mutableLongStateOf(timeAxis.millis) }
    // 手动定位版本号：让播放帧循环在新锚点上重启一次，这是「每帧覆盖 uctMillis」旧缺陷的修法
    var seekVersion by remember { mutableIntStateOf(0) }
    var playing by remember { mutableStateOf(false) }
    // 「实时获取当前时间」开关：默认关；关掉后停在最后一次设定值
    var liveTime by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val prefs = remember { EffectPrefs(context) }
    // v0.8.5：Debug 开关（默认关）。关着时帧间隔 / P95 / 分段耗时既不绘制也不占布局。
    var debugReadout by remember { mutableStateOf(prefs.loadDebugReadout()) }
    var fx by remember { mutableStateOf(prefs.load()) }
    var scene by remember { mutableStateOf(prefs.loadScene()) }
    // v0.7.1：真实帧间隔监测。连续 3 帧超过 20 ms（低于 50 fps）就降一档画质，并在 HUD 明示，
    // 不静默降级 —— 用户随时可以再手动调回去。
    var frameMs by remember { mutableStateOf(0.0) }
    var autoDowngraded by remember { mutableStateOf(false) }
    // v0.8.2：三档画质（低/中/高）+ 升降回滞。原来只有降档，一次尖峰就永久停在低档。
    val governor = remember { com.skysim.app.core.QualityGovernor() }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        var last = 0L
        var lastUi = 0L
        while (true) {
            androidx.compose.runtime.withFrameNanos { now ->
                if (last != 0L) {
                    val ms = (now - last) / 1_000_000.0
                    // v0.7.2：每帧写 State 会每帧触发整页重组（真机 176 ms 的一部分就在这）。
                    // 只在 250 ms 粒度上刷新，数字照样看得清。
                    if (now - lastUi >= 250_000_000L) {
                        frameMs = ms
                        lastUi = now
                        com.skysim.app.core.Perf.computeP95()
                    }
                    com.skysim.app.core.Perf.recordFrame(ms)
                    val dtSec = (now - last) / 1_000_000_000.0
                    val next = governor.frame(dtSec, ms, scene.quality)
                    if (next != scene.quality) {
                        scene = scene.copy(quality = next)
                        autoDowngraded = governor.auto
                    }
                }
                last = now
            }
        }
    }
    var speedIdx by remember { mutableIntStateOf(prefs.loadSpeedIndex()) }
    var scrubbing by remember { mutableStateOf(false) }
    val uctSeconds = uctMillis / 1000L
    // 天气按 UCT 推进（手动档直接使用设定值，自动档按时间缓慢演变）
    val weather = FxRules.resolveWeather(fx.weather, uctSeconds.toDouble())
    var anchored by remember { mutableStateOf(false) }
    var showPath by remember { mutableStateOf(true) }
    // 潮汐锁定天体的观察面：false = 永昼面，true = 永夜面
    var tidalNight by remember(planet.id) { mutableStateOf(false) }
    var showUi by remember { mutableStateOf(true) }
    var showPicker by remember { mutableStateOf(false) }
    var pickerStars by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    // v0.9.0：二级面板（null = 关闭）。倍速、日期时间、语言各自独立，关闭后状态不丢。
    var panel by remember { mutableStateOf<String?>(null) }

    // v0.8.5：手动定位统一入口。同时递增 seekVersion，让播放帧循环在新锚点上重启一次，
    // 这样「播放中按时刻 / 改日期」不会被下一帧覆盖（旧缺陷）。
    fun seekTo(target: Long) {
        uctMillis = timeAxis.seekTo(target)
        seekVersion++
    }

    // 手动编辑日期时把「实时」关掉：两个写入者同时存在就等于又制造一次静默失效。
    fun seekManual(target: Long) {
        liveTime = false
        seekTo(target)
    }

    // 「实时获取当前时间」：打开后跟随系统时间；关闭后停在最后一次设定值。
    LaunchedEffect(liveTime) {
        if (!liveTime) return@LaunchedEffect
        while (true) {
            // 这里直接写 uctMillis，不递增 seekVersion，避免每 250 ms 重启一次帧循环
            uctMillis = timeAxis.seekTo(System.currentTimeMillis())
            delay(250)
        }
    }

    val latitude = Latitudes.all[latIdx].degrees
    val uctStamp = remember(uctSeconds) { TimeSystem.uctStamp(uctSeconds) }
    // 日期输入框的文本状态：只在日期变化且未在编辑时回填，避免每帧冲掉用户输入
    var yearText by remember { mutableStateOf(uctStamp.year.toString()) }
    var monthText by remember { mutableStateOf(uctStamp.month.toString()) }
    var dayText by remember { mutableStateOf(uctStamp.day.toString()) }
    var dateEditing by remember { mutableStateOf(false) }
    var dateHint by remember { mutableStateOf("") }
    LaunchedEffect(uctStamp.year, uctStamp.month, uctStamp.day, dateEditing) {
        if (!dateEditing) {
            yearText = uctStamp.year.toString()
            monthText = uctStamp.month.toString()
            dayText = uctStamp.day.toString()
        }
    }

    // 三个输入框任一提交：能解析就立刻生效，越界钳到边界并给出提示
    fun applyDateText() {
        val y = yearText.trim().toIntOrNull()
        val m = monthText.trim().toIntOrNull()
        val d = dayText.trim().toIntOrNull()
        if (y == null || m == null || d == null) return
        val edit = UctDate.withDate(uctMillis, y, m, d)
        seekManual(edit.millis)
        dateHint = if (edit.clamped) Str.clampedDateHint(edit.millis) else ""
    }

    val localStamp = remember(planet, uctSeconds, tidalNight) {
        TimeSystem.localStamp(planet, uctSeconds, tidalNight)
    }
    // 季节必须使用当前天体自己的年内日序；公历 UCT 日序只适用于地球。
    val geo = remember(planet, latitude, localStamp.dayOfYearLocal, uctSeconds, anchored, tidalNight) {
        if (planet.isStation) Astro.stationGeo()
        else Astro.sunGeo(
            planet, latitude, localStamp.dayOfYearLocal,
            // v0.8.9：太阳周日运动改由**物理本地日相位**驱动（含纪元自转相位），
            // 不再把「1 UCT 日 = 1 本地日」当默认：火星一天 88775 s，金星一天 116.75 地球日。
            localStamp.dayFraction, anchored, tidalNight
        )
    }

    // v0.9.0：日出/日落相位在同一个 sunGeo 模型上二分求出（极昼极夜返回 NaN），
    // 输入只有天体、纬度、年内日序与轨迹模式，缓存起来供按钮与信息栏共用。
    val risePhase = remember(planet.id, latIdx, localStamp.dayOfYearLocal, anchored, tidalNight) {
        if (planet.isStation) Double.NaN
        else Astro.phaseOfSunAltitude(
            planet, latitude, localStamp.dayOfYearLocal, 0.0,
            rising = true, tidalNightSide = tidalNight, anchored = anchored
        )
    }
    val setPhase = remember(planet.id, latIdx, localStamp.dayOfYearLocal, anchored, tidalNight) {
        if (planet.isStation) Double.NaN
        else Astro.phaseOfSunAltitude(
            planet, latitude, localStamp.dayOfYearLocal, 0.0,
            rising = false, tidalNightSide = tidalNight, anchored = anchored
        )
    }

    // 自动播放：按真实帧间时间差推进，与帧率无关；拖动时间轴时本轮循环结束（scrubbing）
    LaunchedEffect(playing, scrubbing, seekVersion) {
        if (!playing || scrubbing) return@LaunchedEffect
        // 把时钟对齐到当前时刻（手动定位后 restart 会落在新锚点上）
        timeAxis.seekTo(uctMillis)
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L && now > last) {
                    // v0.8.7：advanceFrame 返回的是**绝对时刻**。以前它返回本帧增量，这里却当绝对时刻用，
                    // 于是一点播放就跳到 1970-01-01 附近（3600× 时约一分钟，随帧间隔抖）。
                    uctMillis = timeAxis.advanceFrame(
                        now - last,
                        PlaybackClock.SPEEDS[speedIdx]
                    )
                }
                last = now
            }
        }
    }

    // 相机状态只创建一次：如果随 planet/lat 作为 remember key 重建，
    // pointerInput(Unit) 里挂着的旧闭包会继续写入已经被丢弃的旧 State，表现为“切换星球后拖不动”。
    // 效果设置持久化（滑动时 300ms 防抖，避免每帧写一次磁盘）
    LaunchedEffect(fx) {
        delay(300)
        prefs.save(fx)
    }
    LaunchedEffect(speedIdx) { prefs.saveSpeedIndex(speedIdx) }

    var lookAz by remember { mutableFloatStateOf(0f) }
    var lookAlt by remember { mutableFloatStateOf(16f) }
    // 切换星球 / 纬度时把视角重置到该天体当前的太阳方位（在说明中写清此行为）
    LaunchedEffect(planet.id, latIdx) {
        lookAz = geo.sunAzDeg
        lookAlt = 16f
    }

    MaterialTheme(colorScheme = darkColorScheme(primary = Accent)) {
        BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black)) {
            val panelWidth = if (maxWidth < 620.dp) 232.dp else 326.dp
            val compact = maxHeight < 360.dp

            SkyCanvas(
                planet = planet,
                latitudeDeg = latitude,
                geo = geo,
                // v0.8.9：星空与太阳共用同一个本地日相位（恒星日与太阳日 0.15% 的差未再区分）
                uctFraction = localStamp.dayFraction.toFloat(),
                showSunPath = showPath && !planet.isStation,
                lookAz = lookAz,
                lookAlt = lookAlt,
                fx = fx,
                weather = weather,
                animSeconds = uctMillis / 1000.0,
                scene = scene,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            val down = awaitFirstDown(requireUnconsumed = false)
                            var slopPassed = false
                            var travel = 0f
                            var lastX = down.position.x
                            var lastY = down.position.y
                            val slop = viewConfiguration.touchSlop
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                if (change.changedToUpIgnoreConsumed()) {
                                    if (!slopPassed) showUi = !showUi
                                    break
                                }
                                val dx = change.position.x - lastX
                                val dy = change.position.y - lastY
                                lastX = change.position.x
                                lastY = change.position.y
                                travel += kotlin.math.hypot(dx, dy)
                                if (!slopPassed && travel > slop) slopPassed = true
                                if (slopPassed) {
                                    lookAz = Astro.normalize360((lookAz - dx * 0.18f).toDouble()).toFloat()
                                    lookAlt = (lookAlt - dy * 0.16f)
                                        .coerceIn(if (planet.isStation) -85f else -12f, 88f)
                                    change.consume()
                                }
                            }
                        }
                    }
            )

            if (showUi) {
                // 左上角信息 HUD
                Column(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 14.dp, top = 12.dp)
                        .widthIn(
                            min = HudMetrics.MIN_WIDTH_DP.dp,
                            max = HudMetrics.maxCardWidthDp(maxWidth.value, panelWidth.value).dp
                        )
                        .clip(RoundedCornerShape(18.dp))
                        .background(PanelGradient)
                        .border(1.dp, PanelBorder, RoundedCornerShape(18.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    // v0.9.0：HUD 只留「名称 / 本地时间 / 限定小字 / UCT / 画质」五行，
                    // 进制族、相位档、说明长句全部搬到右侧信息栏（那里才是能滚动、能换行的地方）。
                    Text(
                        Str.planetName(planet),
                        color = Color.White,
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        Str.calendarText(localStamp),
                        color = Color(0xFFBFE0FF),
                        fontSize = if (compact) 12.sp else 14.sp,
                        lineHeight = if (compact) 16.sp else 18.sp,
                        fontFamily = FontFamily.Monospace,
                        // v0.9.5.2：允许在卡片内换行，宽度自适应后不再需要靠截断收场。
                        softWrap = true,
                        maxLines = HudMetrics.MAX_TEXT_LINES
                    )
                    val qualifier = Str.hudQualifier(localStamp)
                    if (qualifier.isNotEmpty()) {
                        Text(
                            Str.tr(qualifier),
                            color = Color(0xFF8FB4D8),
                            fontSize = 9.sp,
                            lineHeight = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            softWrap = true,
                            maxLines = HudMetrics.MAX_TEXT_LINES
                        )
                    }
                    Text(
                        Str.t("hud.uct", Str.calendarText(uctStamp)),
                        color = TextDim,
                        fontSize = 10.sp,
                        lineHeight = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        softWrap = true,
                        maxLines = HudMetrics.MAX_TEXT_LINES
                    )
                    // v0.8.5：画质档位与「已自动降档」是用户可见状态，始终显示
                    Text(
                        Str.qualityLine(scene.quality, autoDowngraded),
                        color = if (autoDowngraded) Color(0xFFFFB86B) else Color(0x8899BBCC),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    // v0.8.5：Debug 关闭时这里一行都不发射 —— 不绘制、不占布局、不留空白条
                    if (debugReadout) {
                        DebugReadout.debugLines(
                            enabled = true,
                            frameMs = frameMs,
                            p95Ms = com.skysim.app.core.Perf.p95Ms(),
                            stageLine = com.skysim.app.core.Perf.stageLine(),
                            residualMs = com.skysim.app.core.Perf.residualMs(frameMs)
                        ).forEach { line ->
                            Text(
                                Str.debugLine(line),
                                color = Color(0x8899BBCC),
                                fontSize = 8.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    // v0.9.5.1：HUD 回到 0.9.0 定稿 —— 只有「名称 / 本地时间 / 限定小字 / UCT / 画质」五行，
                    // 不放任何按钮。回正视角与隐藏界面统一收进右栏「视角工具」。
                }

                // 右侧控制面板
                Column(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 10.dp, top = 8.dp, bottom = 8.dp)
                        .width(panelWidth)
                        .fillMaxHeight()
                        .shadow(22.dp, RoundedCornerShape(22.dp))
                        .clip(RoundedCornerShape(22.dp))
                        .background(PanelGradient)
                        .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 14.dp, vertical = 13.dp)
                ) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("SKY SIMULATOR", color = Color(0xFF8DBEFF), fontSize = 9.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                            Text(Str.tr("观测控制台"), color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Text("v${com.skysim.app.BuildConfig.VERSION_NAME}", color = Color(0xFF83D7B0), fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(10.dp))
                    // 星球选择
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0x263E76B7), Color(0x171FFFFFFF))))
                            .border(1.dp, Color(0x224E8DCE), RoundedCornerShape(14.dp))
                            .clickable { showPicker = true }
                            .padding(horizontal = 12.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(
                                    if (star.id == "sun") Str.planetName(planet) else "${Str.starName(star)} · ${Str.planetName(planet)}",
                                    color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    "${Str.planetName(planet)} · ${Str.planetKind(planet)}",
                                    color = TextDim, fontSize = 10.sp
                                )
                                if (star.id != "sun") {
                                    Text(
                                        Str.tr("所属恒星：${Str.starName(star)}（${star.spectralTypeZh}）"),
                                        color = Accent, fontSize = 9.sp,
                                        modifier = Modifier.clickable { pickerStars = true; showPicker = true }
                                    )
                                }
                            }
                            Text(Str.tr("切换 ▾"), color = Accent, fontSize = 11.sp)
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    // v0.9.4：四个按钮改成两行两列。原来一行四个，英文 "Date & time" 被截成 "Date &"、
                    // 语言按钮只剩地球图标 —— 232 dp 下每格只有约 50 dp。两行后每格约 106 dp，
                    // 中文/英文/日文都能完整显示，且不需要在两行文字上赌 28 dp 的高度。
                    val toolbarItems = listOf(
                        "⚡ " + Str.t("app.speed") to "speed",
                        "🕒 " + Str.t("app.datetime") to "time",
                        "🖥 " + Str.t("app.view") to "view",
                        Str.t("app.lang") to "lang"
                    )
                    toolbarItems.chunked(InfoLineMetrics.TOOLBAR_BUTTONS_PER_ROW).forEach { rowItems ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { (label, key) ->
                                MiniButton(label, Modifier.weight(1f)) { panel = key }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    SectionLabel(Str.t("info.title"))
                    InfoLine(Str.t("info.alt"), Str.solarAltitude(geo.sunAltDeg.toDouble(), Str.tr(Astro.azimuthLabel(geo.sunAzDeg)), geo.sunAzDeg.toDouble()))
                    InfoLine(Str.t("info.hourAngle"), Str.solarHourAngle(localStamp.solarHourAngleDeg))
                    InfoLine(Str.t("info.decl"), "${fmt(geo.declinationDeg)}°")
                    if (planet.isSynchronous) {
                        // 潮汐锁定：太阳日数学上发散，不编造一个「本地日长」
                        InfoLine(Str.t("info.localNight"), "无：太阳固定在天空，时角不变")
                        InfoLine(Str.t("info.localDay"), "发散（同步锁定，无昼夜循环）")
                        InfoLine(Str.t("info.riseSet"), "不存在：晨昏线固定在一条经线上")
                        InfoLine(Str.t("info.orbitPhase"), "${localStamp.orbitPhaseDeg.toInt()}°")
                        InfoLine(Str.t("info.polar"), "永久正午 / 永久午夜")
                    } else {
                        InfoLine(
                            "白昼长度",
                            when {
                                geo.polarDay -> "极昼 24 小时"
                                geo.polarNight -> "极夜 0 小时"
                                else -> Str.hours(geo.dayLengthHours.toDouble())
                            }
                        )
                        // v0.9.0：日出日落改成本地时（以前印的是 UCT 小时，与本地钟对不上）
                        val localHm = { ph: Double ->
                            val fields = TimeSystem.localClockFields(
                                localStamp.scheme, ph, TimeSystem.localDayLengthSeconds(planet)
                            )
                            if (localStamp.scheme == com.skysim.app.core.DayScheme.COARSE_DECIMAL) {
                                "%d:%d:%d".format(fields[0], fields[1], fields[2])
                            } else {
                                "%02d:%02d".format(fields[0], fields[1])
                            }
                        }
                        InfoLine(
                            "日出 / 日落",
                            when {
                                geo.polarDay -> "太阳不落（极昼）"
                                geo.polarNight -> "太阳不升（极夜）"
                                risePhase.isNaN() || setPhase.isNaN() -> "无解"
                                else -> "${localHm(risePhase)} / ${localHm(setPhase)} ${Str.tr("本地")}"
                            }
                        )
                    }
                    InfoLine(Str.t("info.localDay"), Str.duration(TimeSystem.localDayLengthSeconds(planet)))
                    InfoLine(
                        Str.t("info.localYear"),
                        Str.localYearLength(
                            com.skysim.app.core.LocalCalendars.of(planet).yearLocalDays,
                            planet.localYearEarthDays
                        )
                    )
                    InfoLine(Str.t("info.yearStruct"), Str.yearStructure(localStamp))
                    InfoLine(Str.t("info.radix"), Str.schemeShortText(localStamp))
                    InfoLine(Str.t("info.phase"), Str.phaseDetailText(localStamp))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        Str.tr("提示：在天空区域拖动可平移视角，点按天空可隐藏 / 唤出界面；隐藏后自动播放继续推进。"),
                        color = Color(0x77FFFFFF), fontSize = 9.sp
                    )
                    Spacer(Modifier.height(6.dp))
                }

                // 宽屏底部快捷时间台：把最常用的播放、实时、倍速与日相位跳转放到主画面，
                // 不必每次打开二级日期面板。窄屏保留原有右侧面板，避免遮挡可视天空。
                if (maxWidth >= TimelineUi.WIDE_DOCK_MIN_WIDTH_DP.dp) {
                    val dockWidth = (maxWidth - panelWidth - 42.dp).coerceAtMost(560.dp)
                    Column(
                        Modifier
                            .align(Alignment.BottomStart)
                            .padding(start = 14.dp, bottom = 14.dp)
                            .width(dockWidth)
                            .shadow(20.dp, RoundedCornerShape(20.dp))
                            .clip(RoundedCornerShape(20.dp))
                            .background(PanelGradient)
                            .border(1.dp, PanelBorder, RoundedCornerShape(20.dp))
                            .padding(horizontal = 13.dp, vertical = 9.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                            MiniButton(if (playing) "Ⅱ  ${Str.tr("暂停")}" else "▶  ${Str.tr("播放")}", Modifier.weight(1.1f)) {
                                playing = !playing
                                if (playing) liveTime = false
                            }
                            Column(Modifier.weight(1.5f)) {
                                Text(Str.clockText(localStamp), color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Monospace)
                                Text(Str.calendarText(localStamp), color = TextDim, fontSize = 8.sp, maxLines = 1)
                            }
                            MiniButton(Str.speedLabel(PlaybackClock.SPEED_LABELS[speedIdx]), Modifier.weight(0.8f)) { panel = "speed" }
                            Chip(Str.tr("实时"), liveTime, Modifier.weight(0.8f)) {
                                liveTime = !liveTime
                                if (liveTime) playing = false
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Slider(
                                modifier = Modifier.weight(1f),
                                // v0.9.5.1：与日期面板同一条时间轴 —— 0..24 本地小时、23 个刻度、一格一小时。
                                value = (localStamp.dayFraction * 24.0).toFloat().coerceIn(0f, 24f),
                                onValueChange = { value ->
                                    scrubbing = true
                                    seekManual(
                                        TimeSystem.uctSecondsForLocalPhase(
                                            planet, uctSeconds, (value / 24.0).toDouble()
                                        ) * 1000L
                                    )
                                },
                                onValueChangeFinished = { scrubbing = false },
                                valueRange = 0f..TimelineUi.LOCAL_HOURS_PER_DAY,
                                steps = TimelineUi.LOCAL_HOUR_STEPS,
                                colors = SliderDefaults.colors(
                                    thumbColor = Accent,
                                    activeTrackColor = Accent,
                                    inactiveTrackColor = Color(0x33FFFFFF)
                                )
                            )
                            MiniButton(Str.tr("日出"), Modifier.weight(0.55f), enabled = !risePhase.isNaN()) {
                                seekManual(TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, risePhase) * 1000L)
                            }
                            MiniButton(Str.tr("正午"), Modifier.weight(0.55f)) {
                                seekManual(TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, 0.5) * 1000L)
                            }
                            MiniButton(Str.tr("日落"), Modifier.weight(0.55f), enabled = !setPhase.isNaN()) {
                                seekManual(TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, setPhase) * 1000L)
                            }
                        }
                    }
                }

                // v1.0：正式版移除左下角那行「版本号 · 数据离线」水印，连同其占位一起删掉，
                // 不再只是改成透明或空串；版本号只保留在右栏品牌头里显示。
            }
            // v0.9.0：二级面板。倍速、日期时间、语言各自独立成浮层：
            // 关闭后倍速档、当前时刻、语言选择都不丢（它们都存在上层 State 里）。
            val activePanel = panel
            if (activePanel != null) {
                BackHandler { panel = null }
                Box(Modifier.fillMaxSize().background(Color(0x99000000)), contentAlignment = Alignment.CenterEnd) {
                    Column(
                        Modifier
                            .padding(end = 10.dp, top = 8.dp, bottom = 8.dp)
                            .width(panelWidth)
                            .fillMaxHeight()
                            .shadow(24.dp, RoundedCornerShape(22.dp))
                            .clip(RoundedCornerShape(22.dp))
                            .background(PanelGradient)
                            .border(1.dp, PanelBorder, RoundedCornerShape(22.dp))
                            .padding(horizontal = 14.dp, vertical = 13.dp)
                    ) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                when (activePanel) {
                                    "speed" -> Str.t("panel.speed.title")
                                    "time" -> Str.t("panel.datetime.title")
                                    "view" -> Str.t("panel.view.title")
                                    else -> Str.t("panel.lang.title")
                                },
                                color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f)
                            )
                            MiniButton(Str.t("panel.close")) { panel = null }
                        }
                        Spacer(Modifier.height(8.dp))
                        Column(Modifier.fillMaxWidth().weight(1f).verticalScroll(rememberScrollState())) {
                            when (activePanel) {
                                "view" -> {
                    if (planet.isStation) {
                        SectionLabel(Str.tr("空间站视角"))
                        Text(
                            Str.tr("无地面、无大气：拖动画面即可自由旋转观察恒星与星空，时间仍按 UCT 推进"),
                            color = Color(0x889FB0C8), fontSize = 8.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    SectionLabel(Str.tr(if (planet.isStation) "参考纬度（仅用于星空方位）" else "观测纬度"))
                    Latitudes.all.chunked(2).forEach { pair ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            pair.forEach { lat ->
                                val idx = Latitudes.all.indexOf(lat)
                                Chip(
                                    text = lat.labelZh,
                                    selected = idx == latIdx,
                                    modifier = Modifier.weight(1f)
                                ) { latIdx = idx }
                            }
                        }
                        Spacer(Modifier.height(6.dp))
                    }

                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .height(30.dp)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (playing) Color(0xFFE0574C) else Accent)
                                .clickable {
                                    playing = !playing
                                    // 播放与「实时」互斥：两个写入者同时存在就是又制造一次静默失效
                                    if (playing) liveTime = false
                                }
                                .padding(horizontal = 14.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(Str.tr(if (playing) "⏸ 暂停" else "▶ 播放"), color = Color.White, fontSize = 11.sp)
                        }
                        Spacer(Modifier.width(9.dp))
                        Column {
                            Text(
                                "${Str.speedLabel(PlaybackClock.SPEED_LABELS[speedIdx])} · ${Str.speedDescription(PlaybackClock.SPEEDS[speedIdx])}",
                                color = Color(0xFFDCE7F5), fontSize = 10.sp
                            )
                            Text(
                                Str.tr(if (playing) "推进中" else "已暂停"),
                                color = if (playing) Color(0xFF7FD6A0) else TextDim,
                                fontSize = 9.sp
                            )
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    SectionLabel(Str.tr("天空采样密度（画质档）"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        com.skysim.app.core.TerrainQuality.values().forEach { q ->
                            Chip(
                                "${q.labelZh} ${com.skysim.app.core.SkyGrid.samples(q)}",
                                scene.quality == q && !governor.auto,
                                Modifier.weight(1f)
                            ) {
                                scene = scene.copy(quality = q)
                                governor.manual()
                                autoDowngraded = false
                            }
                        }
                        Chip("自动", governor.auto, Modifier.weight(1f)) { governor.enableAuto() }
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        Str.tr("行 26 × 列 " + com.skysim.app.core.SkyGrid.columns(scene.quality) +
                            " · " + com.skysim.app.core.SkyGrid.samples(scene.quality) +
                            " 个采样点 · 覆盖 ±180°" + (if (governor.locked) " · 已锁低档" else "")),
                        color = Color(0x8899BBCC),
                        fontSize = 8.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(Modifier.height(10.dp))
                    if (planet.tidallyLocked) {
                        SectionLabel(Str.tr("潮汐锁定：观察面"))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Chip("永昼面", !tidalNight, Modifier.weight(1f)) { tidalNight = false }
                            Chip("永夜面", tidalNight, Modifier.weight(1f)) { tidalNight = true }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text(
                            Str.tr("自转与公转同步：太阳（母星）永远停在同一个位置，本地时刻冻结"),
                            color = Color(0x889FB0C8), fontSize = 8.sp
                        )
                        Spacer(Modifier.height(8.dp))
                    }

                    // v0.9.5.1：全应用只保留这一处视角工具入口（HUD 里那两个已删除）。
                    SectionLabel(Str.tr("视角工具"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        MiniButton(Str.tr("回正视角"), Modifier.weight(1f)) {
                            lookAz = geo.sunAzDeg
                            lookAlt = 16f
                        }
                        // 只把观察高度压到地平线（0°），不是回到默认的 16°，所以叫「压到地平线」。
                        MiniButton(Str.tr("压到地平线"), Modifier.weight(1f)) { lookAlt = 0f }
                    }
                    Spacer(Modifier.height(6.dp))
                    // 隐藏后有三条恢复路径：点按天空切换、右下角 ⋯ 圆钮、以及本按钮所在的右栏本身。
                    MiniButton(Str.tr("隐藏界面"), Modifier.fillMaxWidth()) { panel = null; showUi = false }
                    Spacer(Modifier.height(8.dp))
                    SectionLabel(Str.tr("显示"))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Chip("太阳轨迹", showPath, Modifier.weight(1f)) { showPath = !showPath }
                        Chip(Str.tr(if (anchored) "日出锚定" else "物理轨迹"), anchored, Modifier.weight(1f)) {
                            anchored = !anchored
                        }
                    }
                    Spacer(Modifier.height(6.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        // v0.8.5：帧间隔 / P95 / 分段耗时的总开关，默认关（正式版不需要）
                        Chip(Str.tr("Debug 读数"), debugReadout, Modifier.weight(1f)) {
                            debugReadout = !debugReadout
                            prefs.saveDebugReadout(debugReadout)
                        }
                        MiniButton(Str.t("app.info"), Modifier.weight(1f)) { showInfo = true }
                    }

                    Spacer(Modifier.height(10.dp))
                    SceneSection(
                        planet = planet,
                        geo = geo,
                        scene = scene,
                        fx = fx,
                        onSceneChange = {
                            scene = it.clamped()
                            prefs.saveScene(scene)
                        }
                    )

                    Spacer(Modifier.height(10.dp))
                    EffectsSection(
                        planet = planet,
                        latitudeDeg = latitude,
                        geo = geo,
                        fx = fx,
                        onChange = { fx = it }
                    )

                    Spacer(Modifier.height(10.dp))
                                }
                                "speed" -> {
                                    SectionLabel(
                                        Str.t(
                                            "panel.speed.current",
                                            Str.speedLabel(PlaybackClock.SPEED_LABELS[speedIdx]),
                                            Str.speedDescription(PlaybackClock.SPEEDS[speedIdx])
                                        )
                                    )
                                    PlaybackClock.SPEED_LABELS.chunked(3).forEach { row ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                            row.forEach { label ->
                                                val idx = PlaybackClock.SPEED_LABELS.indexOf(label)
                                                Chip(Str.speedLabel(label), idx == speedIdx, Modifier.weight(1f)) { speedIdx = idx }
                                            }
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Text(Str.t("panel.speed.note"), color = Color(0x8899BBCC), fontSize = 8.sp)
                                }
                            "time" -> {
                                SectionLabel(
                                    Str.t(
                                        "time.slider",
                                        Str.clockText(localStamp),
                                        String.format("%.0f", TimeSystem.localDayLengthSeconds(planet) / 24.0)
                                    )
                                )
                                Slider(
                                    value = (localStamp.dayFraction * 24.0).toFloat(),
                                    onValueChange = { v ->
                                        scrubbing = true
                                        seekManual(
                                            TimeSystem.uctSecondsForLocalPhase(
                                                planet, uctSeconds, (v / 24.0).toDouble()
                                            ) * 1000L
                                        )
                                    },
                                    onValueChangeFinished = { scrubbing = false },
                                    valueRange = 0f..TimelineUi.LOCAL_HOURS_PER_DAY,
                                    steps = TimelineUi.LOCAL_HOUR_STEPS,
                                    colors = SliderDefaults.colors(
                                        thumbColor = Accent,
                                        activeTrackColor = Accent,
                                        inactiveTrackColor = Color(0x33FFFFFF)
                                    )
                                )
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    val polar = risePhase.isNaN() || setPhase.isNaN()
                                    MiniButton(
                                        when {
                                            geo.polarDay -> Str.tr("极昼")
                                            geo.polarNight -> Str.tr("极夜")
                                            else -> Str.t("btn.sunrise")
                                        },
                                        Modifier.weight(1f), enabled = !polar
                                    ) {
                                        seekManual(
                                            TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, risePhase) * 1000L
                                        )
                                    }
                                    MiniButton(Str.t("btn.noon"), Modifier.weight(1f)) {
                                        seekManual(
                                            TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, 0.5) * 1000L
                                        )
                                    }
                                    MiniButton(
                                        when {
                                            geo.polarDay -> Str.tr("极昼")
                                            geo.polarNight -> Str.tr("极夜")
                                            else -> Str.t("btn.sunset")
                                        },
                                        Modifier.weight(1f), enabled = !polar
                                    ) {
                                        seekManual(
                                            TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, setPhase) * 1000L
                                        )
                                    }
                                    MiniButton(Str.t("btn.midnight"), Modifier.weight(1f)) {
                                        seekManual(
                                            TimeSystem.uctSecondsForLocalPhase(planet, uctSeconds, 0.0) * 1000L
                                        )
                                    }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    MiniButton(Str.t("btn.prevDay"), Modifier.weight(1f)) {
                                        seekManual(UctDate.plusDays(uctMillis, -1))
                                    }
                                    MiniButton(Str.t("btn.now"), Modifier.weight(0.7f)) {
                                        seekManual(System.currentTimeMillis())
                                    }
                                    MiniButton(Str.t("btn.nextDay"), Modifier.weight(1f)) {
                                        seekManual(UctDate.plusDays(uctMillis, 1))
                                    }
                                }
                                Spacer(Modifier.height(8.dp))
                                SectionLabel(Str.t("time.dateSection"))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    NumberField(Str.tr("年"), yearText, Modifier.weight(1.4f),
                                        onValueChange = { yearText = it; applyDateText() },
                                        onFocusChange = { dateEditing = it })
                                    // v0.9.4：中间单位随历法链取名 —— 星制天体是 Starn（不是 Month、更不是 Star）
                                    NumberField(Str.middleUnitLabel(localStamp), monthText, Modifier.weight(1f),
                                        onValueChange = { monthText = it; applyDateText() },
                                        onFocusChange = { dateEditing = it })
                                    NumberField(Str.tr("日"), dayText, Modifier.weight(1f),
                                        onValueChange = { dayText = it; applyDateText() },
                                        onFocusChange = { dateEditing = it })
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    MiniButton(Str.tr("−1年"), Modifier.weight(1f)) { seekManual(UctDate.plusYears(uctMillis, -1)) }
                                    MiniButton(Str.middleUnitDelta(localStamp, -1), Modifier.weight(1f)) { seekManual(UctDate.plusMonths(uctMillis, -1)) }
                                    MiniButton(Str.tr("−1日"), Modifier.weight(1f)) { seekManual(UctDate.plusDays(uctMillis, -1)) }
                                }
                                Spacer(Modifier.height(4.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    MiniButton(Str.tr("+1日"), Modifier.weight(1f)) { seekManual(UctDate.plusDays(uctMillis, 1)) }
                                    MiniButton(Str.middleUnitDelta(localStamp, 1), Modifier.weight(1f)) { seekManual(UctDate.plusMonths(uctMillis, 1)) }
                                    MiniButton(Str.tr("+1年"), Modifier.weight(1f)) { seekManual(UctDate.plusYears(uctMillis, 1)) }
                                }
                                Spacer(Modifier.height(6.dp))
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Chip(Str.t("btn.live"), liveTime, Modifier.weight(1.4f)) {
                                        liveTime = !liveTime
                                        if (liveTime) playing = false
                                    }
                                    MiniButton(Str.t("btn.today"), Modifier.weight(1f)) {
                                        seekManual(UctDate.todayMidnight(System.currentTimeMillis()))
                                        dateHint = Str.todayHint()
                                    }
                                }
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    Str.tr(dateHint.ifEmpty { Str.t("msg.range", UctDate.rangeDateText()) }),
                                    color = if (dateHint.isEmpty()) Color(0x8899BBCC) else Color(0xFFFFB86B),
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace
                                )
                            }
                                else -> {
                                    UiLang.values().forEach { l ->
                                        Row(
                                            Modifier.fillMaxWidth()
                                                .clip(RoundedCornerShape(10.dp))
                                                .background(if (Str.lang == l) Color(0x33FFFFFF) else Color(0x11FFFFFF))
                                                .clickable { Str.lang = l; prefs.saveLanguageCode(l.code) }
                                                .padding(horizontal = 10.dp, vertical = 9.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                l.labelNative,
                                                color = if (Str.lang == l) Accent else Color(0xFFDCE7F5),
                                                fontSize = 12.sp,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Text(Str.tr("界面语言"), color = TextDim, fontSize = 8.sp)
                                        }
                                        Spacer(Modifier.height(6.dp))
                                    }
                                    Text(Str.t("panel.lang.note"), color = Color(0x8899BBCC), fontSize = 8.sp)
                                }
                            }
                        }
                    }
                }
            }
 else {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(Color(0x22FFFFFF))
                        .clickable { showUi = true },
                    contentAlignment = Alignment.Center
                ) {
                    Text("⋯", color = Color(0xAAFFFFFF), fontSize = 16.sp)
                }
            }
        }

        if (showPicker) {
            SkyPicker(
                current = planet,
                startAtStars = pickerStars,
                onPick = { planetId = it.id; showPicker = false; pickerStars = false },
                onDismiss = { showPicker = false; pickerStars = false }
            )
        }
        if (showInfo) {
            InfoDialog(planet, onDismiss = { showInfo = false })
        }
    }
}

private fun jumpTo(hour: Float, uctSeconds: Long, anchored: Boolean, set: (Long) -> Unit) {
    if (hour.isNaN()) return
    val dayStart = Math.floorDiv(uctSeconds, 86400L) * 86400L
    set(dayStart + (hour.toDouble() * 3600.0).roundToLong())
}

private fun fmt(v: Float): String = String.format(java.util.Locale.US, "%.1f", v)

@Composable
internal fun SectionLabel(text: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.width(3.dp).height(12.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Accent)
        )
        Spacer(Modifier.width(7.dp))
        Text(
            Str.tr(text),
            color = Color(0xFFB8CBE4),
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.25.sp
        )
    }
}

/**
 * v0.9.4：信息栏「标签 + 数值」的排版常量。
 *
 * 0.9.3 里两个 Text 紧挨着（标签只有 min 宽度、没有间距），英文就粘成了
 * "Solar altitude67.0°"；长数值（如 "668.5993 local days (686.98 Earth days)"）还会被
 * 挤到下一行右对齐。这里改成「固定标签列 + 固定间距 + 数值列 weight(1f) 左对齐」，
 * 三种语言共用同一套几何，不靠在译文里手打空格。
 */
/**
 * v0.9.5.1：底部时间台的出现阈值与时间轴粒度。
 *
 * 阈值 1000 dp 的依据：1080×2400、密度约 2.75 的手机横屏只有约 873 dp 宽，
 * 0.9.5 用的 720 dp 会把手机也算成「宽屏」，时间台直接压在画面与 HUD 上。
 * 1000 dp 只有平板级（横屏 ≥ 10 英寸）才够得到，笔记本外接屏同理。
 */
/**
 * v0.9.5.2：HUD 卡片的宽度与换行约束。
 *
 * 0.9.5.1 把卡片写死成 widthIn(min = 176, max = 286)，中文五行放得下，英文
 * 「Year 1077 · Starn 12 · Day 22  23:42:23」约 330 dp，于是被 286 的硬上限裁掉。
 * 现在宽度随内容自适应，只保留一个封顶：屏幕宽 − 右栏宽 − 间隔，保证永远不顶到右栏。
 * 长度超限时在卡片内换行（最多两行），不省略、不横向撑破屏幕。
 */
/**
 * v0.9.5.3：切换星球弹窗的宽度与卡片列数规则。
 *
 * 0.9.5.2 及以前写死 width(780.dp) 且沿用平台默认弹窗宽度，实测被压到约 360 dp：
 * 英文 Mercury 被从中间折断、terrestrial / Day length 折行不齐，中文的「日长 24.0 小时」
 * 和关闭按钮也被挤成两行。现在宽度按语言给一个偏好值，再封顶到「屏幕宽 − 两侧留白」；
 * 卡片列数按内容宽度算，宁可少一列也不让单词断行。
 */
internal object PickerMetrics {
    const val SCREEN_MARGIN_DP = 24f
    const val PREFERRED_WIDTH_ZH_JA_DP = 660f
    const val PREFERRED_WIDTH_EN_DP = 760f
    const val MIN_WIDTH_DP = 320f
    const val DIALOG_PADDING_DP = 28f
    const val CARD_SPACING_DP = 8f
    const val CARD_H_PADDING_DP = 20f
    const val NAME_DOT_DP = 18f
    const val MIN_CARD_WIDTH_DP = 190f
    const val MIN_COLUMNS = 2
    const val MAX_COLUMNS = 3
    /** 关闭按钮固定方形触控区，不参与文字压缩。 */
    const val CLOSE_BUTTON_DP = 48f

    fun dialogWidthDp(screenWidthDp: Float, lang: UiLang): Float {
        val available = (screenWidthDp - SCREEN_MARGIN_DP * 2f).coerceAtLeast(0f)
        val preferred = if (lang == UiLang.EN) PREFERRED_WIDTH_EN_DP else PREFERRED_WIDTH_ZH_JA_DP
        val width = minOf(preferred, available)
        return maxOf(width, minOf(MIN_WIDTH_DP, available))
    }

    fun contentWidthDp(dialogWidthDp: Float): Float = dialogWidthDp - DIALOG_PADDING_DP

    fun columnsFor(contentWidthDp: Float): Int {
        val fit = ((contentWidthDp + CARD_SPACING_DP) / (MIN_CARD_WIDTH_DP + CARD_SPACING_DP)).toInt()
        return fit.coerceIn(MIN_COLUMNS, MAX_COLUMNS)
    }

    fun cardWidthDp(contentWidthDp: Float, columns: Int): Float =
        (contentWidthDp - CARD_SPACING_DP * (columns - 1)) / columns

    /** 卡片里真正放字的宽度：去掉内边距与左侧色点。 */
    fun cardTextWidthDp(contentWidthDp: Float, columns: Int): Float =
        cardWidthDp(contentWidthDp, columns) - CARD_H_PADDING_DP - NAME_DOT_DP
}

internal object HudMetrics {
    const val MIN_WIDTH_DP = 176f
    /** 卡片左内边距 14 + 右栏右内边距 10 + 与右栏之间留 22 的余量。 */
    const val PANEL_GAP_DP = 46f
    /** 卡片水平内边距合计（左右各 14）。 */
    const val CARD_PADDING_DP = 28f
    /** 本地时间、限定小字、UCT 三行最多两行，超出就换行，不做省略号截断。 */
    const val MAX_TEXT_LINES = 2

    fun maxCardWidthDp(screenWidthDp: Float, panelWidthDp: Float): Float =
        (screenWidthDp - panelWidthDp - PANEL_GAP_DP).coerceAtLeast(MIN_WIDTH_DP)

    fun innerWidthDp(screenWidthDp: Float, panelWidthDp: Float): Float =
        maxCardWidthDp(screenWidthDp, panelWidthDp) - CARD_PADDING_DP
}

internal object TimelineUi {
    const val WIDE_DOCK_MIN_WIDTH_DP = 1000f
    const val LOCAL_HOURS_PER_DAY = 24f
    const val LOCAL_HOUR_STEPS = 23
}

internal object InfoLineMetrics {
    const val LABEL_WIDTH_DP = 78f
    const val LABEL_VALUE_GAP_DP = 8f
    const val TOOLBAR_BUTTONS_PER_ROW = 2
}

@Composable
private fun InfoLine(label: String, value: String) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 1.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            Str.tr(label),
            color = TextDim, fontSize = 10.sp, lineHeight = 13.sp,
            softWrap = true,
            modifier = Modifier.width(InfoLineMetrics.LABEL_WIDTH_DP.dp)
        )
        Spacer(Modifier.width(InfoLineMetrics.LABEL_VALUE_GAP_DP.dp))
        Text(
            Str.tr(value),
            color = Color(0xFFE6EEFA), fontSize = 10.sp, lineHeight = 13.sp,
            softWrap = true, textAlign = TextAlign.Start,
            modifier = Modifier.weight(1f)
        )
    }
}

/** v0.9.5.3：弹窗关闭按钮固定 48 dp 方形触控区，文字不再被标题行挤压。 */
@Composable
private fun PickerCloseButton(onDismiss: () -> Unit) {
    Box(
        Modifier
            .size(PickerMetrics.CLOSE_BUTTON_DP.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1FFFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(12.dp))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Text("✕", color = Accent, fontSize = 16.sp, maxLines = 1)
    }
}

@Composable
internal fun Chip(text: String, selected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Accent.copy(alpha = 0.92f) else ChipBg)
            .border(1.dp, if (selected) Color(0x667EBBFF) else Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            Str.tr(text),
            color = if (selected) Color.White else Color(0xFFD6E1F0),
            fontSize = 9.5.sp,
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun MiniButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    Box(
        modifier
            .height(32.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0x20FFFFFF))
            .border(1.dp, Color(0x1FFFFFFF), RoundedCornerShape(10.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier),
        contentAlignment = Alignment.Center
    ) {
        Text(
            Str.tr(text),
            color = if (enabled) Color(0xFFDCE7F5) else Color(0x66DCE7F5),
            fontSize = 9.5.sp,
            maxLines = 1
        )
    }
}

/**
 * v0.8.5：日期输入框。用 BasicTextField 而不是 OutlinedTextField —— 右侧面板在 232 dp 宽时
 * 三个框必须挤在一行，Material 默认 56 dp 的最小高度会把整块面板顶出屏幕。
 */
@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
    onFocusChange: (Boolean) -> Unit
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(Str.tr(label), color = TextDim, fontSize = 8.sp)
        Box(
            Modifier
                .fillMaxWidth()
                .height(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0x1FFFFFFF)),
            contentAlignment = Alignment.Center
        ) {
            BasicTextField(
                value = value,
                // 只放行数字和负号（年份要能输公元前），长度上限防手滑粘一大串
                onValueChange = { s -> if (s.length <= 7 && s.all { it.isDigit() || it == '-' }) onValueChange(s) },
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(
                    color = Color(0xFFDCE7F5),
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                ),
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .onFocusChanged { onFocusChange(it.isFocused) }
            )
        }
    }
}

@Composable
private fun SkyPicker(current: Planet, startAtStars: Boolean, onPick: (Planet) -> Unit, onDismiss: () -> Unit) {
    var showStars by remember { mutableStateOf(startAtStars) }
    var starId by remember { mutableStateOf(current.hostStarId) }
    var subTab by remember(starId) { mutableIntStateOf(0) }
    val star = StarData.byId(starId)
    val bodies = StarCatalog.bodies(starId)
    val station = BodyCatalog.stationFor(star.id)
    // v0.9.5.3：宽度按语言给偏好值再封顶到屏幕可用宽度；列数按内容宽度算，宁少一列也不折断单词。
    val screenWidthDp = LocalConfiguration.current.screenWidthDp.toFloat()
    val dialogWidthDp = PickerMetrics.dialogWidthDp(screenWidthDp, Str.lang)
    val columns = PickerMetrics.columnsFor(PickerMetrics.contentWidthDp(dialogWidthDp))

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color(0xC0000000))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                Modifier
                    .padding(16.dp)
                    .width(dialogWidthDp.dp)
                    .heightIn(max = 560.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(Color(0xFA0B1018))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { }
                    .padding(14.dp)
            ) {
                // 标题区吃剩余宽度并允许换行 / 省略；关闭按钮固定 48 dp 方块，不参与压缩。
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        if (showStars) {
                            Text(
                                Str.tr("恒星目录"), color = Color.White, fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                Str.tr("${StarCatalog.stars.size} 颗恒星 · ${Planets.all.size} 个天体"),
                                color = TextDim, fontSize = 9.sp, lineHeight = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    Str.tr("← 恒星"), color = Accent, fontSize = 11.sp,
                                    maxLines = 1, modifier = Modifier.clickable { showStars = true }
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    Str.starName(star), color = Color.White, fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                "${Str.tr(star.spectralTypeZh)} · ${star.tempK.toInt()} K",
                                color = TextDim, fontSize = 9.sp, lineHeight = 12.sp,
                                maxLines = 2, overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    PickerCloseButton(onDismiss)
                }
                Spacer(Modifier.height(10.dp))
            if (showStars) {
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    StarCatalog.stars.forEach { st ->
                        StarRow(st, current.hostStarId == st.id) {
                            starId = st.id
                            showStars = false
                        }
                    }
                }
            } else {
                if (bodies.size > 1 && star.id == "sun") {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("行星", "卫星", "矮行星与小行星").forEachIndexed { i, label ->
                            Chip(label, subTab == i, Modifier.weight(1f)) { subTab = i }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                val shown = when {
                    star.id == "sun" -> when (subTab) {
                        0 -> bodies.filter { it.group == PlanetGroup.PLANET }
                        1 -> bodies.filter { it.group == PlanetGroup.MOON }
                        else -> bodies.filter { it.group == PlanetGroup.DWARF }
                    }
                    else -> bodies
                }
                Text(Str.tr(bodyHint(star, shown.size)), color = TextDim, fontSize = 9.sp)
                Spacer(Modifier.height(6.dp))
                Column(Modifier.verticalScroll(rememberScrollState())) {
                    if (station != null) {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PickerMetrics.CARD_SPACING_DP.dp)
                        ) {
                            PlanetCard(station, current.id == station.id, Modifier.weight(1f)) { onPick(station) }
                            Spacer(Modifier.weight((columns - 1).toFloat()))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    shown.chunked(columns).forEach { row ->
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(PickerMetrics.CARD_SPACING_DP.dp)
                        ) {
                            row.forEach { p ->
                                PlanetCard(p, p.id == current.id, Modifier.weight(1f)) { onPick(p) }
                            }
                            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun StarRow(st: CoreStar, selected: Boolean, onClick: () -> Unit) {
    val count = StarCatalog.bodies(st.id).size
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF1D3A6B) else Color(0x1AFFFFFF))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(Color(st.color.r.coerceIn(0f, 1f), st.color.g.coerceIn(0f, 1f), st.color.b.coerceIn(0f, 1f)))
        )
        Spacer(Modifier.width(8.dp))
        Text(Str.starName(st), color = Color.White, fontSize = 12.sp, modifier = Modifier.width(150.dp), maxLines = 1)
        Text(
            "${Str.tr(st.spectralTypeZh)} · ${st.tempK.toInt()} K",
            color = TextDim, fontSize = 10.sp, modifier = Modifier.weight(1f), maxLines = 1
        )
        Text(
            Str.tr(if (count > 0) "$count 个天体" else "仅恒星 · 空间站"),
            color = if (count > 0) Color(0x889FB0C8) else Color(0xFF7FD8A0), fontSize = 10.sp
        )
        Spacer(Modifier.width(10.dp))
        Text(
            Str.tr(if (st.isSun) "太阳系" else "${st.distanceLy} 光年"),
            color = Color(0x889FB0C8), fontSize = 10.sp, modifier = Modifier.width(80.dp)
        )
    }
}

private fun bodyHint(star: CoreStar, n: Int): String = when {
    star.isSun -> "太阳系：行星、卫星、矮行星三类用上面的标签切换"
    n == 0 -> "该系统目前只有恒星、没有已知行星：提供空间站观察点（位于等效应日照距离）"
    else -> "该系统有 $n 个天体：多行星系统会全部列在这里"
}

@Composable
private fun PlanetCard(p: Planet, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) Color(0xFF1D3A6B) else Color(0x1AFFFFFF))
            .clickable { onClick() }
            .padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(12.dp)
                    .clip(CircleShape)
                    .background(
                        Color(
                            p.groundTint.r.coerceIn(0f, 1f),
                            p.groundTint.g.coerceIn(0f, 1f),
                            p.groundTint.b.coerceIn(0f, 1f)
                        )
                    )
            )
            Spacer(Modifier.width(6.dp))
            // v0.9.5.3：天体名单行显示，绝不从单词中间折行；列数与字号已保证放得下。
            Text(
                Str.planetName(p), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            Str.planetKind(p), color = TextDim, fontSize = 9.sp, lineHeight = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
        if (p.group == PlanetGroup.EXOPLANET) {
            // v0.8.3：没有大气实测的必须显式标「推断」，不能让它看起来像测出来的
            if (!p.hasMeasuredAtmosphere) {
                Text(
                    Str.tr("推断 · 无大气实测"),
                    color = Color(0xFFE0A050), fontSize = 8.sp, maxLines = 1
                )
            }
            Text(
                p.confidence.labelZh,
                color = when (p.confidence) {
                    DataConfidence.OBSERVED -> Color(0xFF7FD8A0)
                    DataConfidence.MODELED -> Color(0xFFE0C070)
                    DataConfidence.UNKNOWN -> Color(0x889FB0C8)
                },
                fontSize = 8.sp, maxLines = 1
            )
        }
        Text(
            "${Str.tr("日长")} ${Str.durationShort(p.localDayHours)}",
            color = Color(0x889FB0C8), fontSize = 9.sp, lineHeight = 12.sp,
            maxLines = 1, overflow = TextOverflow.Ellipsis
        )
    }
}

private fun durationShort(p: Planet): String = Str.durationShort(p.localDayHours)

@Composable
private fun InfoDialog(planet: Planet, onDismiss: () -> Unit) {
    val star = StarData.byId(planet.hostStarId)
    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .widthIn(max = 660.dp)
                .heightIn(max = 460.dp)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0xF00B1018))
                .padding(16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${Str.planetName(planet)} · ${Str.tr("数据与规则")}", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(Str.tr("关闭"), color = Accent, fontSize = 11.sp, modifier = Modifier.clickable { onDismiss() })
            }
            Spacer(Modifier.height(10.dp))
            InfoLine("半径", "${planet.radiusKm.toInt()} km")
            InfoLine(if (planet.isGasGiantCloudTop) "参考层重力" else "表面重力", "${planet.gravity} m/s²")
            InfoLine("观察基准", planet.observationLevelZh)
            InfoLine("自转周期", Str.rotationPeriod(planet.siderealDayHours))
            InfoLine("真太阳日", Str.duration(planet.solarDaySeconds))
            InfoLine("公转周期", Str.earthDays(planet.orbitalPeriodDays))
            InfoLine("转轴倾角", "${fmt(planet.axialTiltDeg.toFloat())}°")
            InfoLine("大气", Str.scientificNote(planet.compositionZh, "composition"))
            InfoLine(if (planet.isGasGiantCloudTop) "参考层气压" else "气压", if (planet.pressureAtm <= 0.0) "真空" else "${planet.pressureAtm} atm")
            InfoLine("天空模型", Str.scientificNote(planet.skyBasisDisplayZh, "sky-model"))
            InfoLine("距母星", "${planet.sunDistanceAu} AU")
            InfoLine("母星视直径", Str.angularMinutes(planet.sunAngularDiameterDeg * 60.0))
            if (planet.group == PlanetGroup.EXOPLANET) {
                Spacer(Modifier.height(6.dp))
                InfoLine("母星", Str.starAffiliation(star))
                InfoLine("母星温度", "${planet.starTempK.toInt()} K · ${String.format("%.3f", planet.starLuminositySolar)} L☉")
                InfoLine("母星视半径", Str.solarRadius(planet.starRadiusSolar))
                val stellarSources = com.skysim.app.core.StarCatalog.visualSources(planet.hostStarId, planet.sunDistanceAu)
                if (stellarSources.size > 1) {
                    InfoLine("恒星系统", Str.binarySystem(stellarSources.map { Str.starSourceName(it.nameZh) }))
                    InfoLine("伴星视直径", Str.angularMinutes(stellarSources[1].angularDiameterDeg * 60.0))
                    InfoLine("双星说明", "伴星位置为示意值，尚未进行实时双星轨道积分")
                }
                InfoLine("距地球", Str.lightYears(planet.distanceLy))
                InfoLine("平衡温度", "${planet.eqTempK.toInt()} K")
                InfoLine("半径 / 质量", String.format("%.2f R⊕ / %.2f M⊕", planet.radiusEarths, planet.massEarths))
                InfoLine("数据可信度", planet.confidence.labelZh)
                InfoLine("大气证据", Str.atmosphereEvidence(planet))
                if (planet.skyBasisZh.isNotEmpty()) InfoLine("补充推导依据", Str.scientificNote(planet.skyBasisZh, "sky-model"))
            }
            if (planet.tidallyLocked) InfoLine("潮汐锁定", "自转 = 公转，一面永昼一面永夜")
            if (planet.isStation) {
                InfoLine("观察点", "空间站（虚构观察点，非真实航天器）")
                InfoLine("站位距离", Str.stationDistance(planet.sunDistanceAu))
                InfoLine("轨道周期", Str.duration(planet.solarDaySeconds))
                InfoLine("站内时钟", "直接使用 UCT（站不随行星自转）")
                InfoLine("天空", Str.scientificNote("无大气：纯黑，只有恒星本体与星空", "sky-model"))
            }
            InfoLine("所属恒星", Str.starAffiliation(star))
            InfoLine("本地历法", Str.localCalendarInfo(planet))
            InfoLine("本地秒长", Str.localSecondLength(planet.localSecondLengthSeconds))
            Spacer(Modifier.height(12.dp))
            Text(
                Str.timeNotes(),
                color = Color(0xFFB9C8DC), fontSize = 10.sp, lineHeight = 15.sp
            )
        }
    }
}
