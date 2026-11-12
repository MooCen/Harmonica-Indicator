# Harmonica Indicator · 开发者交接 README

> 写给未来的我（D 老师），以及任何接手这个仓库的人。
> 项目状态：v0.19.5，作者手写完成第一版并已发布到 GitHub；功能可用，但有若干"作者知道、代码没说"的约定。
> 读这份文档的顺序建议：第 3 节（数据流）→ 第 4 节（渲染）→ 第 5 节（悬浮窗）→ 第 8 节（已知坑）。只想快速改一个参数的话，直接看第 9 节的"改动配方"。

---

## 0. 一句话说明这是什么

把 **MIDI 文件** 解析成音符，用**悬浮窗**盖在别的 App 上，按时间轴在屏幕上"落note"，告诉你**下一个音是哪个孔、要不要升半音、要不要变调**。

主战场是 **《三角洲行动》里的口琴小游戏**（换成音游 / 任何需要看谱的 App 也成立）。

> **设计红线（务必守住）**：不联网、不读别的应用内存、不注入、不修改游戏。它**只**做两件事——读你给的 MIDI、在屏幕上画提示。这也是它属于"辅助工具"而不是"外挂"的原因；将来加功能请保持这条边界（作者就因为被误判成外挂下架过视频，很气）。

- 包名 / applicationId：`com.moocengg.harmonica_indicator`
- 仓库：https://github.com/MooCen/Harmonica-Indicator
- 语言：Kotlin（无 Compose，全部 `findViewById` + 自定义 `View.onDraw`）
- 核心依赖：`dev.atsushieno:ktmidi-android:0.12.0`（只用了 `Midi1Music` 解析）

---

## 1. 环境与构建

| 项目 | 值 | 备注 |
|---|---|---|
| Gradle | 9.5.0 | wrapper 指向阿里云镜像（`gradle/wrapper/gradle-wrapper.properties`） |
| AGP | 9.3.0 | `gradle/libs.versions.toml` |
| compileSdk / targetSdk | 37 | `compileSdk { version = release(37) }`，AGP 9 的新 DSL |
| minSdk | 26 | |
| Java | 17 编译，target 11 | `compileOptions` |
| Kotlin 插件 | **没有显式声明** | 靠 AGP 9 内置的 Kotlin 支持；不要再手动加 `kotlin-android`，会冲突 |
| viewBinding / Compose | 都没开 | 别指望 `binding.xxx` |
| 版本 | **0.21.1（versionCode 4）** | 0.20.x 加了样式预览 / SMPTE / Hold |

- 用 Android Studio 直接打开根目录即可；JDK 17。
- `local.properties` 里有 `sdk.dir`，不进 Git。
- release 构建关闭了优化：`buildTypes.release.optimization.enable = false`（AGP 9 写法，等价于不混淆不裁剪）。
- 版本号只有一个来源：`app/build.gradle.kts` 的 `versionName`，通过 `resValue("string", "app_version", versionName)` 同步到 `@string/app_version`（抽屉头、关于页都读它）。**发版时 `versionName` 和 `versionCode` 要一起改**（0.20.0 已把 versionCode 从 1 提到 2）。
- 产物路径不是标准的 `build/`：作者习惯把 APK 复制到 `app/debug/app-debug.apk`、`app/release/app-release.apk`，并且**这些 APK 已经被 Git 跟踪**（仓库里有约 8MB 二进制）。

---

## 2. 目录地图

```
app/src/main/
├── AndroidManifest.xml          # 5 个 Activity + 1 个前台 Service(specialUse) + 2 个文本 Intent-filter
├── assets/resource_packs_phigros_theme/<包名>/   # 内置资源包（目前只有 resource_phigros_replicate）
├── java/com/moocengg/harmonica_indicator/
│   ├── MainActivity.kt          # 选 MIDI / 解析 / 生成 Note / 抽屉菜单 / 今日人品
│   ├── OverlayService.kt        # 前台服务：悬浮窗、校准手势、播放循环
│   ├── NoteIndicatorView.kt     # 核心渲染：判定线、下落 note、收缩圈、打击特效（含设置页 previewMode）
│   ├── OverlayState.kt          # 单例"黑板"：Activity 与 Service 之间唯一的数据通道
│   ├── SettingManager.kt        # SharedPreferences 封装（object SettingsManager）
│   ├── SettingCodec.kt          # 设置码编解码（object SettingsCodec ← 注意文件名和类名不一致）
│   ├── ResourcePackLoader.kt    # info.yml 解析 + 资源包枚举/打开文件；也定义了 ResourcePackInfo
│   ├── ResourcePackImporter.kt  # zip 导入 / 删除外部资源包
│   ├── SettingsActivity.kt      # 设置页（外观、资源包、5 个基础参数）
│   ├── AdvancedSettingActivity.kt # 高级设置页（线型/速度/alpha + 设置码 + 重复的 5 个基础参数）
│   ├── AuthoritySettingActivity.kt # 悬浮窗/通知权限状态与跳转
│   ├── AboutActivity.kt         # 关于页（长按复制作者信息 / 仓库地址）
│   └── TodaysCharacterHelper.kt # "今日人品" 伪随机（同设备同一天恒定）
└── res/layout/
    ├── activity_main.xml                     # 抽屉 + 中间三个按钮
    ├── service_note_indicator_overlay_view.xml # ★ 悬浮窗全部结构（校准点都在这里）
    ├── activity_settings.xml                 # 553 行，纯手写，大量重复块
    ├── activity_advanced_setting.xml         # 780+ 行，同上；末尾是"样式预览"
    ├── activity_authority_setting.xml / activity_about.xml
    ├── activity_launch.xml                   # ★ 没有任何引用，旧草稿
    └── main_drawer_header.xml / main_drawer_menu.xml
```

数据类分散在两个文件里，找的时候注意：
- `syllable` / `tempoEvent` / `INFO` / `Note` → 定义在 **MainActivity.kt 顶部**（不在 model 包里，没有 model 包）。
- `ResourcePackInfo` / `HitEffect` / `HitEffectPool` → 分别定义在 `ResourcePackLoader.kt` / `NoteIndicatorView.kt` 里。

---

## 3. 数据流：从 MIDI 到屏幕

### 3.1 入口

```
用户选文件 (btnPickText → GetContent "audio/midi")
   └─ 或外部 ACTION_VIEW / ACTION_SEND 拉起（微信/QQ"用其他应用打开"）
        └─ MainActivity.readMidiFromUri(uri)   [IO 线程读字节]
             └─ parseMidiBytes()               [主线程，同步解析]
```

