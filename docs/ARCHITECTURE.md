# 架构

## 技术栈

- Kotlin 2.1.20 + Jetpack Compose + Material 3
- Room 2.7.1 持久化，DataStore 保存少量偏好
- Navigation Compose 管理页面，Coroutines + Flow 驱动状态
- MVVM + Repository；依赖由 `AppContainer` 手动创建，不引入 DI 框架
- minSdk 26 / targetSdk 35；Gradle 8.14.1 + AGP 8.9.2 + Version Catalog
- 纯 JVM 单元测试（JUnit 4），无 instrumentation 测试

## 目录结构

```
app/src/main/java/com/ltq/count/
├── CountApplication.kt          应用入口 + AppContainer（数据库/仓库/偏好）
├── MainActivity.kt              单 Activity、边到边、音量键拦截、快捷方式深链
├── data/
│   ├── db/                      Room 实体、DAO、数据库、Entity↔Domain 映射
│   ├── prefs/SettingsRepository.kt  DataStore：主题模式、卡片大小
│   └── repo/
│       ├── CounterRepository.kt  所有聚合/写入规则（事务内完成校验与聚合）
│       └── BackupRepository.kt   SAF 读写 + 导入计划应用
├── domain/
│   ├── model/Models.kt          Counter / CountEvent / Tag / CounterSummary
│   ├── CounterMath.kt           纯算术：当前值、重置 delta、反向 delta
│   ├── CounterRules.kt          校验、AdjustOutcome、UndoOutcome
│   ├── EventFilter.kt           时间范围/正负/备注筛选
│   ├── heatmap/HeatmapCalculator.kt  本地时区分桶 + 三种指标 + 等级
│   └── backup/                  JSON 模型、编解码、合并/替换计划
├── shortcut/ShortcutHelper.kt   桌面快捷方式（ShortcutManagerCompat）
└── ui/
    ├── theme/                   颜色、字体、形状、主题、减少动态效果
    ├── components/              软卡片、Pill、数值动画、标签编辑器、弹窗
    ├── home/                    主页 + ViewModel + 拖拽排序
    ├── edit/                    新建/编辑计数器
    ├── detail/                  详情、事件列表、热力图
    ├── quick/                   全屏快速计数 + 音量键桥接
    ├── settings/                设置、导入导出、负值冲突对话框
    └── navigation/              路由与 NavHost

app/src/main/res/                中文字符串（默认）+ values-en 英文 + 主题 XML + 图标
app/src/test/java/com/ltq/count/  纯 JVM 单元测试
```

## 架构与数据流

```
Compose Screen ──collectAsStateWithLifecycle──> ViewModel
      │                                           │
      │ 用户事件                                   │ suspend 调用 / Flow
      ▼                                           ▼
CounterRepository ──withTransaction──> Room (SQLite)
      │                                   │
      └────── Flow<List<CounterSummary>> ─┘

SettingsRepository (DataStore) ──> 主题模式 / 卡片大小
BackupRepository (SAF) + ImportPlanner ──> JSON 导入/导出
```

- `AppContainer` 在 `CountApplication` 中创建一次，屏幕通过 `ViewModelProvider.Factory` 取得依赖；没有反射注入。
- 所有改变数值的操作都在 `CounterRepository` 的 Room 事务内完成「读取聚合 → 校验 → 追加事件」，避免并发快速点击读到旧值。
- Home 的聚合查询用 `LEFT JOIN + COALESCE(SUM(delta), 0)`，不缓存 `currentValue`；`events(counterId, timestamp)` 上有索引。
- 时间统一以 UTC `Instant` 持久化（Room 中为 epoch millis），只在展示与热力图分桶时转换到设备本地时区。
- 导入分为纯函数 `ImportPlanner`（计算合并/替换计划）和 `CounterRepository.applyImport`（单事务执行），因此所有导入决策都可以脱离 Android 测试。

## 数据模型

