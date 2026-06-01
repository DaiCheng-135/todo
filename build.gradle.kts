plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("org.jetbrains.intellij") version "1.17.4"
}

group = "com.todo.plugin"
version = "1.1.0"

repositories {
    mavenCentral()
}

intellij {
    version.set("2024.1")
    type.set("IC")
}

tasks {
    patchPluginXml {
        sinceBuild.set("241")
        untilBuild.set("")  // 移除上限，兼容 2024.1 及之后所有版本
    }
    runIde {}
    
    // Set the plugin archive name
    buildPlugin {
        archiveFileName.set("git-ai-assistant-${project.version}.zip")
    }
}
