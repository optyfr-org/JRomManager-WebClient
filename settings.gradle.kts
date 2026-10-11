pluginManagement {
    plugins {
        id("org.sonarqube") version "latest.release"
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "WebClient"
if (hasProperty("debug")) {
    rootProject.buildFileName = "debug.gradle.kts"
} else {
    rootProject.buildFileName = "build.gradle.kts"
}
