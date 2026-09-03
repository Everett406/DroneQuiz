# 更新日志 (Changelog)

本文件记录 DroneQuiz 每个版本的变更明细。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本（MAJOR.MINOR.PATCH）。

## [Unreleased]

- 待用户复测项：① 开关圆钮随状态左右滑动；② 液态玻璃开启时底栏 5 图标可见、无幽灵图标、选中图标透过玻璃呈墨色；③ 内置题库为正式 800 题
- 已知改进方向：补充 Room 正式 Migration、自动化测试、题库 id 跨版本稳定性方案

## [2.2.0] - 2026-09-03

### 修复

- **开关圆钮恒停左侧**（`GlassKit.GlassToggle`）：圆钮位移计算误用圆钮自身宽度（24dp）而非轨道宽度（52dp），导致位移恒为 -4dp；改为 `3dp + 22dp × progress`（两侧各留 3dp），圆钮现随开关状态正常滑动
- **底栏图标消失 / 幽灵槽 / 白团**（`AppRoot tabIcon`）：官方 `LiquidBottomTabs` 约定 `tabIcon(-1)` 渲染整排图标位，此前 `-1` 误入 when-else 分支只渲染单个全宽槽——导致底层玻璃胶囊仅显示居中幽灵「☰」、隐藏染色层无图标可折射（选中块白色圆团根因）。修复为 `-1` 时 `repeat(5)` 渲染全部 `TabIconSlot`

### 新增

- **题库版本机制**：`Repo.ensureBankLoaded` 由「仅 count==0 导入」升级为 DataStore `bank_version` 与 assets `version` 比对；不一致时清空学习数据后重导入（`ExamDao` 补 `clearExams` / `clearExamAnswers`）；`MainActivity` 以 `flow.first()` 取持久化值，避免组合态初值误触发
- 接入正式题库《无人机装调题库（含解析）》**800 题**（单选 640 / 判断 160，CSV 无分类列，统一命名「无人机装调」），JSON `version = 2`

### 变更

- ⚠️ **升级到本版本会自动重置学习记录**（题目 id 变化，旧记录必然失配，属预期行为）

### 工程

- `.gitignore` 补 `app/build/`，清理误跟踪的构建产物
- CI `build.yml` 版本号同步至 2.2.0 / v2.2.0

## [2.1.0] - 2026-09-03

### 修复

- **根治首次启动 SIGSEGV 闪退**（根因定位：官方 issue #54）——`layerBackdrop` 记录层内部不得存在采样同一 backdrop 的 `drawBackdrop` 节点（循环引用 → RenderThread native 崩溃，Java 层无法捕获）。v2.0 将 `NavHost` 内容层整体 `layerBackdrop` 且页面内玻璃组件全部采样，违反该硬约束
- 架构按官方语义重构（非降级）：
  - 新增 `Modifier.glassMaterial()`：iOS regular material 观感（半透明渐变表面 + 顶部高光描边 + 轻阴影），无采样循环风险
  - `GlassCard` / `GlassButton` / `GlassSlider` / `GlassToggle` 增加 `refracts` 参数：默认 `false`（内容流 = 材质）；`true` 且 `GlassRuntime.enabled` 时才走真折射（仅限记录层外）
  - `GlassBottomTabs`（记录层外的浮动元素）保持真折射玻璃不变
- **模考时长滑杆整体偏下**（`GlassSlider` 重写）：轨道与滑块置于同一 24dp 居中容器
- `GlassButton` 材质版增加按压缩放 + 高光描边；开关轨道样式重做

## [2.0.2] - 2026-09-03

### 新增

- **BootGuard 启动守护**：面包屑日志（`boot_log.txt`，150 行）+ 启动心跳（`boot_state.txt`，首帧渲染稳定 700ms 后 `markHealthy`，`onStop` 兜底）+ 连续异常死亡计数；兼容 native 崩溃 / 系统杀进程，无需 Java 堆栈即可定位死亡时点
- **自愈机制**：上次启动未标记健康即死 → 自动安全模式（`GlassRuntime.enabled = false`，全部玻璃组件退化为纯 Compose 渲染）；连续 2 次 → 启动弹出诊断屏（设备信息 + 版本 + 面包屑 + 一键复制）
- 设置页新增「画面特效」开关（DataStore `glass_effects` 持久化）；`MainActivity` 增加 `SafeModeBanner`

