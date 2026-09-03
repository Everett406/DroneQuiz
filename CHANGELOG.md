# 更新日志 (Changelog)

本文件记录 DroneQuiz 每个版本的变更明细。格式参考 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，版本号遵循语义化版本（MAJOR.MINOR.PATCH）。

## [Unreleased]

## [2.6.1] - 2026-09-03

### 修复 —— 滚动惯性真根因（用户第六轮实测反馈）

- **"上下滑动没有惯性，一松手它就停了"（连续三轮未修透的真根因）**：经 GitHub/官方文档调研确认，`NestedScrollConnection.onPreFling` 的返回值语义是**本层消费的速度**，未消费的部分才交还子列表继续 fling（sinasamaki 原文未过冲时 `return super.onPreFling(available)` = 消费 0）。此前 v3/v4 把"要交还的速度"当返回值写成了 `return available`——语义完全颠倒，等于把普通 fling 的全部速度一口吞掉：列表收到 0 速度，松手即停。v5 将三处返回值全部修正：未过冲 → 消费 0（普通甩动零干扰、惯性完整保留）；过冲 + 强甩向内容侧 → 消费 0（速度全部交还，惯性立即生效），过冲量异步摩擦衰减并行归零；甩不回/往外甩 → 消费全部垂直速度，交给异步软弹簧回弹
- **fling 途中撞边的过冲量挂住不回弹**：新增 `onPostFling` 兜底——fling 自然结束后，剩余过冲量带残余速度弹簧回弹（onPreFling 已启动的动画不打断）
- **柔化遮挡内容（主页进度环被渐变裁切）**：`softTopFade`/`softVerticalEdges` 增加 0..1 强度参数，绑定列表/网格的 `canScrollBackward`/`canScrollForward`（180ms 渐变过渡）——停在顶部/底部时完全无柔化，首屏内容（进度环、筛选行、首条结果）不再被渐变遮住；开始滚动后柔化淡入，停在中间时保持。覆盖：四个 Tab 页固定标题、搜索结果列表、模考/刷题答题卡题号网格
- **主页连击卡与今日卡高度不齐**：双卡 Row 改 `IntrinsicSize.Min` + 两卡 `fillMaxHeight`，等高对齐
- **自定义壁纸下文字看不清**：壁纸层叠加主题纱（亮色叠米白纱、深色叠墨色纱，开模糊时更透），任意壁纸/主题组合下文字对比度恒定可读

### 变更

- **每日提醒智能化**：不再固定每天 20:00——自动学习用户习惯，取最近 10 天每天首次刷题时刻的中位数（夹在 10:00–21:30，无数据默认 19:30）作为提醒时刻；**当天已刷过题则保持安静**；调度改为 OneTime + Worker 自续（每天动态重算时刻）；去掉"首次开启需通知权限""未授予通知权限…"等说教文案，拒绝权限时静默不置位
- **底栏"刷题"图标重绘**：去掉旧版怪异的后卡帽（视觉上像歪掉的盖子），改为与错题本"方块+X"成对的"方块+对勾"填充式图标，Tab 栏视觉语言统一

## [2.6.0] - 2026-09-03

### 修复 —— 惯性丢失 + 刷题记录（用户第五轮实测反馈）

- **上下滚动"没有惯性，停止滑动它就停"**：根因是过冲量存放在 `Animatable` 里，拖动/惯性每帧经 `scope.launch { snapTo }` 异步写入——fling 结束的 `onPostFling` 与排队中的写入存在时序竞态：读到旧值 0 就跳过回弹、随后写入又把过冲量置回非零 → **过冲量残留**；此后每次松手 `onPreFling` 都误入过冲分支并**同步挂起数百毫秒**（软弹簧动画），fling 被推迟到回弹结束才开始，体感即"没惯性"。v4 重构：过冲量改为 `mutableFloatStateOf` **同步直写**（零协程零竞态），`onPreFling` 内**零挂起点**立即返回——普通滚动惯性完全不受影响；过冲时向内容侧强甩则全部速度立刻交还列表（惯性立即生效），过冲量异步摩擦衰减并行归零
- **刷过的题没记录**：普通刷题再次开始时自动接续上次会话——按筛选参数匹配快照，恢复题目顺序/已答对错标记/进度页码（换筛选条件=开新会话）；配合既有的逐题落盘，"今日已刷 N 题"与实际作答完全对得上

### 新增