`parseMidiBytes()` 是整条链的核心，做四件事：解析 → 清洗 → 生成 Note → 推给 `OverlayState`。

### 3.2 解析阶段的两种中间结构

```kotlin
data class syllable(val startTick: Long, val pitch: Int, var endTick: Long = -1)
data class tempoEvent(val startTick: Long, val value: Int)  // value = 每四分音符的微秒数
```

- **音高**：只处理 `NOTE_ON`（`statusCode == MidiChannelStatus.NOTE_ON`）和 `NOTE_OFF`。
  - `NOTE_OFF`（以及力度为 0 的 `NOTE_ON`）都走 `closeSyllable()`：给**第一个"同音高且还没结束"**的音符盖上 `endTick`。
  - 找不到匹配的孤儿 `NOTE_OFF` 直接忽略；收尾阶段仍没闭合的"悬挂音符"会被 `removeAll` 丢弃并打日志（0.20.0 修的，见 8.1）。
- **速度**：META `TEMPO` 事件 → `TempoList`。
  - 解析前先塞一个默认 `tempoEvent(0, 500_000)`（120 BPM）。
  - 遇到 `nowTick == 0` 的 tempo 事件会先 `clear()`，再写入真正的初始速度。
- **division**：直接从文件头第 12、13 字节大端读（`getDivisionFromByteArray`），**没有用 ktmidi 的解析结果**。分两种制式：
  - `bit15 == 0` → **PPQ**：每四分音符 `division` 个 tick，时间靠 tempo 事件分段积分；
  - `bit15 == 1` → **SMPTE 时间码**：高字节是二补码形式的负帧率（`fps = 256 - h`），低字节是每帧多少 tick，`每 tick 秒数 = 1 / (fps × ticksPerFrame)`，**tempo 事件不参与时间轴**。`-29` 是 30fps 的 drop-frame，按 29.97 处理。
  - `CalcTimePoint()` 里靠 `smpteSecondsPerTick > 0f` 分流；`RefreshData()` 会重置这三个 SMPTE 字段（`smpteSecondsPerTick` / `smpteFps` / `smpteTicksPerFrame`），避免换歌时串味。
  - ktmidi 本身对两种制式都能正常读事件（实测过一个 SMPTE 文件：1 轨 4 事件、delta 正确），所以只需要我们自己做时间换算。

### 3.3 音高 → 轨位（`CalcPitchNum`）

```kotlin
val (lane, semitone) = CalcPitchNum(pitch)   // lane: 1..8, semitone: 是否升半音
```

- 只看 `pitch % 12`，映射到简谱音级 1–7；`remainder ∈ {1,3,6,8,10}` 表示升半音。
- 特例：`pitch == 84`（C6，quotient 7、remainder 0）→ **lane = 8**，且不算半音。
- 八度档 `tuneGear` 由**绝对音高**决定：

| pitch 区间 | tuneGear | META 后缀 | 含义 |
|---|---|---|---|
| 48–59 | -1 | `.` | 低音区 |
| 60–71 | 0 | （无） | 中音区 |
| 72–83 | +1 | `'` | 高音区 |
| 84 | +1 | `''` | 双高音（只有 C） |

- `META` 是给人看的简谱字符串，拼接顺序是 **`#` + lane + 八度后缀**，例如 `#3'` = 升 3 高八度，`5.` = 低音 5。

### 3.4 生成 `Note`（渲染单元）

```kotlin
data class Note(
    val startTime: Float,      // 秒（1 倍速）
    val endTime: Float,        // 秒；⚠️ 目前完全不参与绘制
    val type: Int,             // 轨位 or 功能点编号
    val funcType: Int,         // 1 = 演奏音符, 2 = 功能提示
    val META: String,          // 简谱文本，调试用
    val doubleRhyme: Boolean = false,
    var clickTime: Float = -1f // ★ 可变：这一颗"到点"的时刻，用于只触发一次特效/combo
)
```

**每次变速/半音状态切换时，会额外插入"提示 note"**（`funcType = 2`），这就是屏幕上那些收缩圈：

| 触发条件 | 生成的 Note | type | 对应的校准点 View |
|---|---|---|---|
| `tuneGear` 变化 | 变调提示 | `3 - tuneGear` → -1 得 4、0 得 3、+1 得 2 | 4=`calibPointDownTone`、3=`calibPointNaturalTone`、2=`calibPointUpTone` |
| `semitone` 变化 | 半音提示 | 固定 `1` | 1=`calibPointSemitone` |
| 每个音 | 演奏音符 | `lane`（1..8） | 1..8=`calibPoint1..8` |

所以 `pointDict` 的 key 是 `Pair(type, funcType)`，`(1..8, 1)` 是八条轨，`(1..4, 2)` 是四个功能点。

`doubleRhyme` 的算法：本音符相对上一个音符，`tuneGear` 变 → +1，`semitone` 变 → +1。
- 提示 note 用 `>= 2`（两个都变才算"双押"）；
- 演奏 note 用 `>= 1`（只要变了就标记）。
- 效果：`doubleRhyme` 为真的演奏 note 用 `*_mh.png`，为真的功能点圈用 `colorGood` 颜色画。

### 3.5 时间换算

```kotlin
CalcTimePoint(tick): Float  // tick → 秒，按 TempoList 分段积分
```

- 分段累加 `(Δtick × 每四分音符微秒) / (division × 1_000_000)`。
- **SMPTE 制式走另一条分支**：`tick × smpteSecondsPerTick`，和 tempo 完全无关（所以 SMPTE 文件里那些 tempo 事件只是摆设，别被它们骗了）。
- 默认曲首没有 tempo 事件时用的是 500_000（120 BPM）。
- **BPM 只取第一个 tempo 事件**：`(60000000.0 / TempoList[0].value).toInt()`，变速曲的主界面 BPM 显示就是这个值。SMPTE 文件也照这个规则显示，但那个数**只是名义 BPM**，不代表实际速度。

### 3.6 交给悬浮窗

```kotlin
WholeINFO = INFO(name, bpm, 0, notes)
OverlayState.updateINFO(WholeINFO)   // Activity 与 Service 同进程，靠 object 单例传数据
OverlayState.updateNotes(notes)
```

`OverlayState` 是**唯一**的跨页面通道（没有 Binder、没有 Intent 传参、没有数据库）：

