pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://repo.papermc.io/repository/maven-public/")
    }
}

plugins {
    // Auto-provisions the Java toolchain (JDK 25) if not installed locally
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

rootProject.name = "CoinOfTheRealm"

// Include the shared Clockworx data library as a composite build
includeBuild("../clockworx-data") {
    dependencySubstitution {
        substitute(module("org.clockworx:clockworx-data")).using(project(":"))
    }
}
