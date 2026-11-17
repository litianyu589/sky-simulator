# 天空模拟器 / Sky Simulator

一个完全离线的 Android 天空模拟器：选一颗恒星或行星，选一个观测纬度，然后拖动时间轴，看天空怎样随时间改变。

A fully offline sky simulator for Android. Pick a star or planet, pick an observing latitude, drag the time axis, and watch the sky change.

## 截图 / Screenshots

仓库暂未附带真机截图，可把横屏截图放进 `docs/screenshots/` 后取消下面两行的注释：

```markdown
![HUD 与右栏控制台](docs/screenshots/main-hud.png)
![切换星球](docs/screenshots/planet-picker.png)
```

## 核心特性 / Features

- **双时间体系**：宇宙协调时间（UCT，地球式 365 天 / 24 小时，给人读的）与每颗天体按真实日长、年长建立的本地历法并行显示。本地历法有五种日期链：年.月.日、年.星.日、年.日、日.年，以及潮汐锁定天体的度分秒相位。
- **7 个纬度视角**：北极点 90°N、北回归线 23.5°N、北中纬 50°N、赤道 0°、南中纬 50°S、南回归线 23.5°S、南极点 90°S，覆盖极昼极夜与不同太阳轨迹。
- **真实日长与年长**：本地日长、年长、自转相位按天体数据表取真实值（例如火星恒星日 88642.6630 秒），不是等比例缩放。
- **多档倍速自动播放**：1× / 10× / 60× / 600× / 3600× / 86400×，按真实帧间时间差推进而非帧计数。
- **可独立开关的天文效果图层**：云层、降水、彩虹、日晕/幻日、极光、霾/沙尘、闪电，每项都有 0–100% 强度；不满足物理条件时面板写明原因，不硬画。
- **完全离线**：没有 INTERNET 权限，不联网、不上报、不加载远程资源。
- **三语界面**：简体中文 / English / 日本語，应用内即时切换并持久化。
- **横屏优先**：锁定横屏，天空铺满，控制面板浮在右侧；点天空或按按钮可隐藏全部界面，只留纯净天空。

## 天体覆盖 / Catalog

恒星与行星按 `hostStarId` 归属组织成「恒星 → 行星」两级；没有已知行星的恒星挂一个「空间站」观察点（站位取等效应日照距离 a=√(L/L☉) AU）。天体物理参数与显示参数全部在数据表中（`core/Planet.kt` 的 `Planets`、`core/PlanetsExo*.kt`、`core/PlanetsOuter.kt`、`core/StarData.kt`），代码里不按天体 id 分支，新增天体只需加一行数据。

数据置信度分三档并如实标注：大气有观测证据、大气模型推测、大气未知（按无大气处理）。纪元自转相位另有四档来源：实测锚定、实测、约定相位（设定值，非实测）、相位未定。系外行星的外观属于自洽推测，不是实测景象。

## 安装 / Install

1. 下载 [dist/sky-simulator-v1.0.apk](dist/sky-simulator-v1.0.apk)，或到 Release 页面下载附件。
2. 允许「安装未知来源应用」后安装。
3. 需要 Android 8.0（API 26）及以上；targetSdk 34。

APK 校验：`SHA-256 e2452441196de43aae153813cd490ca39d32a25f639fc68bb01e9b4c3ec20450`，5,566,822 字节。

关于权限：应用没有 INTERNET 权限。清单里唯一的 `<uses-permission>` 是 `com.skysim.app.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`，这是 Android 构建工具为 targetSdk 33+ 自动注入的应用内动态广播权限，属自签名级别，与网络无关。

关于包名：1.0 的包名是 `com.skysim.app`，与早期测试版 `com.skysim.tianyu` 不同。两者是两个独立应用，可以共存；但 1.0 无法覆盖安装旧版，旧版数据也不会迁移过来。

## 技术栈与项目结构 / Tech stack

Kotlin 1.9.10 + Jetpack Compose（BOM 2023.10.01、Material 3），自绘天空渲染，无 WebView，无网络依赖。构建用 AGP 8.1.3，compileSdk 34 / minSdk 26 / targetSdk 34，Java 17。

```
app/src/main/java/com/skysim/app/
  core/    天文与时间内核：Planet 数据表、LocalCalendar 历法、TimeSystem、Astro、
           PlaybackClock、Twilight、Terrain、Ground3D、SkyModel、Effects、QualityGovernor…
  data/    EffectPrefs（效果图层偏好）
  ui/      MainScreen、SkyView、SkyRaster、SceneControls、EffectControls、Str 语言表、各渲染器
app/src/test/java/com/skysim/app/   39 个测试文件、370 项单元测试
docs/      设计说明、数据与天体、构建与发布
dist/      已构建的 v1.0 APK
```

## 构建 / Build

需要 JDK 17 与 Android SDK 34。仓库自带 Gradle Wrapper（Gradle 8.2.1），不需预装 Gradle：

```bash
git clone https://github.com/litianyu589/sky-simulator.git
cd sky-simulator
./gradlew assembleDebug        # 调试包
./gradlew assembleRelease      # release 包（需要根目录 keystore.properties）
./gradlew test                 # 单元测试
```

Windows 用 `gradlew.bat assembleDebug`。首次运行会下载 Gradle 8.2.1 发行包（约 123 MB），之后走本地缓存。

也可以直接用 Android Studio 的 `File` → `Open` 打开仓库根目录，等 Sync 完成后运行测试或构建。

`app/build.gradle.kts` 会在根目录存在 `keystore.properties` 时启用 `signingConfigs.release`；没有这个文件时 `assembleRelease` 仍然可以产出未签名包，方便只想编译的人。请不要把 keystore 提交进仓库，详见 [docs/构建与发布.md](docs/构建与发布.md)。

## 文档 / Docs

- [设计说明](docs/设计说明.md)：时间体系与历法口径、纬度视角、效果图层的物理约束
- [数据与天体](docs/数据与天体.md)：天体数据与代码分离的约定、如何新增天体
- [构建与发布](docs/构建与发布.md)：本地构建、签名、发版流程
- [多语言说明](MULTILANGUAGE.md)、[UI 设计说明](UI-DESIGN-0.9.5.md)、[更新记录](CHANGELOG.md)

## 已知限制 / Known limitations

- 星历与天空模型是公开天文常数的近似实现，目标是视觉与教学上的自洽，不用于精密推算或导航。
- 系外行星、部分卫星的大气与表面参数是模型推测或约定值，界面会标注来源。
- 构建环境没有可用模拟器，v1.0 的最终观感仍建议在真机上按 16:9 / 20:9 手机横屏与 7–11 英寸平板各看一遍。

## 许可 / License

[MIT](LICENSE) © 2026 litianyu589

## 致谢与关于本项目 / About

产品决策、需求与验收由作者 litianyu589 完成；编码、调试、测试与文档由 AI 全程参与。这是本项目的第一个正式版，工程基线固定在此前的 0.9.5.3，后续版本号从 1.x.x 起，包名与 release 签名不再变更——否则已安装用户无法覆盖升级。

天体数据取自公开天文常数与 IAU 2015 自转要素；感谢所有公开星表与行星科学资料的维护者。