| 成员 | 类型 | 谁写 | 谁读 |
|---|---|---|---|
| `OverlayAlive` | Boolean | Service onCreate/onDestroy | MainActivity 按钮显隐 |
| `WholeINFO` / `notes` | StateFlow | MainActivity | Service / View |
| `currentTimeMs` | StateFlow | Service 播放循环 | NoteIndicatorView |
| `combo` | StateFlow | NoteIndicatorView | Service 的 textHint |
| `calibrationContainer` | `lateinit` MutableStateFlow\<FrameLayout\> | Service.startPlayback | NoteIndicatorView.syncINFO |
| `pointDict` | `Map<Pair<Int,Int>, View>` | Service.onCreate | NoteIndicatorView |

⚠️ 进程被杀后这些状态全丢，重进 App 需要重新选曲。

---

## 4. 渲染：`NoteIndicatorView`

### 4.1 坐标系与"校准"机制（这个项目最不直观的部分）

悬浮窗里叠了一层**校准底图** `calibrationContainer`（内含 `ivCalibration` = `drawable/example.png`，尺寸 600dp×200dp）和 12 个**不可见的小 View**（标注判定点的位置）。用户把这张图拖到和游戏界面重合、再缩放，代码就认为"图上的点 = 游戏里的孔位"。

```
overlayView (全屏, WindowManager)
└── FrameLayout touchInterceptorView    ← 手指拖动/双指缩放都绑在它身上
    ├── FrameLayout layoutPauseRaycast  ← 暂停按钮（⚠️ 见第 8 节）
    └── FrameLayout calibrationContainer ← translationX/Y + scaleX/Y 就是要记的校准结果
        ├── ImageView ivCalibration (example.png)
        └── calibPoint1..8 / Semitone / UpTone / NaturalTone / DownTone
```

- 手势：单指拖动改 `translationX/Y`，双指 `ScaleGestureDetector` 改 `scaleX/Y`（限 0.2–5.0）。
- 播放前把 `translation/scale` 存进 `SettingsManager.lastCalibrationPos* / lastCalibrationScale*`；下次 `onCreate` 再读回来。
- **缩放默认值 `-1f` 是"没校准过"的哨兵值**，读到 > 0 才应用。

坐标计算（每次 `syncINFO()` 算一次）：

```kotlin
point.getLocationOnScreen(pointLoc)          // 判定点的屏幕坐标
targetX = pointLoc[0] - trackLoc[0] + point.width  * pointScaleX / 2f
targetY = pointLoc[1] - trackLoc[1] + point.height * pointScaleY / 2f
```

`trackLoc` 是 `NoteIndicatorView` 自己的屏幕坐标（全屏窗口所以基本是 0,0），减掉之后就变成 View 的局部坐标。

⚠️ 判定点位置是**在 XML 里写死的 dp 值**（八轨 `marginStart` = 42/109/175/241/308/375/442/508dp、top 109dp；功能点 top 52dp）。**换 `example.png` 必须同步改这些 margin**，否则校准点会和图对不上。

### 4.2 三种绘制形态

`funcType` 和设置项共同决定画什么：

| 情况 | 画法 | 相关设置 |
|---|---|---|
| `noteLineType == 1` | 水平判定线（`lineCorePaint`，16px 圆头） | `noteLineType` |
| `funcLineType == 1` | 功能判定线 | `funcLineType` |
| `funcType == 2` 且线型 == 2 | **收缩圆圈**（`circlePaint` / `circlePaintDoubleRhyme`） | `circleShrinkRate`、`circleLeadTime`、`circleAlphaMin/Max` |
| `funcType == 1`（或功能点线型 == 1） | **下落图片**（`*_mh.png` 或普通版） | `fallVelo`、`noteSizeBonus`、`*LineSpeed`、`*Reverse`、`*NoteType` |

**绘制顺序**：`音符 → 判定线 → 打击特效`。判定线刻意画在音符之后（音符从线下面穿过、线盖在上面），改顺序时 `onDraw()` 里这三段别弄反。

下落公式：

```kotlin
val dt = note.startTime / playSpeed - currentTimeMs / 1000f   // 还剩几秒
val y  = targetY - dt * fallVelo * noteFuncType2FuncLineSpeed[funcType]
                   * (if (reverse) -1 else 1)
```

- 正 `dt` → 音符在判定点**上方**，随时间下落到 `targetY`。
- `playSpeed` 只压缩"时间轴"，**不改变下落速度**（视觉上音符间距会变）。
- 命中判定：`currentTimeMs/1000f > note.startTime / playSpeed` 时，把 `clickTime` 置为当前时间（只触发一次），**`funcType == 1` 会 `combo + 1`**（功能提示不计），然后 `continue` 不再绘制。两条分支（指示圈 / 下落音符）都要各自判断，别只改一处。
- `*_Reverse` 用 `canvas.scale(1f, -1f, ...)` 做上下翻转，不改坐标。
- 屏幕外（`y < -noteSizeY || y > height + noteSizeY`）直接跳过。

收缩圈：

```kotlin
radius  = point.width * pointScaleX / 2.2f + dt * circleShrinkRate   // dt=0 时正好是目标半径
progress = 1f - (dt / circleLeadTime)                                 // 0=刚出现 1=到点
alpha    = circleAlphaMin + (circleAlphaMax - circleAlphaMin) * progress
```

`dt > circleLeadTime` 或 `dt < 0` 就不画。所以 **`circleLeadTime` 是"提前多久出现"，`circleShrinkRate` 是"从多大缩回来"**。

### 4.3 打击特效

- 精灵图 `hit_fx.png` 由 `BitmapRegionDecoder` 按 `info.yml` 的 `hitFx: [cols, rows]` 切帧。
- 每帧**只解码一次**并缓存到 `frameBitmapCache`；`preloadEffectFrames()` 在 Service onCreate 里全量预热。
- 特效对象走对象池 `HitEffectPool(128)`，`acquire / release`，避免每帧 new 对象。
- 播放帧率**硬编码 `effectFps = 90f`**，`info.yml` 里的 `hitFxDuration` 没被使用。
- `startPlayback()` 会 `syncINFO()` → 清空 `activeEffects` 并全部归还池、把所有 `note.clickTime` 重置为 -1，这样重播会重新触发特效。

### 4.4 参数速查（默认值来自 `SettingsManager`）

