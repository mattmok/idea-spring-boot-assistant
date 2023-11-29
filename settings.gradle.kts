pluginManagement {
    plugins {
        id("org.jetbrains.intellij") version "1.14.2"
        id("org.jetbrains.changelog") version "1.3.1"
        id("io.freefair.lombok") version "6.4.3"
    }

    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        gradlePluginPortal()
    }
}
rootProject.name = "tiamaes-spring-boot-assistant"