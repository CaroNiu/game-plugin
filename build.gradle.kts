import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask

plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.22"
    id("org.jetbrains.intellij.platform") version "2.1.0"
}

group = "com.caro"
version = "1.0.0"

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
        // 同步降低 IDEA 版本到 2023.2
        intellijIdeaCommunity("2023.2")
        pluginVerifier()
        instrumentationTools()
    }
    
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
}

intellijPlatform {
    pluginConfiguration {
        name = "NBA Live Score"
        version = "1.0.0"
        
        ideaVersion {
            sinceBuild = "232"
            untilBuild = "241.*"
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