| 设置项 | 默认 | 范围（UI） | 作用 |
|---|---|---|---|
| `playSpeed` | 1.0 | 0–2.0（步长 0.05） | 时间轴倍速 |
| `fallVelo` | 900 px/s | 0–2000 | 下落速度 |
| `noteSizeBonus` | 1.0 | 0–2.0 | 音符大小倍率 |
| `circleShrinkRate` | 400 px/s | 0–2500 | 圈收缩速度 |
| `circleLeadTime` | 0.75 s | 0–5 s | 圈提前出现时间 |
| `effectSizeBonus` | 1.0 | 0–2.0 | 特效大小 |
| `circleAlphaMin/Max` | 40 / 255 | 5 的倍数 | 圈透明度渐变 |
| `backgroundAlpha` | 150 | 0–200（步长 5） | 编辑态黑底透明度 |
| `noteLineType` / `funcLineType` | 1 / 2 | 1=线 2=圈 | 两种音符的呈现方式 |
| `noteLineNoteType` / `funcLineNoteType` | 1 / 3 | 1=Tap 2=Drag 3=Flick 4=Hold | 用哪张图 |
| `holdMinTime` | 0.15 s | 0–1.0（步长 0.01） | 时值短于它的音按 Tap 画（0 = 不过滤） |
| `noteLineSpeed` / `funcLineSpeed` | 1.0 | 0–2.0 | 两组音符各自的下落速度倍率 |
| `noteLineReverse` / `funcLineReverse` | false | | 反转方向 |
| `resourcePack` | `resource_phigros_replicate` | | 当前资源包名 |
| `hideBasicSetting` | false | | 高级页折叠基础区 |
| `previewEnabled` | true | | 高级页「样式预览」开关 |
| `themeMode` | FOLLOW_SYSTEM | | AppCompatDelegate 模式 |

### 4.5 设置页的"样式预览"（`previewMode`）

**不是新写的渲染器**，而是 `NoteIndicatorView` 的一个分支：`previewMode = true` 时 `onDraw()` 直接走 `drawPreview()`，完全不碰 `pointDict` / `calibrationContainer` / `OverlayState`。画笔、贴图、公式全部复用，改一处两边都生效。

- 位置：`activity_advanced_setting.xml` 最底部（"重置所有高级设置"按钮之后）。
- 结构：`样式预览` 标题 → `启用预览` 勾选框 → `advanced_setting_preview_container`（说明文字 + 200dp 的 `<NoteIndicatorView>`）。勾选框状态存在 `SettingsManager.previewEnabled`（默认 true）。
- 开关必须同时做两件事：`previewView.previewMode = false` **和** 容器 `GONE`。只 `GONE` 是停不掉帧循环的（视图还在窗口里，照样每秒画 60 次）。
- 两条线：上 = funcLine（28% 高度）、下 = noteLine（72% 高度），和校准图里"功能点在上、八条轨在下"一致。
- **每条线各按自己的 `lineType` 渲染**：`1` = 判定线 + 下落音符图，`2` = 收缩指示圈（半径 / alpha 用和真实绘制同一套公式）。所以"音符线选了音符、功能线选了指示圈"这种混搭也能正确预览。
- 三拍循环：第 0 拍只有 noteLine 单押；第 1 拍 funcLine 单押 + noteLine 双押；第 2 拍两条都双押。

**尺寸怎么算的（这段踩过坑，别乱改）**：预览 = 缩小版的真实悬浮窗，所有尺寸先按真实公式算，再统一乘 `previewScale`：

```kotlin
previewScale = 预览宽度 / max(屏幕宽, 屏幕高)      // 横屏屏宽，真机上约 0.4
音符宽高   = bmp尺寸 × 校准缩放² × noteSizeBonus ÷ 7 × previewScale
判定圈半径 = (50dp × 校准缩放 ÷ 2.2 + 剩余时间 × circleShrinkRate) × previewScale
```

- **`÷ 7` 和"校准缩放²"都是从真实绘制里抄来的**（真实代码 `noteSizeX` 里除了一次 7，`halfX` 里又乘了一次 `pointScaleX`）。不抄这份公式，`click.png`（1000×100 的超宽条）就会被撑成一根糊满预览区的大棍子。
- `previewScale` 用 **max(宽, 高)** 而不是 `widthPixels`：悬浮窗是横屏的，而设置页是竖屏，用 `widthPixels`（≈1080）会算大一倍。
- 校准缩放取 `lastCalibrationScaleX`（没校准过是 -1，按 1 算）。所以用户改了校准缩放，预览里的音符大小也会跟着变——这是对的，真机就是那个大小。

- 节奏：`beatDur = (circleLeadTime + 0.35).coerceIn(0.9, 2.5)`，音符的行程 = 判定线到"进场边"的距离（反转时从底边进场），下落速度也乘了 `previewScale`，所以"从边缘落到线"的秒数和真机一致。
- 刷新：内部 `postOnAnimation` 循环 60fps 重绘，**10Hz** 把 `SettingsManager` 灌进来（`syncVisualSettings()`）——拖滑块能实时看到效果，又不会每帧读 SharedPreferences。
- 资源包：`ensureResourcePack()` 只在包名变化时重读磁盘。不要在 10Hz 循环里调 `loadResourcePack()`，那会反复解码 5 张 PNG。
- 省电：`onWindowVisibilityChanged` 不可见就停循环，`onDetachedFromWindow` 里 `stopPreviewTicker()` + 释放资源。

三个同步方法的分工（别混）：

| 方法 | 谁调 | 行为 |
|---|---|---|
| `syncVisualSettings()` | 两边都调 | 只赋值，不碰资源包 |
| `syncFromSettings()` | `OverlayService.syncSetting()` | 强制 `loadResourcePack()` + 赋值（保留"每次开播重试加载"） |
| `ensureResourcePack()` | 预览 ticker | 包名变了才加载 |

---

### 4.6 Hold 长条（0.20.1）

- **什么时候是 Hold**：`noteFuncType2NoteDrawableMap[funcType] == 4`（说明当前样式选的是 Hold）**且** `endTime - startTime >= holdMinTimeSec`。两个条件缺一不可：
  - 时值短于 `holdMinTimeSec`（设置项 `holdMinTime`，默认 0.15s）的音会退回 Tap 画法，免得满屏都是短长条；
  - ⚠️ **这个阈值只管"唱名区"（`funcType == 1`）**：功能提示音的时值恒为 0，套上阈值会永远被降级，等于废掉"功能线选 Hold"；
  - 指示圈模式（`lineType == 2`）走另一条分支，跟 Hold 无关。