| 表 | 字段 |
| --- | --- |
| `counters` | id(UUID), name, initialValue(Int), allowNegative, createdAt, sortOrder, pinned, defaultStep |
| `events` | id(UUID), counterId(FK CASCADE), delta(Int, 非 0), timestamp(UTC millis), note, source, sessionId |
| `tags` | id(UUID), name, createdAt |
| `counter_tags` | counterId + tagId 复合主键；同一标签不会重复关联，数量不限 |

`source` 取值：`MANUAL` / `WIDGET` / `SHORTCUT` / `RESET` / `UNDO`。`WIDGET` 目前预留给未来的桌面小组件。

### 不变量

1. 当前值永远由 `initialValue + Σdelta` 计算，不落库。
2. 修改初始值只改变基线，历史事件不被读取/修改以外的任何操作触碰。
3. 「重置到初始值」= 追加 `delta = initialValue - currentValue`、`source = RESET` 的事件；已在基线时为空操作并提示。
4. 撤销 = 追加与原事件相反 `delta`、`source = UNDO` 的事件；不删除、不修改历史。范围撤销按时间倒序逐条追加反向事件，最终回到目标事件之前的状态。
5. `allowNegative = false` 时，任何会让运行值 < 0 的追加都会被拒绝（手动、快捷、重置、撤销、导入统一走同一校验）。
6. 事件 delta 绝对值和初始值都限制在 `1_000_000_000` 以内，避免溢出与无意义数据；聚合内部使用 `Long`。
7. 每个计数器的标签数量不设上限；标签只用于主页筛选，不做层级。

## 并发与事务

- 快速连点、音量键和全屏按钮共用同一套 Repository 写入路径，事务内读取最新聚合值，因此不会出现「读到旧值再覆盖」。
- 撤销只针对目标事件及其后的连续事件；若目标不是最新事件，按 `(timestamp, id)` 全序判断并拒绝不连续的范围。
- Home 的排序更新在一个事务里重写受影响计数器的 `sortOrder`；筛选状态下禁用拖拽，避免只对可见子集重新编号。
- 删除计数器级联删除事件；删除标签只删除关联，计数器与事件不受影响。

## 时间与时区

- 事件时间存 UTC；所有「今天」「本周」「最近 24 小时」都按设备当前时区解释。
- 热力图按 `Instant.atZone(zone).toLocalDateTime()` 分桶：分/时是锚定当前时刻的滚动窗口，日/周/月按日历分桶。
- 跨时区旅行时，历史事件的本地日期会随设备时区变化，这是「按本地时间展示」的预期行为；原始 UTC 时间不变。

## 热力图

- 频率基准：`次数`（事件数）、`增量绝对值之和`、`净增量`。
- 时间粒度：分 / 时 / 日 / 周 / 月，默认「日」。
- 分 = 最近 60 分钟，时 = 最近 24 小时，都是锚定当前时刻的滚动窗口，整分/整点截断；日/周/月按日历分桶，周从周一开始，月从 1 号开始。
- 日粒度渲染滚动 52 周（GitHub 风格）小方块：周一对齐、月份标签、工作日标签；首屏自动滚到最右（最近）。
- 周 = 最近 26 周，月 = 最近 12 个月，使用柱状图；分/时同样使用柱状图（60 / 24 根），分档自动收窄柱间距。
- 颜色：单一低饱和灰绿主色，按当前可见区间的最大值映射 4 级明度；0 值接近表面色。净增量为负时使用独立的低饱和陶土暖色，不使用同色更深。
- 动效：格子按索引错峰淡入；系统「移除动画」开启时直接呈现静态结果。
- `HeatmapData` 的 key 统一为 `LocalDateTime`；日历粒度固定落在 00:00，分/时保留到分钟/整点。

## 构建

构建命令、环境要求和安装步骤见根目录 [README](../README.md)。测试说明见 [TESTING](TESTING.md)。
