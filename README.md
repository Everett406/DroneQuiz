<div align="center">

# DroneQuiz · 无人机装调题库

**Android 原生刷题 / 模考 / 错题本应用 · Jetpack Compose · 液态玻璃 UI**

[![Release](https://img.shields.io/github/v/release/Everett406/DroneQuiz?style=flat-square)](https://github.com/Everett406/DroneQuiz/releases)
[![Build APK](https://github.com/Everett406/DroneQuiz/actions/workflows/build.yml/badge.svg)](https://github.com/Everett406/DroneQuiz/actions/workflows/build.yml)
[![License](https://img.shields.io/github/license/Everett406/DroneQuiz?style=flat-square)](LICENSE)
![Platform](https://img.shields.io/badge/platform-Android%2012%2B-3DDC84?style=flat-square)
![Kotlin](https://img.shields.io/badge/Kotlin-2.2.20-7F52FF?style=flat-square)

内置《无人机装调题库（含解析）》**800 题**（单选 640 / 判断 160），
支持自定义题库导入、模考自动阅卷、错题强化训练与每日学习提醒。

[下载最新 APK](https://github.com/Everett406/DroneQuiz/releases/latest) · [更新日志](CHANGELOG.md) · [问题反馈](https://github.com/Everett406/DroneQuiz/issues)

</div>

---

## 目录

- [项目简介](#项目简介)
- [功能特性](#功能特性)
- [安装与使用](#安装与使用)
- [技术栈](#技术栈)
- [应用架构](#应用架构)
  - [源码结构](#源码结构)
  - [液态玻璃实现（交接重点）](#液态玻璃实现交接重点)
  - [数据层](#数据层)
- [构建指南](#构建指南)
- [CI/CD 与发版流程](#cicd-与发版流程)
- [签名体系](#签名体系)
- [题库维护](#题库维护)
- [诊断与排障](#诊断与排障)
- [已知限制与注意事项](#已知限制与注意事项)
- [工具脚本](#工具脚本)
- [版本历史](#版本历史)
- [致谢](#致谢)
- [许可证](#许可证)

---

## 项目简介

DroneQuiz 是一款面向**无人机装调员职业技能培训**的离线刷题应用。应用内置 800 道含解析的正式题库，覆盖单选与判断两类题型，提供刷题练习、全真模考、错题本、学习打卡与每日提醒等完整学习闭环，同时支持通过设置页导入自定义题库（JSON 格式）以适配其他科目。

项目采用纯 Kotlin + Jetpack Compose 构建，UI 层基于 Kyant0 的 **AndroidLiquidGlass (backdrop)** 库实现了 Android 平台少见的「液态玻璃」视觉效果（折射、色散、振动度），并为此建立了一整套**运行时守护与自动降级机制**（BootGuard / CrashGuard / 安全模式），保证在低端 GPU 或异常渲染环境下应用始终可用。全部构建、签名与发版流程由 GitHub Actions 自动化完成，本地无需 Android Studio 亦可参与开发。

> 应用完全离线运行，不申请网络权限，不收集任何用户数据。

---

## 功能特性

### 学习功能（五大页面）

| 页面 | 功能要点 |
|------|----------|
| **首页** | 学习进度环、预估通过率条、近 7 日刷题量柱状图 / 正确率折线（Canvas 自绘）、连续打卡双卡、错题提示、上次模考成绩速览 |
| **刷题** | 分类 / 题型筛选 chips、`HorizontalPager` 左右滑题、点选即判、答错抖动 + 解析弹簧展开、题号面板（`ModalBottomSheet`，单一数据源）、顺序 / 随机顺序、自动切题 |
| **模考** | 题数、判断题占比、时长、及格分全部可拖滑杆配置；倒计时（< 60s 红色脉冲）、到时自动交卷、交卷确认对话框、自动阅卷与成绩单 |
| **错题本** | 答错自动收录、「连续答对 N 次」三档移除策略、错题特训入口、展开解析 |
| **设置** | 主题三档（跟随系统 / 浅色 / 深色）、字号四档、刷题顺序、自动切题、及格分滑杆、错题移除阈值、每日 20:00 学习提醒（WorkManager）、SAF 导入自定义题库（JSON）、清空学习记录、画面特效开关 |

### 交互与视觉

- **液态玻璃 UI**：底部导航胶囊、按钮、滑杆、开关、卡片均基于 backdrop 折射渲染，选中图标透过玻璃呈墨色；所有玻璃组件带纯 Compose 降级分支
- **动画系统**：页面转场统一（淡入 + 轻缩放 280ms，单一动画源）、iOS 式过冲回弹（BounceState）、按压缩放、倒计时脉冲
- **配色**：奶油底 + 墨黑主操作 + 橙色点缀（浅色）；暖夜深色主题
- **稳定性**：全局崩溃捕获、启动心跳守护、自动安全模式、崩溃报告屏内一键复制

---

## 安装与使用

### 系统要求

| 项目 | 要求 |
|------|------|
| 系统版本 | Android 12（API 31）及以上 |
| 设备 | 手机（竖屏应用），armeabi-v7a / arm64-v8a / x86_64 / x86 |
| 网络 | 无需 |

### 安装步骤

1. 从 [Releases](https://github.com/Everett406/DroneQuiz/releases/latest) 下载最新 `DroneQuiz-x.y.z.apk`；
2. 直接安装。**v2.0.2 起所有版本使用同一固定签名，之后升级均可直接覆盖安装，无需卸载旧版**；
   - 若从 v2.0.x 旧签名版本升级，需先卸载一次旧版（签名不同导致）；
3. 首次启动若提示未知来源，按系统引导授权即可。

### 使用提示

- **题库升级**：升级到内置题库版本更新的 APK 时，应用会自动重置学习数据（题目 id 变化，旧记录无法映射），属预期行为，详见[题库版本机制](#题库版本机制)；
- **自定义题库**：设置 → 导入题库，选择符合 [questions.json 格式](#题库文件格式)的 JSON 文件；
- **画面特效**：低端设备若出现卡顿或渲染异常，可在 设置 → 画面特效 关闭液态玻璃（应用也会在检测到异常退出后自动降级）。

---

## 技术栈

| 类别 | 组件 | 版本 |
|------|------|------|
| 语言 | Kotlin（启用 `-Xcontext-parameters`） | 2.2.20 |
| UI | Jetpack Compose（BOM）+ Material 3 | 2025.06.01 |
| 导航 | androidx.navigation:navigation-compose | 2.9.8 |
| 数据库 | Room + KSP | 2.8.4 |
| 偏好存储 | DataStore Preferences | 1.1.7 |
| 后台任务 | WorkManager | 2.10.5 |
| 序列化 | kotlinx-serialization-json | 1.9.0 |
| 协程 | kotlinx-coroutines-android | 1.10.2 |
| 构建 | AGP / Gradle | 8.13.2 / 8.14.3 |
| 工具链 | JDK（编译目标 21）、KSP | 21 / 2.2.20-2.0.4 |
| SDK | compileSdk / targetSdk / minSdk | 36 / 35 / 31 |

液态玻璃核心为 **vendor 进仓库的 [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) backdrop 库源码**（`com.kyant.backdrop`，41 个 Kotlin 文件），未以 Gradle 依赖形式引入，原因与改动见[液态玻璃实现](#液态玻璃实现交接重点)。

---

## 应用架构

### 源码结构

```
DroneQuiz/
├── app/src/main/
│   ├── AndroidManifest.xml              # 权限：POST_NOTIFICATIONS、VIBRATE；竖屏锁定
│   ├── assets/questions.json            # 内置题库（800 题，version=2）
│   └── java/
│       ├── com/drone/quiz/
│       │   ├── MainActivity.kt          # 入口 Activity：启动画面、SafeModeBanner、诊断屏路由
│       │   ├── QuizApp.kt               # Application：CrashGuard 安装、WorkManager 调度
│       │   ├── BootGuard.kt             # 启动守护：心跳 / 面包屑 / 异常死亡计数 / 安全模式
│       │   ├── CrashGuard.kt            # 全局未捕获异常 → last_crash.txt → 启动报告屏
│       │   ├── data/
│       │   │   ├── db/                  # Room：AppDatabase / Entities（七表）/ Daos
│       │   │   ├── repo/Repo.kt         # 数据仓库：题库装载、版本比对、统计聚合
│       │   │   └── settings/SettingsStore.kt  # DataStore：9 个设置项（含 bankVersion、glass_effects）
│       │   ├── screens/
│       │   │   ├── HomeScreen.kt        # 首页（进度环 / 可视化图表 / 打卡）
│       │   │   ├── PracticeScreen.kt    # 刷题（Pager / 题号面板 / 点选即判）
│       │   │   ├── ExamScreens.kt       # 模考配置 + 考试 + 成绩单
│       │   │   ├── WrongBookScreen.kt   # 错题本 + 特训
│       │   │   ├── SettingsScreen.kt    # 设置（含 SAF 题库导入）
│       │   │   └── common/Common.kt     # 公共组件（进度环、滑杆、确认对话框等）
│       │   ├── ui/
│       │   │   ├── glass/               # 液态玻璃组件族
│       │   │   │   ├── GlassKit.kt      #   GlassRuntime / glass() / GlassCard / GlassButton / GlassSlider / GlassToggle
│       │   │   │   ├── GlassBottomBar.kt#   底部导航（真折射玻璃胶囊，官方 LiquidBottomTabs 移植）
│       │   │   │   ├── Bounce.kt        #   BounceState（iOS 式过冲回弹）/ rememberPressScale
│       │   │   │   └── AppIcons.kt      #   自绘描边矢量图标（底栏 5 图标 + 通用 glyphs）
│       │   │   ├── nav/AppRoot.kt       # NavHost：路由 + 统一转场 + glassMaterial() 布局
│       │   │   └── theme/Theme.kt       # 三档主题 / 四档字号缩放 / 暖夜深色配色
│       │   └── work/Notify.kt           # 每日 20:00 学习提醒（WorkManager）
│       └── com/kyant/backdrop/          # ★ vendored Kyant0 backdrop 库（Apache-2.0，勿随意重构）
│           ├── Backdrop.kt / LayerBackdrop.kt / DrawBackdropModifier.kt
│           ├── effects/（blur / lens / RenderEffect / ColorFilter）
│           ├── highlight/ shadow/ internal/（AGSL 着色器、图层记录）
│           └── catalog/utils/（DampedDragAnimation / InteractiveHighlight 等官方示例工具类）
├── .github/workflows/build.yml          # CI：构建 → 签名 → Artifact → 自动发 Release
├── scripts/                             # 工具脚本（Python，见「工具脚本」节）
├── CHANGELOG.md / NOTICE / LICENSE
├── build.gradle.kts                     # 插件版本集中声明
└── settings.gradle.kts
```

### 液态玻璃实现（交接重点）

这一节是本项目**最容易踩坑的核心知识**，接手前请务必完整阅读。

#### 组件分层

| 层 | 内容 | 说明 |
|----|------|------|
| 库层 | `com.kyant.backdrop.*`（vendored） | 官方 backdrop 库源码并入仓库：合并 `expect/actual` 为 Android 单源集、`lens()` 解除对外部 shapes 库的依赖、剥离 org.intellij 注解 |
| 组件层 | `GlassKit.kt` / `GlassBottomBar.kt` | `glass()` 修饰符（vibrancy + blur + AGSL lens 折射/色散）、`GlassCard` / `GlassButton` / `GlassSlider` / `GlassToggle`（官方 DampedDrag 移植，支持点选 + 拖动 + 步进吸附）、`GlassBottomTabs`（底层玻璃胶囊 + 隐藏染色副本 + 选中块折射染色图标，支持拖动切换） |
| 材质层 | `Modifier.glassMaterial()` | iOS regular material 观感（半透明渐变表面 + 顶部高光描边 + 轻阴影），**无采样循环风险**，用于页面内容流 |

#### ⚠️ 硬约束：记录层内禁止 drawBackdrop 采样（v2.1.0 血泪教训）

官方 issue [#54](https://github.com/Kyant0/AndroidLiquidGlass/issues/54)：**`layerBackdrop` 记录层内部如果存在采样同一 backdrop 的 `drawBackdrop` 节点（即"内容绘制自身"），会构成循环引用，在 RenderThread 触发 `SIGSEGV` native 崩溃**。该崩溃 Java 层 catch 不到，表现为首次启动即闪退。

- v2.0 的错误做法：`AppRoot` 把 `NavHost` 内容层整体 `layerBackdrop`，页面内所有玻璃组件都在记录层内采样 → 首屏必崩；
- v2.1.0 的正解：**页面内容流一律使用 `glassMaterial()`（无采样）**；真折射（`refracts = true`）只允许用于记录层**之外**的浮动元素（当前仅底部导航胶囊）；
- 玻璃上叠玻璃需走官方 `drawBackdrop(exportedBackdrop = ...)` 导出方案，不要直接嵌套采样；
- 修改玻璃相关代码后，**必须在真机（API 31+）冷启动首帧验证**，模拟器绿不代表真机不崩。

#### 运行时守护与自动降级

由于 AGSL 着色器行为依赖 GPU 驱动，项目建立了三层防御：

```
启动 → BootGuard 心跳（首帧稳定 700ms 后写 boot_state.txt 标记健康）
         │
         ├─ Java 层崩溃 → CrashGuard 捕获 → last_crash.txt → 下次启动显示「启动报告」屏
         ├─ AGSL 着色器构造失败 → runCatching 降级为纯模糊 + 普通描边（不崩溃）
         └─ 上次未标记健康即死（native 崩溃 / 被杀）
              ├─ 1 次 → 自动安全模式：GlassRuntime.enabled = false，全部玻璃组件退化为纯 Compose 渲染
              └─ 连续 2 次 → 启动时弹出诊断屏（设备信息 + 版本 + 面包屑，一键复制）
```

- `GlassRuntime.enabled` 为全局总开关，另有 DataStore 持久化用户开关 `glass_effects`（设置 → 画面特效）；
- 安全模式下用户可手动重开特效验证是否为特效所致；
- 兼容性基线：`RenderEffect` 需 API 31+，`RuntimeShader`（AGSL lens 折射）需 API 33+，低版本自动走降级分支。

### 数据层

#### Room 数据库（drone_quiz_v2.db，七表）

| 表名 | 用途 | 关键点 |
|------|------|--------|
| `questions` | 题库 | 索引 category / type；id 由 CSV 题号或题干哈希生成 |
| `practice_records` | 刷题记录 | 索引 qid / ts |
| `question_stats` | 题目维度统计 | 正确次数 / 作答次数等 |
| `exam_records` | 模考记录 | 配置、得分、用时 |
| `exam_answers` | 模考答题明细 | 索引 examId / qid |
| `wrongbook` | 错题本 | qid 唯一索引；`addedAt` **非空默认 0**（v2.0 交卷崩溃修复点，所有插入路径必须显式赋值 `System.currentTimeMillis()`） |
| `streak_log` | 连续打卡 | 按日记录 |

- 迁移策略为 `fallbackToDestructiveMigration()`：**升级改表结构会清库重建**，不要指望旧数据保留；正式发版前如改 schema，建议正确编写 Migration；
- 模考交卷曾因 `wrongbook.addedAt` 非空约束触发 `SQLiteConstraintException`，此约束已被修复并固化为编码规约。

#### DataStore（settings）

| 键 | 含义 | 默认值 |
|----|------|--------|
| `theme` | 0 跟随系统 / 1 浅色 / 2 深色 | 0 |
| `font_level` | 字号四档（0.85 / 1.0 / 1.15 / 1.3） | 1 |
| `auto_next` | 答题后自动切题 | true |
| `pass_score` | 及格分（50–95，步进 5） | 60 |
| `remove_threshold` | 错题「连续答对 N 次」移除 | 2 |
| `daily_notify` | 每日 20:00 提醒 | false |
| `practice_order` | 0 顺序 / 1 随机 | 0 |
| `glass_effects` | 画面特效（液态玻璃）开关 | true |
| `bank_version` | 已加载题库版本（与 assets 比对） | 0 |

#### 题库版本机制

`Repo.ensureBankLoaded` 启动时比对 **DataStore `bank_version`** 与 **assets/questions.json 的 `version` 字段**：

- 相同 → 跳过导入（老手机也能拿到新题库靠这个机制，v2.2.0 引入，此前仅 `count == 0` 才导入）；
- 不同 → 清空全部学习数据（刷题记录 / 统计 / 模考 / 错题 / 打卡）后重新导入，并写入新版本号。**替换内置题库时必须递增 `version`，否则存量用户拿不到新题**。

---

## 构建指南

### 环境要求

| 项 | 版本 | 说明 |
|----|------|------|
| JDK | 21（需含 javac，即 JDK 而非 JRE） | `compileOptions` 与 `kotlinOptions.jvmTarget` 均为 21 |
| Android SDK | Platform 36 + Build-Tools 35.0.0 + Platform-Tools | `sdkmanager` 安装；`local.properties` 或 `ANDROID_HOME` 指向 SDK |
| Gradle | 8.14.3 | 由 wrapper 自动下载，无需手动安装 |
| 网络 | 首次构建需访问 google() / mavenCentral() | 依赖走 Gradle 缓存后可离线 |

### 本地构建

```bash
# Debug
./gradlew assembleDebug

# Release（与 CI 产物一致，需要签名环境变量，见「签名体系」）
export DQ_KS_PATH=/path/to/dronequiz.keystore
export DQ_KS_STORE_PASS=<密码>
./gradlew assembleRelease
# 产物：app/build/outputs/apk/release/app-release.apk
```

<details>
<summary>无 Android Studio 的最小环境搭建（Linux）</summary>

```bash
# 1. JDK 21（Temurin）
# 2. Android cmdline-tools + sdkmanager
sdkmanager "platforms;android-36" "build-tools;35.0.0" "platform-tools"
# 3. export ANDROID_HOME=<sdk路径>   ← 新 shell 必须显式设置，workspace 重置后最常见漏项
./gradlew assembleRelease
```

</details>

> 常见坑：① 只装了 JRE 没有 javac → 换 JDK 21；② shell 里 `ANDROID_HOME` 未导出 → Gradle 找不到 SDK；③ Kotlin `-Xcontext-parameters` 编译器参数已在 `build.gradle.kts` 配置，不要删除，源码中有依赖该特性的写法。

---

## CI/CD 与发版流程

CI 由 [.github/workflows/build.yml](.github/workflows/build.yml) 承担，`push` 到 `main` 或手动触发（`workflow_dispatch`）时运行：

```
Checkout → JDK 21(Temurin) → Gradle Setup
  → 从 Secrets 恢复 keystore（DQ_KEYSTORE_B64 解码 + DQ_KS_STORE_PASS）
  → ./gradlew assembleRelease
  → 重命名为 DroneQuiz-${APP_VERSION_NAME}.apk
  → Upload Artifact
  → softprops/action-gh-release 自动发布到 tag ${APP_VERSION_TAG}
```

### 发版 Checklist（每版必做，四处同步）

| 位置 | 字段 | 示例 |
|------|------|------|
| `app/build.gradle.kts` | `versionCode`（每版 +1）、`versionName` | `6` / `"2.2.0"` |
| `.github/workflows/build.yml` `env` | `APP_VERSION_NAME`、`APP_VERSION_TAG` | `2.2.0` / `v2.2.0` |
| `.github/workflows/build.yml` `body:` | Release notes 文案 | 按更新内容撰写 |
| （建议）`CHANGELOG.md` | 版本变更记录 | 见现有条目 |

> ⚠️ 版本号不同步的直接后果：tag 与 APK 实际版本不一致，用户在 Release 下载到旧版本号命名的 APK；且 tag 复用时 `overwrite_files: true` 会覆盖旧附件。

---

## 签名体系

项目自 v2.0.2 起使用**固定签名 keystore**（RSA 2048，有效期 30 年，alias `dronequiz`），本地与 CI 共用同一把钥匙，保证所有渠道 APK 签名一致、可覆盖安装。

| 载体 | 内容 |
|------|------|
| 本地 | keystore 与密码保存在仓库**之外**的安全目录（本项目工作区为 `/home/z/my-project/keystore/`：`dronequiz.keystore` + `pass.txt`），**严禁提交进仓库** |
| GitHub Secrets | `DQ_KEYSTORE_B64`（keystore 文件的 base64）、`DQ_KS_STORE_PASS`（store/key 密码，两者相同） |
| 构建注入 | 本地经环境变量 `DQ_KS_PATH` / `DQ_KS_STORE_PASS`；CI 由 build.yml 解码 secret 写入 `$RUNNER_TEMP` 后同样以环境变量传给 Gradle |
| 缺省回退 | 若环境变量缺失，release 构建回退为 **debug 签名**——该产物无法覆盖安装正式版，注意识别 |

**交接必读**：keystore 一旦丢失，后续所有版本都无法以固定签名发布，用户必须卸载重装。请立即备份 `dronequiz.keystore` 与密码（至少两份异地存储，如密码管理器 + 离线介质）。需要重置 Secrets 时可运行 `scripts/setup_signing.py`（需 `pip install pynacl`，并经环境变量提供 GitHub Token）。

---

## 题库维护

### 题库文件格式（assets/questions.json）

```jsonc
{
  "version": 2,                        // 题库版本号，替换题库必须递增
  "questions": [
    {
      "id": 1,                         // int，稳定唯一（CSV 题号优先，否则题干哈希）
      "category": "无人机装调",         // 分类名，刷题筛选 chips 数据源
      "type": "single",                // "single" 单选 / "judge" 判断
      "question": "时间压力可能会导致人为差错，是因为____。",
      "options": ["A 选项", "B 选项", "C 选项"],   // judge 类型固定为 ["正确","错误"]
      "answer": 2,                     // int，options 的下标（0 起）
      "explanation": "选C。……"          // 解析，可为空串
    }
  ]
}
```

### 从 CSV 转换内置题库

```bash
python3 scripts/convert_bank.py <题库.csv> [-o app/src/main/assets/questions.json]
```

脚本能力与约定：

- 自动识别列头（题干 / 选项A–H / 答案 / 解析 / 类型 / 分类 / 题号，支持中英文变体），多编码尝试（utf-8-sig / gb18030 等）；
- 答案归一化：字母（A–H）、对/错、正确/错误、数字序号（1 起）均可解析；解析失败会逐行列出；
- 输出 `version = 2`；**若替换的是新版题库，请手动递增 version**（如改为 3）并重新发版；
- 当前内置题库：`version = 2`，800 题（单选 640 / 判断 160），单分类「无人机装调」。

`scripts/gen_bank.py` 是初版 801 题参数化生成器（11 分类），仅作为历史数据源留存，不再用于当前题库。

---

## 诊断与排障

### 用户侧（按现象）

| 现象 | 处置 |
|------|------|
| 打开即闪退 | 重新打开应用：若出现「启动报告」屏，复制内容反馈；否则应用会自动进入安全模式（顶部出现安全模式横幅） |
| 安全模式下正常，开特效后异常 | 设置 → 画面特效 关闭；将机型 + Android 版本反馈到 Issues |
| 无法覆盖安装 | 先卸载旧版再装（多为混入了 debug 签名构建或 v2.0.x 旧签名版本） |
| 想换题库 | 设置 → 导入题库（JSON），格式见上文 |

### 开发侧

| 数据 | 位置（应用私有目录） | 用途 |
|------|----------------------|------|
| `last_crash.txt` | `/data/data/com.drone.quiz/files/` | 最近一次 Java 层崩溃堆栈 |
| `boot_log.txt` | 同上（≤150 行） | 启动面包屑，定位死在启动哪个阶段 |
| `boot_state.txt` | 同上 | 心跳状态；「上次是否健康启动」决定安全模式触发 |

`adb bugreport` 或 `adb shell run-as com.drone.quiz cat files/boot_log.txt`（debug 构建可用）可直接取阅。

---

## 已知限制与注意事项

1. **破坏性迁移**：Room 未编写正式 Migration，改表结构升级即清库；正式运营阶段应补充 Migration；
2. **题库升级重置**：内置题库 `version` 变更会清空全部学习数据（设计如此，因题目 id 不具备跨版本稳定性）；若未来需要保留记录，须改为「题干哈希稳定 id + 迁移映射」方案；
3. **液态玻璃依赖 GPU 驱动**：AGSL 行为在不同厂商 GPU 上存在差异，已有三层守护兜底，但新增玻璃用法仍需真机验证（见[硬约束](#⚠️-硬约束记录层内禁止-drawbackdrop-采样v210-血泪教训)）；
4. **minSdk = 31**：Android 11 及以下不在支持范围（RenderEffect 基线）；
5. **竖屏锁定**：Manifest 固定 `portrait`，无平板 / 横屏适配计划；
6. **签名 keystore 不可再生**：丢失即失去固定签名能力（见[签名体系](#签名体系)）；
7. **无自动化测试**：当前仓库未包含单元 / UI 测试，回归依赖真机手工验证。

---

## 工具脚本

| 脚本 | 用途 | 备注 |
|------|------|------|
| `scripts/convert_bank.py` | 题库 CSV → `assets/questions.json` | 日常题库维护主入口 |
| `scripts/gen_bank.py` | 初版 801 题参数化生成器 | 历史留存 |
| `scripts/vendor_backdrop.py` | 从上游 backdrop 仓库源码 vendor 进 `com.kyant.backdrop` | `SRC` 为上游源码本地路径，升级库版本时修改后运行；vendor 后需按 NOTICE 重做 expect/actual 合并等改动 |
| `scripts/setup_signing.py` | 生成 / 校验 keystore，并写入 GitHub Secrets（`DQ_KEYSTORE_B64` / `DQ_KS_STORE_PASS`） | GitHub Token 经环境变量 `GITHUB_TOKEN` 提供；依赖 `pynacl` |

---

## 版本历史

| 版本 | versionCode | 主题 |
|------|-------------|------|
| v2.0 | 2 | 全量功能重建：真液态玻璃（Kyant0 backdrop）、五大页面、动画系统、801 题内置题库 |
| v2.0.1 | 3 | 首帧 AGSL 着色器构造失败自动降级 + CrashGuard 崩溃报告屏 + 新数据库文件名 |
| v2.0.2 | 4 | BootGuard 启动守护 + 自动安全模式 + 画面特效开关 + **固定签名** |
| v2.1.0 | 5 | **根治首启 SIGSEGV**（官方 backdrop 架构：记录层内禁用 drawBackdrop 采样，内容流改 glassMaterial）+ 滑杆居中 / 开关样式修复 |
| v2.2.0 | 6 | 修复开关圆钮恒停左侧、底栏图标消失 / 幽灵槽；接入正式题库 800 题；题库版本机制 |

完整变更明细见 [CHANGELOG.md](CHANGELOG.md)。

---

## 致谢

- [Kyant0/AndroidLiquidGlass](https://github.com/Kyant0/AndroidLiquidGlass) —— 液态玻璃核心能力来源（backdrop 库，Apache-2.0，vendor 并入并修改，见 [NOTICE](NOTICE)）
- [Jetpack Compose](https://developer.android.com/compose) / [Room](https://developer.android.com/training/data-storage/room) / [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) —— AndroidX 全家桶
- 《无人机装调题库（含解析）》 —— 题库内容由需求方提供

## 许可证

本项目代码以 [Apache License 2.0](LICENSE) 发布；`com.kyant.backdrop/` 目录源码源自 Kyant0/AndroidLiquidGlass（Apache-2.0），按其许可要求保留署名与变更说明（见 [NOTICE](NOTICE)）。内置题库内容版权归原始编制方所有，仅随本应用分发使用。