- **题目搜索**：刷题 Tab 顶部新增搜索入口（题干/选项/解析全文 LIKE，防抖 300ms，上限 80 条）；结果点开可看全部选项、正确答案与解析
- **全局壁纸**：设置-外观可导入图片作全局背景"纹路"（复制到私有目录持久化），可开启**壁纸模糊**、可一键恢复默认；壁纸经背景记录层进入所有玻璃组件的折射
- **底栏玻璃模糊度三档**：低（4dp）/中（8dp）/高（14dp），设置-外观可调
- **错题本筛选 + 滚动把手**：顶部固定筛选行（全部/单选/判断/动态分类 chips）；右侧快速滚动把手（按比例映射列表位置，题目少于 8 条自动隐藏）

### 优化 —— 体验细化

- **答题卡/题目面板**：移除深色 scrim（内容层真模糊已足够层次，用户反馈"没必要加深色遮罩"）；顶部新增小把手，**拖动下滑可关闭**（超阈值松手关闭、否则弹性回位）；题号网格**上下边缘柔化渐隐**（DstIn alpha 蒙版），替代硬切行
- **标题固定 + 柔化**：首页/刷题/模考/设置四页标题固定顶部不随滚动（对齐错题本），滚动内容在标题下缘 alpha 渐隐（不依赖背景色，壁纸模式下同样干净）
- **图标填充式**：Tab 栏五图标与答题卡/垃圾桶/通知铃/火焰/导入/漏斗/搜索/播放/对勾/关闭等全部重绘为填充路径（此前为描边线条式）
- **深色模式开关对比度**：开启态轨道为奶色时钮身同步转深（材质/玻璃两种模式），不再白底白钮难以辨认
- **每日打卡活力化**：动态激励文案（按连击阶段变化）+ 里程碑进度条（3/7/14/21/30/50/100 天，显示"再练 X 天达成 N 天连击"）
- **预估通过率参考及格分**：公式改为 `正确率相对及格线达标度×0.7 + 覆盖率×0.3`，调整设置中的及格分立即重算（此前固定阈值无反馈）

## [2.5.1] - 2026-09-03

### 修复 —— 手感与视觉伪影专项（用户第四轮实测反馈）

- **上下滚动"跟手不顺畅"（回弹 v3，按社区标准实现重写）**：调研 sinasamaki《Overscroll animations in Jetpack Compose》标准方案后定位三处根因——① 显示曲线起手斜率仅 0.55（一起手就钝 45%），换 **CubicBezier(0.5, 0.5, 1.0, 0.25)**：起手斜率恰为 1:1 绝对跟手、渐进变韧；② 过冲渐近线仅 110dp，改为**容器高度**（iOS 同款用 dimension）；③ 回弹把松手速度直接当弹簧初速度且无钳制，会甩过头荡到另一侧。甩动衔接改为 **exponentialDecay 预测**：甩不回内容侧→软弹簧（StiffnessLow）带速度回弹；会冲过零→过冲量摩擦滑行、过零瞬间 snapTo(0)、**剩余速度原样交还列表**（iOS 式无缝衔接，不再吞甩动）
- **底栏"方框/圆环"伪影**：选中块按压鼓包 `pressedScale 78/56≈1.39`，放大后 56→78dp，超出 64dp 栏体上下各 7dp，透镜色散在栏外画出一圈带彩虹边的圆环（截图可见）；收敛为 **62/56** 并将选中块透镜 10/14dp 降至 8/11dp。同时移除 v2.5.0 加在底栏上的 `Modifier.blur`——方形模糊层在胶囊四周留下方框裁剪痕迹（底栏玻璃折射的本来就是已模糊的内容层，无需再套）
- **左右切题"上下被切一刀"+阴影感**：v2.5.0 的 3D 转盘（rotationY+透视相机+alpha 渐隐）会让卡片上下角超出 Pager 视口被裁剪出水平切线，且 3D 变换与玻璃 backdrop 采样叠加产生阴影状伪影；移除旋转与渐隐，仅保留轻微纵深缩放（离场页 1→0.97），干净无伪影

### 变更

- **应用图标更换**：采用用户设计图（米色底 + 白色圆角卡 + 黑色对勾 + 橙色线条）——自适应图标前景改为按内容包围盒裁剪的位图（mdpi~xxxhdpi 五档，内容收在 66% 安全区），背景色同步取样为 `#F3EFE3`，移除旧无人机矢量前景

## [2.5.0] - 2026-09-03

### 修复 —— 弹窗被自身模糊 + 界面整理（用户第三轮实测反馈）