### 修复

- `CrashGuard` 升级：崩溃时同步写面包屑；报告屏合并启动轨迹

### 变更

- **固定签名**：生成 `dronequiz.keystore`（RSA 2048 / 30 年 / alias `dronequiz`），base64 加密上传 GitHub Secrets（`DQ_KEYSTORE_B64` / `DQ_KS_STORE_PASS`）；`build.gradle.kts` debug / release 统一读环境变量 `DQ_KS_PATH` / `DQ_KS_STORE_PASS`，本地与 CI 共用同一签名——**此后版本可覆盖安装，无需卸载旧版**

## [2.0.1] - 2026-09-03

### 修复

- 首帧绘制期 AGSL `RuntimeShader` 构造失败导致闪退的风险（部分 GPU 驱动对着色器语法兼容性差）：`lens()` / `HighlightStyle` / `InteractiveHighlight` 三处着色器构造全部加 `runCatching` 降级——失败时保留纯模糊 / 普通描边高光，不崩溃
- 数据库文件名改为 `drone_quiz_v2.db`，规避旧版残留库的 schema 校验冲突

### 新增

- `CrashGuard` 全局未捕获异常处理器：堆栈写入 `last_crash.txt`，下次启动在「启动报告」屏完整展示（可复制）
- `MainActivity` 增加可见的「正在加载题库…」启动画面（此前 ready 前为纯黑屏）

## [2.0.0] - 2026-09-03

首个正式版本，吸收两轮内测反馈后全量重建。

### 新增

- **数据层**：Room 七表（questions / practice_records / question_stats / exam_records / exam_answers / wrongbook / streak_log）+ DataStore 设置 + Repo
- **真液态玻璃 UI**：Kyant0 backdrop 库源码 vendor 进工程（Apache-2.0）；`GlassKit`（`glass()` 修饰符 = vibrancy + blur + AGSL lens 折射/色散）、`GlassButton`、`GlassSlider`（官方 DampedDrag 移植）、`GlassToggle`、`GlassCard`、`GlassBottomTabs`（官方移植，支持拖动切换）
- **五大页面**：首页（进度环 / 预估通过率 / 7 日柱状图 / 正确率折线 / 打卡 / 错题提示 / 上次模考）、刷题（筛选 / Pager / 点选即判 / 题号面板）、模考（全参数可配置 / 倒计时自动交卷）、错题本（连对移除 / 特训）、设置（主题 / 字号 / 提醒 / SAF 题库导入 / 清空记录）
- **动画系统**：NavHost 统一转场（淡入 + 轻缩放 280ms）、BounceState 过冲回弹、按压缩放、答错抖动、解析 spring 展开、倒计时红色脉冲
- **配色**：奶油底 + 墨黑主操作 + 橙色点缀，暖夜深色主题；底栏纯图标化
- **内置题库**：`gen_bank.py` 生成 801 题（单选 637 / 判断 164，11 分类，含解析）
- **CI/CD**：GitHub Actions `build.yml` 自动构建并发布 Release

### 修复（相对内测版）

- 模考交卷崩溃（`SQLiteConstraintException`）：`WrongBookEntity.addedAt` 改为非空默认 0，所有插入路径显式赋值时间戳；`fallbackToDestructiveMigration` 防旧库迁移崩溃
- 转场动画重叠、滑杆不可拖、列表末项被底栏遮挡（contentPadding 130dp）等十余项体验问题
- 首轮 GitHub Actions 31 个编译错误全部修复（context-parameters 编译器参数、`collectAsState` initial、`awaitFrame` → `withFrameNanos`、Lens 解耦 shapes 等）

[Unreleased]: https://github.com/Everett406/DroneQuiz/compare/v2.2.0...HEAD
[2.2.0]: https://github.com/Everett406/DroneQuiz/compare/v2.1.0...v2.2.0
[2.1.0]: https://github.com/Everett406/DroneQuiz/compare/v2.0.2...v2.1.0
[2.0.2]: https://github.com/Everett406/DroneQuiz/compare/v2.0.1...v2.0.2
[2.0.1]: https://github.com/Everett406/DroneQuiz/compare/v2.0.0...v2.0.1
[2.0.0]: https://github.com/Everett406/DroneQuiz/releases/tag/v2.0
