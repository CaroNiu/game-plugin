import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.caro"
version = "3.0.0"

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
        // 升级到 IDEA 2025.3
        intellijIdeaCommunity("2025.3")
        pluginVerifier()
        instrumentationTools()
    }
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.0")
}

intellijPlatform {
    pluginConfiguration {
        name = "NBA Live Score"
        version = "3.0.0"
        
        ideaVersion {
            sinceBuild = "253"
            untilBuild = "253.*"
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