- ⚠️ **atlas 必须跟着实际用的那张贴图走**：功能线固定用 `*_mh` 贴图，所以要取 `holdAtlasMH`。如果拿 `hold_mh.png` 配单押的 `[50,53]`，头部会取到那张图**底部几乎全透明**的 53px，画出来就是"只有尾、没有头"（0.21.1 踩过这个坑）。
- **几何**：`headY = targetY - (startTime - now) × fallSpeed`，`tailY = targetY - (endTime - now) × fallSpeed`。头过线后被截在判定线上（`bottom = min(headY, targetY)`，反转时是 `top = max(headY, targetY)`），所以**整条永远不会越过判定线**。
- **头过线后头部贴图立刻不再绘制**（`showHead = false`，长条只留中间+尾），也就是视觉上"被判定线吃掉"；尾到线则整条消失。
- **Hold 期间每 0.25 秒补一次击打特效**：用 `Note.holdFxTime` 单独记时（**不要复用 `clickTime`**，那是"头命中"的标志）。命中那一帧会把 `holdFxTime` 也设成当前时刻，避免刚命中就叠一次特效。
- **零时长的 Hold**（例如把功能线样式设成 Hold、功能提示音 start==end）：只拼头+尾、中间不画，整体居中在锚点上。
- **三段拼接**（`drawHoldBar`）：纹理按 `holdAtlas = [上端px, 下端px]` 切成「尾 / 中 / 头」——尾和头按原始像素比例画，中间那段拉伸填满。双押用 `holdAtlasMH`，没配就退回 `holdAtlas`；两套都没有就按 0 处理（整张拉伸）。
- ⚠️ **头在纹理下端**（贴判定线那端），这是按内置素材和作者的示意图定的。换皮肤后如果发现头尾反了，把 `drawHoldBar` 里第一段和第三段的 src 矩形对调即可。
- **combo / 特效**仍然只在"头"碰到线时触发一次（靠 `clickTime` 去重），尾到线不额外触发。
- 条太短（头段 + 尾段的固定高度加起来比整条还长）时，两段会按比例压小，避免重叠。
- 预览视图里选 Hold 会给一个固定 0.5 秒的假长度，用同一套拼接逻辑。

---

## 5. 悬浮窗：`OverlayService`

- 前台服务，`foregroundServiceType="specialUse"`（`PROPERTY_SPECIAL_USE_FGS_SUBTYPE = overlay_hint_for_external_app`），通知渠道 `overlay_channel`。
- 窗口：`TYPE_APPLICATION_OVERLAY`，`FLAG_NOT_FOCUSABLE`，`PixelFormat.TRANSLUCENT`，`screenOrientation = LANDSCAPE`（**靠窗口强制横屏**，Android 12+ 起对非全屏窗口可能被系统忽略，真机要验）。
- API 31+ 把 `alpha` 设为 0.8（注释说不设 ≤0.8 时 `NOT_TOUCHABLE` 不生效）。

模式与窗口状态：

| 状态 | 窗口尺寸 | 触控 | 可见性 |
|---|---|---|---|
| 编辑态（onCreate 后） | MATCH_PARENT | 可点 | 校准图 + 控制条 + textHint 可见，`noteTrack` GONE |
| 小窗预览（`btnEditMode`） | WRAP_CONTENT | 可点 | 校准层 GONE |
| 播放态（`btnPlay`） | MATCH_PARENT | `FLAG_NOT_TOUCHABLE` | 校准层 INVISIBLE、控制条 GONE、`noteTrack` VISIBLE |

播放态做的事：存校准值 → 写 `OverlayState.calibrationContainer` → 透明背景 → `syncSetting()` → `syncINFO()` → 启动 60fps 帧循环。

播放循环：

```kotlin
now = elapsedRealtime() - startRealtime - 3000   // ★ 3 秒预滚：起始时间是负数
OverlayState.updateCurrentTime(now)
awaitNextFrame()  // Choreographer
```

`awaitNextFrame()` 用 `suspendCancellableCoroutine` + `Choreographer.postFrameCallback`，取消时会 `removeFrameCallback`。`MainActivity` 里有一模一样的实现（那个循环其实只刷新一行文字）。

`syncSetting()` 是"设置 → View"的唯一同步点：把 `SettingsManager` 的所有视觉参数灌进 `NoteIndicatorView` 的 `var` 字段。

---

## 6. 设置体系与"设置码"

- `SettingsManager`：`object`，SharedPreferences 名 `harmonica_settings`，所有 key 以常量集中定义，属性写成 `var x: Float get() = sp.getFloat(...) set(v) = sp.edit()...apply()`。
- ⚠️ **`SettingsManager.init(context)` 必须在任何读取之前调用一次**。原来只在 `MainActivity.onCreate` 调，现在 `SettingsActivity` / `AdvancedSettingActivity` / `OverlayService.onCreate` 也各自兜底（若进程被回收后直接从设置页恢复、或系统单独重启 `START_STICKY` 的服务，`sp` 会是未初始化状态 → `UninitializedPropertyAccessException`）。`init` 内部有幂等判断，多调无害。
- `TodaysCharacterHelper` 用的是**同一个 SP 文件**，自己读写 `device_uuid`（不经过 SettingsManager）。

### 设置码（`SettingsCodec`）

用于跨设备分享配置，格式：`[版本前缀] + N 个字符`，每字符是 64 字符表 `A-Za-z0-9-_` 的一格（0–63）。

| 类型 | 前缀 | 长度 | 字段顺序 |
|---|---|---|---|
| 高级 | `A` | 1+10 | alphaMin/5, alphaMax/5, funcLineSpeed×20−10, noteLineSpeed×20−10, funcLineType−1, noteLineType−1, funcLineNoteType−1, funcLineReverse, noteLineNoteType−1, noteLineReverse |
| 基础 | `B` | 1+5 | (fallVelo−400)/25, noteSizeBonus×20−10, circleShrinkRate/25−4, circleLeadTime×20, effectSizeBonus×20−10 |

- `enc()` 内部 `coerceIn(0, 63)`，**超范围的值会被静默截断**（例如 `fallVelo = 2000` 会还原成 1975，代码注释里写明了"无所谓"）。
- `decode*()` 直接写 `SettingsManager`，**有副作用**；返回 `DecodeResult.Success(字段数)` / `Failure(原因)`。
- ⚠️ **改动字段数量或顺序 = 旧设置码全部失效**，没有版本兼容逻辑（前缀只有 A/B 两种）。
- 两个设置页的关系：`SettingsActivity` 管外观/资源包/5 个基础项；`AdvancedSettingActivity` 把同样的 5 个基础项又实现了一遍（可用 `hideBasicSetting` 折叠），并多了 alpha 区间、两组线型/速度/方向、设置码入口。

