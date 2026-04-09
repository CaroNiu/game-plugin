import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.caro"
version = "3.0.5"

repositories {
    mavenCentral()
    // 显式添加 JetBrains 仓库
    maven("https://www.jetbrains.com/intellij-repository/releases")
    maven("https://www.jetbrains.com/intellij-repository/snapshots")
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        // 使用 IDEA 2024.3 编译
        intellijIdeaCommunity("2024.3")
        pluginVerifier()
        instrumentationTools()
    }
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
}

intellijPlatform {
    pluginConfiguration {
        name = "NBA Live Score"
        version = "3.0.5"
        
        // 适配 IDEA 2024.2 到 2025.3+
        ideaVersion {
            sinceBuild = "242"
            untilBuild = provider { null }  // 不设置上限
        }
    }
}

kotlin {
    jvmToolchain(17)
}

tasks {
    withType<RunIdeTask> {
        jvmArgumentProviders += CommandLineArgumentProvider {
            listOf("-Xmx2G")
        }
    }
}