- **答题卡/弹窗展开时面板自身也被模糊**：v2.4.0 的 `OverlayBlur` 对整个内容层施加模糊，而弹窗面板就渲染在内容层内，导致"答题卡打开后答题卡自己也是糊的"。现引入**弹窗传送门（GlassOverlayPortal）**：面板统一由 AppRoot 在内容模糊区与底栏之上渲染，永远清晰；多槽位列表支持同屏多个弹窗互不覆盖（如模考页确认框 + 答题卡）
- **弹窗折射升级**：面板位于内容记录层之外后，可安全折射 `combined(背景层, 内容层)`——打开弹窗时折射的正是"被模糊的内容"，更接近 iOS 液态玻璃；此前因循环采样风险只能折射背景渐变层
- **底栏模糊一致性**：弹窗打开时底栏同入模糊区（iOS 同款：面板之外一切虚化），不再出现"内容糊了、底栏清晰"的割裂感
- **首页卡片贴叠**：错题本/上次模考两张信息卡此前无纵向间距（LazyColumn 相邻 item 直接相接），液态玻璃透镜边缘让两张卡看起来融在一起；现统一间距（12dp）与圆角规格（22dp），样式一致
- **错题本类目标签标红**："无人机装调"等类目标签此前用错误红色，视觉上像出错强调；改回中性 `textSub` 低调节，红色只留给错误语义（"错 N 次"等）
- **错题本右上角重复入口**：移除纯图标"开始特训"按钮（与列表顶部"图案+字"按钮重复），只保留一个入口

### 变更 —— 全局手感升级

- **上下过冲回弹重写（BounceState）**：修复此前实现"不够丝滑"的三处根因——① 90ms 定时器兜底会在按住不动/慢速拖动时与手势抢状态造成抖动（最大元凶），改为 `onPreFling`（拖动松手）+ `onPostFling`（fling 惯性撞边结束）双完备触发点；② 位移改用 iOS UIScrollView 同款 rubber-band 曲线 `d·(1−1/(x·c/d+1))`，虚拟位移与显示位移分离，反向拖动严格沿原曲线返回；③ 回弹弹簧改临界阻尼（`dampingRatio=1f`），无过零震荡，松手带初速度顺势回弹——"豆腐般"软糯
- **设置页补齐回弹**：设置页此前是硬边界（无过冲动画），现包 `BounceContainer` 与其他页面一致
- **刷题左右切题转盘效果**：题目 Pager 页面加 `graphicsLayer` 变换——`rotationY`（绕竖轴随滑动偏转 20°）+ 透视相机（`cameraDistance`）+ 弧面压缩（邻页向圆心轻收）+ 轻微缩放淡出，切换时产生"转盘半径"纵深感
- **模考配置页紧凑化**：题目数量/判断题占比/考试时长/及格分四张全宽大卡占满整屏，孰轻孰重失衡；压缩为 **2×2 紧凑设置格**（标签+当前值+迷你滑杆），"开始考试"与"最近模考"首屏即可达
- **移除刷题页"继续上次"卡片**：与错题本练习入口重复且易混淆（会同步显示错题特训会话）；刷题进度快照仍在后台静默保存，后续需要时可低成本恢复 UI

## [2.4.0] - 2026-09-03

### 修复 —— 底栏导航根因修复 + 刷题交互重构（用户实测反馈）