---

## 7. 资源包规范

查找顺序（`ResourcePackLoader.openPackFile`）：**外部目录优先，找不到回退 assets**。

```
① 内置：app/src/main/assets/resource_packs_phigros_theme/<packName>/
② 导入：getExternalFilesDir(null)/resource_packs/<packName>/
```

`info.yml`（极简 YAML：只支持顶层 `key: value`，不支持嵌套/列表块/多行）：

| key | 类型 | 是否被使用 | 说明 |
|---|---|---|---|
| `name` / `author` / `description` | 字符串 | name 仅日志 | |
| `hitFx` | `[cols, rows]` | ✅ | 特效图集切分 |
| `colorPerfect` / `colorGood` | `0xRRGGBB` 或十进制 | ✅ | 判定线/圈/特效着色（`PorterDuff.SRC_IN`） |
| `holdAtlas` / `holdAtlasMH` | `[上px, 下px]` | ✅ 0.20.1 起生效 | Hold 长条的裁剪，上端=尾、下端=头 |
| `hitFxDuration` | float | ❌ | 实际帧率硬编码 90fps |
| `hitFxScale` | float | ❌ | |

图片/音频文件命名（`NoteIndicatorView.noteFileNames`）：

```
click.png     click_mh.png      # Tap   / 双押版
drag.png      drag_mh.png       # Drag
flick.png     flick_mh.png      # Flick
hold.png      hold_mh.png       # Hold（三段拼接：头 / 中拉伸 / 尾）
hit_fx.png                      # 打击特效图集
click.ogg drag.ogg flick.ogg ending.mp3   # ⚠️ 代码里没有任何播放音频的逻辑
```

`_mh` 版本用于 `doubleRhyme == true`；功能点（`funcType == 2`）固定取 `_mh` 图（实际画的是圈，用不上）。

导入（`ResourcePackImporter.importFromZip`）：
- 包名 = zip 文件名去掉 `.zip`；
- 与 assets / 外部同名 → 拒绝；
- 自动识别并剥离 zip 的公共顶层目录；
- 有 Zip Slip 防护（`canonicalPath` 前缀校验）；
- 解压后**必须有 info.yml**，否则整个目录删掉并报错；
- `deleteImportedPack` 只删外部目录，绝不动 assets。

⚠️ `listAllPacks()` 会把 **assets 下的任意子目录**都算作资源包（不校验 info.yml），而外部包要求必须有 info.yml。assets 里放一个没有 info.yml 的目录，会出现在下拉列表里但选择后加载失败。

---

## 8. 已知问题 / 待办

> 标 **[推断]** 的是逻辑推断，没有实机验证。

### 8.1 已修复（2026-11-10，作者点头）

| # | 问题 | 改法 |
|---|---|---|
| 1 | **悬挂音符会让解析崩**（`for` 里 `remove` → `ConcurrentModificationException`） | 改成 `syllables.removeAll { it.endTick < 0 }`，并打一条丢弃数量的日志 |
| 2 | 悬挂音符的**根因**：`NOTE_ON` 力度 0 没当 `NOTE_OFF` | 按 MIDI 规范判断 `event.message.lsb == 0` → 走收尾逻辑（`lsb` 是力度，`msb` 是音高，别记反） |
| 3 | 收尾逻辑原来内联在两处、还带 `break` | 抽成 `MainActivity.closeSyllable(pitch, tick)`：只改 `endTick`、不动列表结构、找不到匹配就静默返回 |
| 4 | **损坏/非标准 MIDI 直接崩** | `Midi1Music.read()` 包 `try/catch`：清空状态 + 提示文案 + Toast，不再冒泡到主线程 |
| 5 | 通知点不动（文案说"点击返回 App"却没有 `contentIntent`） | 补 `PendingIntent.getActivity(...)`，必须带 `FLAG_IMMUTABLE`（Android 12+ 强制，不带会抛 `IllegalArgumentException`） |
| 6 | `SettingsManager.init()` 只在 MainActivity 调 | `SettingsActivity` / `AdvancedSettingActivity` / `OverlayService.onCreate` 都补上（`init` 内部有幂等判断，重复调没有代价） |
| 7 | 三处 `$"文件：..."` | 去掉多余的 `$`（它本来就不起作用，只是笔误） |
| 8 | `startPlayback()` 的空判断用了 `&&` | 改成 `||` |
| 9 | 双押圈不跟随 alpha 渐变（只设了 `circlePaint.alpha`，漏了 `circlePaintDoubleRhyme.alpha`） | 两支画笔都设；不然双押圈恒为全不透明，和单押圈一起出现时很割裂 |

**验证方式**：这次没跑 Gradle（沙箱里申请不到写 `~/.gradle` 的权限），而是用 Kotlin 2.2.10 编译器 + 真实依赖（`android.jar` + 从 AAR 里抽出的 `classes.jar` + `ktmidi` + `coroutines`）+ 自动生成的 `R` 存根，把 13 个 kt 全量编译了一遍：**EXIT=0，70 个 class，只剩一条原本就有的 `BitmapRegionDecoder.newInstance` deprecated 警告**。功能层面（真机点一遍悬挂音符的谱子）还没人验过，下次发版前建议实测一首。

### 8.2 仍然存在，作者确认先不动

