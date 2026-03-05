pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        // 添加 JetBrains 官方仓库
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")

    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        // 确保依赖解析也能访问 JetBrains 仓库
        maven("https://www.jetbrains.com/intellij-repository/releases")
        maven("https://www.jetbrains.com/intellij-repository/snapshots")
    }
}

rootProject.name = "nba-score-plugin"