- **底栏拖拽松手不切页（根因级修复）**：`DampedDragAnimation` 被 `remember` 缓存，其 `onDragStopped` 闭包捕获了首次组合时的 `onTabSelected` → 旧 `navigateTab` → 旧 `tabIndex`。从首页出发后闭包内 tabIndex 恒为 0，拖回首页松手时 `if (index == tabIndex) return` 误判吞掉切换——表现为"高亮块回去了、页面没回去"、从设置页点不回首页。现改用 `rememberUpdatedState` 让拖拽回调始终拿最新引用，`onDragStopped` 直接同步回调切页（不再依赖 `snapshotFlow`/动画链），`currentIndex` 快照流整体删除，页面变化仅驱动高亮块动画
- **首页右上角进设置与底栏行为统一**：`onSettings` 改走 `navigateTab(4)`（保存页面状态、栈结构可预测），彻底消除"页面叠加"体感
- **刷题页被底栏遮挡**：刷题 Tab 重构为**配置入口页**（参考模考配置页布局：题型范围/分类/顺序卡片 + 开始按钮 + 学习概览统计），点"开始刷题"进入**全屏刷题页**（非 tab destination，无底栏遮挡滑杆与左右切题按钮）；错题特训同步走全屏模式
- **普通刷题记录丢失（双根因）**：① 作答记录挂页面级 `rememberCoroutineScope`，答完立刻退出时协程被取消——改挂应用级 `ServiceLocator.appScope`；② 答题卡进度只存内存，页面销毁即丢——新增 `PracticeSession` 快照（DataStore 持久化题目顺序/已答/页码），答题与翻页实时落盘，配置页"继续上次刷题"一键恢复；题库升级与"清空记录"时同步作废
- **模考"进行中"幽灵记录**：放弃考试此前只 `popBackStack`，DB 残留 `score=null` 记录且点击无响应、无法删除。现在：放弃即删（`Repo.abandonExam` 事务删除记录与作答）；未完成模考支持**继续考试**（内存会话优先，进程重启后从 DB 重建题目/已答/剩余时间）与**删除**（垃圾桶按钮）；"清空做题记录"补充清空 `exam_records`/`exam_answers`
- **模考/刷题题目卡片内容溢出**：题目卡内部增加 `verticalScroll`，长题目与解析展开后可上下滚动查看，不再被裁切在屏幕外
- **弹窗背景"黑遮罩"体感**：`GlassOverlay` 打开时通过 `OverlayBlur` 全局状态对内容层施加真实模糊（Android 12+ RenderEffect，低版本自动降级为原效果），scrim 由 28%/45% 减淡至 10%/22% 仅作层次；面板 `surfaceColor` 透明度 0.9→0.62，折射质感可见

### 变更

- 新增路由 `practiceRun?src={src}&type={type}&cat={cat}&resume={resume}`（全屏刷题）；`practice` tab route 仅承载配置页
- `ServiceLocator` 新增 `appScope`（应用级协程域，落盘操作不随页面销毁取消）
- `Repo` 新增 `abandonExam` / `resumeExam` / `loadPracticeByIds`；`ExamDao` 新增按 id 删除
- `SettingsStore` 新增 `practiceSession`（kotlinx.serialization JSON 持久化）

## [2.3.2] - 2026-09-03

### 修复 —— 交互与功能批量修复（用户实测反馈）

- **底栏无法单击切换**：`TabIconSlot` 缺少点击处理（官方 `LiquidBottomTab` 有 `clickable(Role.Tab)`）。现单击任意 tab 图标直接跳页；隐藏染色层通过 `LocalTabClickEnabled` 禁点避免双触发
- **拖拽切换概率失效**：`onTabSelected` 原排在 `animateToValue` 完成之后，且 `collectLatest` 会在连续操作时取消前序动画协程 → 回调丢失（表现为"滑完了页面没切"、切不回主页）。现回调提前到动画之前，动画只承担视觉
- **"一直加载题库"加固**：刷题加载增加 10s 超时 + 异常捕获 + 失败重试按钮；题数 0→N（后台导入完成）自动重载；启动门控最多等 8s 即放行主界面，导入转入后台继续并自行持久化版本
- **滑杆只能点不能拖**：原拖拽手势不消费指针事件，配置页/设置页内会被父级 `verticalScroll` 抢走；进度类滑杆（模考/刷题）拖动时外部值回写与同步循环互相拉扯（橡皮筋）。现重写为轨道区统一手势（按下跳转、按住拖动实时跟随、事件全消费）、拖拽中暂停同步循环、触控区 24dp→40dp
- **模考题目垂直居中**：`HorizontalPager` 默认 `CenterVertically`，改为 `Alignment.Top` 顶部对齐（刷题页同步受益）
- **模考底部滑杆贴手势条**：底部操作条补 `navigationBarsPadding`，滑杆与左右按钮间距 8dp→14dp（刷题页同步处理）
- **模考新增答题卡面板**：顶栏网格按钮打开，玻璃化 `GlassBottomSheet`（与刷题页题号面板同款），已答/未答区分、点题号直接跳题
- **每日打卡通知无权限逻辑**：Android 13+ 开启开关时运行时请求 `POST_NOTIFICATIONS`；授予后排程提醒，拒绝时给出明确提示；副标题显示开启/权限状态
- 底栏 `selectedTabIndex` lambda 改用 `rememberUpdatedState`，避免重组导致内部状态重置

## [2.3.1] - 2026-09-03

### 修复 —— v2.3.0 启动即闪退（热修复）