1. **"暂停触控遮挡"按钮**：作者的设计意图是——校准阶段窗口是"可点"的（会挡住游戏），点这个按钮后 **6 秒内**窗口变 `FLAG_NOT_TOUCHABLE`（触控穿透到游戏，方便没有小窗的机型切出去），进度条走完再恢复遮挡。它和 `touchInterceptorView` 同生共死，所以校准界面隐藏后按钮也跟着不可见，这是**预期行为**，不是 bug。只是命名（`pauseRaycast` / "暂停触控遮挡"）不直观，将来改的话建议改成 `toggleTouchPassthrough` 之类。
2. **判定线水平中心**：`noteLineX = widthPixels` 看着像少除 2，但作者实机体感与用户反馈都没问题，**保持现状**。
3. **同一 layout 内重复 id**：`activity_settings.xml` 里 `setting_playSpeed_RESET` 出现 7 次、`activity_advanced_setting.xml` 5 次，`findViewById` 只命中第一个。作者决定不修——行内 RESET 的必要性不高，有"设置码"和底部"重置全部"就够。**真要做行内重置，必须先改 id。**
4. **Hold 长条**：~~等规则再实现~~ **0.20.1 已实现**（见 4.6）。仍然只做显示：不做"松手断 combo / 尾判"——那需要无障碍服务去记录触控，作者认为有外挂嫌疑，排到很后面。
5. **音频资源**：`.ogg` / `.mp3` 在 assets 里但没接 `SoundPool`，暂时不需要。
6. **死代码**：`extractBpm()` / `extractTrackName()`、`countdown`、`calculatePointPosByCalibPoint()`、`activity_launch.xml`、`calibration_dot.png`、`baseline_code_off_24.xml`、`keepRules/rules.keep`、`setting_colorRow`。**作者有意留着备用，别顺手删。**
7. **坐标缩放被乘两次**：`noteSizeX = bmp.width * pointScaleX / 7` 之后又 `* pointScaleX`，调大小的时候注意是平方级影响。能跑就不动。
8. **`MainActivity` 的 60fps 循环**：只刷新一行文本，和 `fixedUIJob` 职责重叠。**作者是 Unity 出身，故意留一个"安心的 Update()"，别优化掉。**
9. **切换资源包后不重新预热特效帧**：`preloadEffectFrames()` 只在 Service `onCreate` 调一次。能跑就行，真卡了再说。
10. **`versionCode`**：0.19.5 之前恒为 1，0.20.0 已提到 2；以后每次发版记得 +1（见 8.3）。

### 8.3 发版规范建议（作者问过"业界怎么做"，记下来）

- **两个号同时动**：`versionCode` 必须是**只增不减的整数**（Play 商店/系统升级判定用它，目前恒为 1，将来想覆盖安装/上架必须改）；`versionName` 给人看，建议 `0.19.5 → 0.20.0 → 1.0.0` 语义化（破坏兼容/加功能/修 bug）。
- **release 打开 R8**：现在 `optimization { enable = false }` 等于不混淆不裁剪。正式上架建议开 `minifyEnabled` + `shrinkResources`，并用 `keepRules/rules.keep` 显式保留反射/自定义 View（这个项目全是 `findViewById` + 自定义 View，风险很低）。
- **APK 不要进 Git**：现在是 `app/debug/app-debug.apk`、`app/release/app-release.apk` 都被跟踪，每次发版仓库涨 ~8MB。业界做法是本地/CI 产出，走 **GitHub Releases**（附件）或 Actions artifact，仓库里只留源码 + 版本号。
- **签名密钥**：`*.jks` / `keystore.properties` 永远不进仓库，放在 CI secret 或本地；一旦泄漏，Play 上无法换签名（除非用 Play App Signing 托管）。
- **当前这套够用**：个人项目用 GitHub Releases 手动上传 APK 完全 OK，上面几条是"哪天想认真发版"时的清单。


---

## 9. 改动配方

**加一个视觉参数**
1. `SettingManager.kt`：加 `private const val KEY_XXX`，加 `var xxx` 属性（带默认值）。
2. `SettingsActivity` / `AdvancedSettingActivity`：XML 加条 `<SeekBar>` 或 `<Spinner>`，代码里 `findViewById` + 初始化 + `setOnSeekBarChangeListener` 写回。
3. `OverlayService.syncSetting()`：`noteTrack.xxx = SettingsManager.xxx`。
4. `NoteIndicatorView`：加 `var xxx`，用它参与绘制。
5. （可选）`SettingCodec`：要给分享码带上就加一位——**会让旧码失效**，考虑升个前缀版本。

**加一种功能提示点（比如"颤音提示"）**
1. `service_note_indicator_overlay_view.xml`：在 `calibrationContainer` 里加一个 `View`（定位用 dp margin），id 例如 `calibPointVibrato`。
2. `OverlayService.onCreate` 的 `pointDict` 里注册 `5 to 2 to findViewById<View>(R.id.calibPointVibrato)`。
3. `MainActivity.parseMidiBytes()`：在检测到对应事件时 `notes.add(Note(CalcTimePoint(tick), ..., type = 5, funcType = 2, "vibrato"))`。
4. `NoteIndicatorView.onDraw()`：`funcType == 2` 的分支里按 `note.type` 决定画圈/画图/画别的。

**加一种 note 外观（新的图片类型）**
1. 资源包里加图（`xxx.png` / `xxx_mh.png`）。
2. `NoteIndicatorView.noteFileNames` 加 key（注意 key 是 `(funcType 映射后的编号 to doubleRhyme)`）。
3. `AdvancedSettingActivity.noteTypes` 列表加条目 → spinner 的 `position + 1` 自动变成新的 `noteLineNoteType`。
4. 别忘了 `_mh` 变体和 `loadNoteDrawables()` 的"缺图跳过"逻辑。

**加一个页面**
`AndroidManifest.xml` 注册 Activity（非启动页 `exported=false`）→ `main_drawer_menu.xml` 加 `<item>` → `MainActivity` 的 `nav_View.setNavigationItemSelectedListener` 加分支 → 新 Activity 里记得 `enableEdgeToEdge()` + `ViewCompat.setOnApplyWindowInsetsListener`（全项目统一这个套路）。

**给 Hold 加判定（远期的活）**
现在 Hold 只画不判。要做"松手断 combo / 尾判"，绕不开**记录触控**（无障碍服务或 `FLAG_NOT_TOUCHABLE` 之外的手段），作者认为那一步会让软件更像外挂，所以明确排在很后面。真要做的话，落点大概在：`NoteIndicatorView` 里给每条 Hold 记一个"头已判/尾已判"状态（`clickTime` 只能表达"头"，别复用），配合 `OverlayService` 的触控回调，别去动 `syncINFO()` 的重置逻辑。

**发版**
改 `versionName`（`versionCode` 也改一下）→ `gradlew assembleRelease` → 把 APK 复制成 `app/release/Harmonica-Indicator-release-vX.Y.Z.apk` → 打 tag 推 GitHub。

---

## 10. 术语表

