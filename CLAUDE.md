# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## 项目概述

NBA Live Score — IntelliJ IDEA 插件（id: `com.caro.nbascore`），在右侧工具窗口中查看 NBA 实时比分、排名、季后赛对阵图，并内置一个调用 OpenAI 兼容接口的 AI 问答助手。

- Kotlin 1.9.22 + IntelliJ Platform Gradle Plugin 2.1.0，基于 IC 2024.3 编译，JDK 17 toolchain，Gradle 8.5 wrapper
- UI 全部为 Swing（非 Kotlin UI DSL），季后赛对阵图为 `paintComponent` 手绘 2D 图形
- 代码注释、UI 文案、commit message 均为中文；commit 遵循 conventional commits + 中文描述（如 `refactor(nba): ...`、`fix(playoffs): ...`）

## 常用命令

```bash
./gradlew buildPlugin    # 构建插件 zip → build/distributions/nba-score-plugin-<version>.zip
./gradlew runIde         # 启动沙箱 IDEA 实例调试插件（已配置 -Xmx2G）
./gradlew verifyPlugin   # 运行插件兼容性验证
```

- 无测试源码（无 `src/test`），没有 lint 配置；CI workflow（`.github/workflows/build-and-publish.yml`）整体被注释掉，未生效
- ⚠️ 当前 `buildPlugin` 会**编译失败**，原因见下方「已知问题」

## 版本发布约定

发布新版本时需同步修改三处（缺一不可）：

1. `build.gradle.kts` 中的 `version = "..."`（项目版本）
2. `build.gradle.kts` 中 `intellijPlatform.pluginConfiguration.version`（插件版本）
3. `src/main/resources/META-INF/plugin.xml` 的 `<change-notes>` 顶部新增对应版本条目

兼容性范围：`sinceBuild = "242"`（IDEA 2024.2），`untilBuild` 无上限。

## 架构

### 入口与标签页结构

`plugin.xml` 注册三样东西：右侧工具窗口 "NBA Score"（`NBAScoreToolWindowFactory`）、主工具栏刷新按钮（`RefreshAction`）、应用级设置页（"NBA AI 助手"，`NBASettingsState` / `NBASettingsConfigurable`）。

`NBAScoreToolWindowFactory.kt` 中的 `NBAScoreMainPanel` 是 4 个标签页的容器（JBTabbedPane），**懒加载**：先放空的 `JPanel()` 占位，首次点击某标签才实例化真实面板（`initializeTab`），并记录在 `initializedTabs` 中。新增标签页时注意维护这个索引约定（当前：0=比分、1=排名、2=季后赛、3=AI助手；排名面板通过 `onPlayoffClick` 回调切换到索引 2）。

### 面板 + 服务的固定模式

每个标签页都是「一个 JPanel + 一个纯 Kotlin 服务类」的组合，服务类直接 new（非 IntelliJ Service 注入）：

| 面板 | 服务 | 数据源 |
|---|---|---|
| `NBAScorePanel` | `service.NBADataService` | ESPN scoreboard API |
| `StandingsPanel` | `service.StandingsService` | ESPN standings API |
| `PlayoffBracketPanel` | `service.PlayoffService`（**缺失，见已知问题**） | 第三方对阵图 API |
| `GameDetailDialog` / `PlayByPlayPanel` | `service.GameDetailService` | ESPN summary API（按 gameId） |
| `AIAssistantPanel` | 无独立服务，直接在面板内调 OkHttp | 用户配置的 OpenAI 兼容接口（默认智谱 GLM-4.7-Flash） |

**线程模型（所有面板统一遵循）**：每个面板持有 `CoroutineScope(Dispatchers.Default + SupervisorJob())`，在协程里执行**同步** OkHttp `execute()` 网络请求，结果通过 `ApplicationManager.getApplication().invokeLater` 或 `SwingUtilities.invokeLater` 回到 EDT 更新 UI；`dispose()` 取消 scope。不要在 EDT 上发起网络请求，也不要在后台线程直接碰 Swing 组件。

**JSON 解析**：统一用 Gson 的 `JsonObject` 手工逐字段解析到 `model/` 下的 data class（非反射映射），ESPN 字段缺失的容错写法（`?.get(...)?.asString ?: 默认值`）是既有风格。

### 时区约定

ESPN API 按**美国东部日期**查询。`NBADataService.getGames(date)` 要求传入的日期已是美东时间；本地 → 美东的转换用 `NBAScorePanel.toEasternDate()`（companion object）。用户界面上显示的是用户本地日期。

### AI 助手

`AIAssistantPanel.kt` 一个文件包含面板、设置持久化（`NBASettingsState`，存储于 `nba_settings.xml`）、设置页（`NBASettingsConfigurable`）三部分。要点：

- 请求体是 OpenAI 兼容格式，但额外带 `thinking` 参数（智谱特有）；API URL 由用户配置**完整地址**，代码不做任何拼接
- 支持流式（SSE，手动解析 `data: ` 行）和非流式两种响应模式，由设置中的 `stream` 开关决定
- API Key 必须用户自行配置，为空时直接报错

### 已知问题：仓库不可编译（PlayoffService 缺失）

`PlayoffBracketPanel.kt` 引用的三个类从未被提交到仓库（也不在 .gitignore 中，就是漏提交了）：

- `com.caro.nba.service.PlayoffService`（含 `getPlayoffBracket(): Result<PlayoffBracketResponse>`）
- `com.caro.nba.model.PlayoffBracketResponse`（含 `data` 字段）
- `com.caro.nba.model.PlayoffBracketSeries`

任何 `./gradlew buildPlugin` 都会因 unresolved reference 失败。涉及季后赛功能的改动，要么先补建这些类，要么移除季后赛标签页。模型结构可从 `PlayoffBracketPanel` 的用法反推：`data.top`（西部，`List<List<PlayoffBracketSeries>>` 按轮次分组）、`data.bottom`（东部）、`data.finals`（可空）；series 含 `teams: List<Team>?`（Team 有 `name`/`img`/`rank`）、`info1`/`info2`（系列赛比分字符串）、`win_threshold: Int`、`schedule.list`（每场含 `left_team`/`right_team`/`left_logo`/`right_logo`/`score`，score 格式 `"123-110"`）。队名与 ESPN 无关，疑似另一家中文数据源。

### 其他易踩的坑

- **英中队名映射重复三份**：`NBADataService`、`StandingsService`、`GameDetailService` 各自持有独立的 `teamNameMap`，修改时需三处同步
- 所有颜色用 `JBColor(亮色, 暗色)` 双值写法以同时支持明暗主题
- `NBAScoreService.kt` 与 `RefreshAction` 只是激活工具窗口的简单入口，窗口激活后由面板自身触发数据加载