- **崩溃根因**：v2.3.0 将 shapes 从 maven 依赖改为源码 vendor 时，`com.kyant.backdrop.effects.Lens` 中的 `cornerRadii` 分支被一并裁掉了 `RoundedRectangularShape`（Kyant Shapes）分支；而底栏、按钮、滑杆、开关全部使用官方 `Capsule()` 胶囊（实现 `RoundedRectangularShape`）→ 首帧组合即抛 `UnsupportedOperationException: Only RoundedRectangularShape or CornerBasedShape is supported in lens effects.`，应用启动即异常退出
- **修复**：补回 `RoundedRectangularShape` 分支（`shape.corners(size, layoutDirection, density)` 取四角半径，与上游 AndroidLiquidGlass 完全一致），Capsule 连续曲率胶囊的折射效果恢复正常
- **加固**：`lens()` 遇到真正不支持的形状时由硬抛异常改为**优雅降级**——跳过折射、保留已有模糊，任何形状都不会再导致崩溃；与既有的 AGSL 着色器编译失败保底（runCatching）形成双保险

## [2.3.0] - 2026-09-03

### 重构 —— 液态玻璃全面对齐官方（Kyant0/AndroidLiquidGlass）

- **架构修复（核心）**：`AppRoot` 由"单记录层包内容"改为**双记录层**——`bgBackdrop` 只记录背景渐变（官方 demo 的"壁纸"角色），`contentBackdrop` 记录内容。此前玻璃折射采样的是大面透明像素（背景在记录层之外），导致"看不出折射"；现在：
  - 内容流卡片/按钮/滑杆/开关全部升级为**真折射玻璃**（官方 `LazyScrollContainerContent` 同款模式：元素不在背景记录层内，零循环采样、零 SIGSEGV 风险）
  - 底栏折射 `bgBackdrop + contentBackdrop` 组合层，滚动内容从底栏下穿过时透过玻璃可见
- **Capsule 连续曲率胶囊**：官方 shapes（Kyant0/Shapes，Apache-2.0）源码 vendor 到 `com.kyant.shapes`（maven 产物要求 compileSdk 37 + AGP 9.1 并连带升级 Compose，暂不跟进；源码仅依赖 compose-ui 可直接内联），底栏三层结构、按钮、滑杆轨道/滑块、开关轨道/圆钮全部替换为官方同款 `Capsule()`，折射边缘更柔润
- **开关重做**：`GlassToggle` 按官方 `LiquidToggle` 逐行对齐——玻璃圆钮可拖拽、按压膨胀、轨道颜色随状态渐变；明确不可加外层 clickable（`inspectDragGestures` 不消费事件，避免双触发）
- **滑杆对齐官方 `LiquidSlider`**：滑块折射"背景+轨道放大"（`rememberBackdrop` 自定义绘制）、按压色差镜片、点击热区由 6dp 轨道扩大到 24dp 容器
- **弹窗玻璃化**：题号面板（原 `ModalBottomSheet`）与全部确认对话框（原 `AlertDialog`）替换为同窗口自绘玻璃组件 `GlassBottomSheet` / `GlassConfirmDialog`——独立窗口无法采样主窗口 backdrop，故必须同窗绘制；遮罩点按/返回键关闭，面板内点按消费防误关
- 安全模式降级链路保留：特效关闭或异常退出自动降级时，所有玻璃组件退回质感材质（无 RenderEffect）

### 修复

- **"一直加载中"**（`Repo.loadWrongPractice` / `loadPractice`）：Room 对空列表 `IN ()` 生成非法 SQL，错题本为空时进"错题特训"、筛选无结果时协程静默死亡，页面永远"正在加载题库…"。现已空列表短路防御（`startExam` 空库同样防御）
- **模考统计污染**（`Repo.submitExam`）：`bumpStreak(correctIds.size >= 0)` 恒为 true——全错也计"正确+1"且 50 题模考只计 1 次答题。改为 `bumpStreakBulk(答题数, 正确数)` 按实际累计
- **题库导入死锁**（`Repo.ensureBankLoaded`）：此前导入失败仍返回版本号并被持久化，空库状态下永不重试。现在解析/校验先行（失败不碰数据库），"清学习数据+换题库"并入单事务，只有完全成功才返回版本号，失败下次启动自动重试

### 变更

- `GlassToggle` 签名对齐官方：`checked: () -> Boolean`（避免 remember 闭包捕获过期状态）
- `GlassCard` / `GlassButton` / `GlassIconButton` 默认 `refracts = true`

### 工程

- vendor 官方 Kyant0/Shapes 源码（Apache-2.0，NOTICE 已署名）
- CI `build.yml` 版本号同步至 2.3.0 / v2.3.0

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