| 词 | 含义 |
|---|---|
| syllable | MIDI 原始音符（tick 计），解析中间态 |
| tempoEvent | 变速点，`value` = 每四分音符微秒数 |
| Note | 渲染单元，秒为单位，带 `type`/`funcType` |
| funcType 1 / 2 | 演奏音符（8 条轨） / 功能提示（变调、半音的圈） |
| lane | 简谱音级 1–8（8 = 高音 1 / C6） |
| tuneGear | 八度档：-1 低音区、0 中音区、+1 高音区 |
| doubleRhyme | 双押标记：本音符相对上一个音同时发生调性/半音变化 |
| META | 给人看的简谱串，如 `#3'`、`5.` |
| calibrationContainer | 校准底图容器，拖动/缩放的就是它 |
| pointDict | `(type, funcType) → 校准点 View` 的映射，坐标系的地基 |
| 设置码 | `A`/`B` 开头的 64 进制短码，跨设备分享配置 |
| 资源包 | 一套图片 + info.yml，内置在 assets 或用户 zip 导入 |

---

## 11. 备份与版本

- **源码/布局备份**：`backups/HarmonicaIndicator-src-v0.19.5-<时间戳>.zip`（含 `app/src` 全部、`gradle*`、`keepRules`、`.gitignore`；不含 `build/`、`local.properties`、`.idea`）。`backups/` 已加入 `.gitignore`。
- 这份 zip 是 **改动前** 的原始快照（也就是已经发到 GitHub 的 v0.19.5）；之后的改动一直在**工作区里、尚未提交**，`git diff` 就能看到全貌。
- Git 远端：`origin → github.com/MooCen/Harmonica-Indicator`，当前分支 `master`，最新提交 `66759c5`（v0.19.5）。
- 历史里 `936b086` 是初版，`df82ae2` 加 GitHub 链接，`66759c5` 清理 AndroidManifest。

### 0.21.1 改动清单（2026-11-12，未提交）

- 新增设置 **`holdMinTime`**（"唱名"区音符样式下方）：时值短于设定值的 Hold 按 Tap 显示，0–1.0s、步长 0.01、默认 0.15s；"唱名"区切到指示圈时这一栏跟着隐藏。**只作用于唱名区（noteLine）**，功能线不受影响。
- **判定线改为在音符之后绘制**（线在顶层，音符从线下面穿过），预览视图同步；绘制顺序现在是 `音符 → 判定线 → 打击特效`。
- 设置页音符样式 `Hold(极短版)` 更名为 **`Hold`**（现在真的会拉伸了，不再"极短"）。
- **Hold 长条**：按 `holdAtlas` / `holdAtlasMH` 三段拼接（头尾定长、中段拉伸）。
- 头过线后**头部贴图立即不再绘制**，长条被判定线一节节"吃掉"；尾到线整条消失。
- Hold 期间（头已贴线、尾未落线）**每 0.25 秒补一次击打特效**，用 `Note.holdFxTime` 独立记时。
- **零时长的 Hold** 只画头+尾、不画中间（修掉"功能线设成 Hold 时渲染整张图"的问题）。
- 预览视图的 Hold 改成 **0.3 秒**，并把"被判定线吃掉"的过程也演出来。
- `versionName` 0.20.1 → **0.21.1**，`versionCode` 3 → 4。

### 0.20.1 改动清单（2026-11-12，已提交 `5764f98`）

- 支持 **SMPTE 时间码** MIDI：`division` 高字节按二补码取负帧率、低字节为每帧 tick 数，`CalcTimePoint()` 分流成 `tick × 秒/tick`，与 tempo 无关；`-29` 按 29.97（drop-frame）处理。
- 解析完成后只弹**一条摘要**：`格式 → 速度参数 → 有无变速 → 音符数`，例如
  `格式：PPQ，BPM 120（480 tick/四分音符）；无变速；音符 250 个` 或
  `格式：SMPTE，25 fps × 40 ticks/帧（1000 tick/秒）；变速不生效；音符 250 个`。
  之前"格式"和"变速/音符数"是两条 Toast，现在合成一条（`buildParseSummary()`）。
- **combo 只统计 noteLine 上的音符**（`funcType == 1`），功能提示圈（`funcType == 2`）不计；两条线无论用"音符"还是"指示圈"渲染，判定口径都一样。
- `RefreshData()` 重置 SMPTE 三个字段，避免切歌串味。
- 解析完成后的提示：SMPTE 文件不再报"变速次数"，改为显示时间码参数。

**验证**：用一个手工构造的 SMPTE MIDI（`division = 0xE728`，25fps × 40 ticks/帧）实测 ktmidi 能正常读出事件；`1000 tick/秒` 下 note on→off 的 100 tick 正好 = 0.1s。**真机还没跑过**，等作者构建确认。

### 0.20.0 改动清单（2026-11-10，未提交）

**新增**
- 高级设置页底部「样式预览」：`NoteIndicatorView.previewMode` + `activity_advanced_setting.xml` 末尾的预览容器（详见 4.5）。
- 「启用预览」勾选框，状态存在 `SettingsManager.previewEnabled`（默认开）。

**修复**
- 预览音符尺寸改成照抄真实绘制公式（`图片尺寸 × 校准缩放² × 倍率 ÷ 7`）再等比缩小；之前按"视图高度 × 0.13"算，`click.png`（1000×100）会被撑成 780px 宽的大长条。
- 悬挂音符解析崩溃（`removeAll` + 力度 0 的 `NOTE_ON` 当 `NOTE_OFF`）
- 损坏 MIDI 直接崩（`try/catch`）
- 通知点了没反应（补 `contentIntent`）
- 进程被回收后从设置页/服务恢复会崩（`SettingsManager.init` 兜底）
- 双押圈不跟随 alpha 渐变
- `startPlayback()` 空判断 `&&` → `||`
- 三处 `$"..."` 笔误

**版本**
- `versionName` 0.19.5 → **0.20.0**，`versionCode` 1 → **2**

改动文件：`MainActivity.kt`、`OverlayService.kt`、`NoteIndicatorView.kt`、`SettingsActivity.kt`、`AdvancedSettingActivity.kt`、`activity_advanced_setting.xml`、`build.gradle.kts`、`.gitignore`。

---

## 12. 给未来接手者（包括未来的我）的三句话

1. **这个项目的本质是"把 tick 变成屏幕上一个 y 坐标"**，所有复杂度都在时间换算和校准坐标系上，先啃这两块，别急着看 UI。
2. `OverlayState` 是全局黑板，改任何跨页面数据都先看它——但也要记得它**在进程死亡时不留痕迹**。
3. 新增设置项时，**四个地方要同时改**（SettingsManager、设置页、syncSetting、View），漏一个就会出现"设置能保存但不生效"的经典症状。
