# NBA Live Score - IntelliJ IDEA Plugin

<p align="center">
  <img src="src/main/resources/icons/nba.svg" width="100" height="100" alt="NBA Logo">
</p>

<p align="center">
  <b>在 IDEA 中查看 NBA 实时比分</b>
</p>

---

## 功能特性

- 🏀 **实时比分** - 显示当天所有 NBA 比赛实时比分
- 🔄 **自动刷新** - 支持 60 秒自动刷新
- 📊 **比赛详情** - 显示比赛状态、节次、时间
- 🎯 **工具栏集成** - 右侧工具栏快速访问

## 安装

### 方式一：从文件安装

1. 下载最新的插件包 `nba-score-plugin-1.0.0.zip`
2. 打开 IDEA → Settings → Plugins → ⚙️ → Install Plugin from Disk
3. 选择下载的 zip 文件
4. 重启 IDEA

### 方式二：从源码构建

```bash
# 克隆仓库
git clone https://github.com/CaroNiu/game-plugin.git
cd game-plugin

# 构建
./gradlew buildPlugin

# 插件位于
# build/distributions/nba-score-plugin-1.0.0.zip
```

## 使用方法

1. 安装插件后，IDEA 右侧会出现 **NBA Score** 工具窗口
2. 点击打开即可查看当天比赛
3. 点击 **刷新** 按钮手动更新
4. 勾选 **自动刷新** 每 60 秒自动更新

## 技术栈

- Kotlin
- IntelliJ Platform Plugin SDK
- OkHttp (网络请求)
- Gson (JSON 解析)
- Coroutines (异步处理)

## API 说明

使用 ESPN 公开 API 获取比赛数据：
- `https://site.api.espn.com/apis/site/v2/sports/basketball/nba/scoreboard`

## 开发环境

- IntelliJ IDEA 2023.3+
- JDK 17
- Kotlin 1.9.22
- Gradle 8.5.x

## 项目结构

```
nba-score-plugin/
├── build.gradle.kts          # Gradle 构建配置
├── settings.gradle.kts       # Gradle 设置
└── src/
    └── main/
        ├── kotlin/com/caro/nba/
        │   ├── model/
        │   │   └── NBAGame.kt          # 数据模型
        │   ├── service/
        │   │   └── NBADataService.kt   # 数据服务
        │   ├── NBAScorePanel.kt        # 主面板 UI
        │   ├── NBAScoreToolWindowFactory.kt
        │   ├── NBAScoreService.kt
        │   └── RefreshAction.kt        # 刷新动作
        └── resources/
            ├── META-INF/
            │   └── plugin.xml          # 插件配置
            └── icons/
                └── nba.svg             # 图标
```

## License

MIT License

---

Made with ❤️ by Caro
