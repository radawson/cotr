plugins {
    id("java")
    id("com.gradleup.shadow") version "9.5.1"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.21"
}

group = "org.clockworx.cotr"

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://repo.thenextlvl.net/releases")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    paperweight.paperDevBundle("26.1.2.build.74-stable")

    // Shared Clockworx data layer (Hibernate + Flyway + HikariCP + JDBC drivers)
    // Provided via composite build from ../clockworx-data (see settings.gradle.kts)
    implementation("org.clockworx:clockworx-data:0.1.0-SNAPSHOT")

    // ServiceIO is optional - only needed at compile time for API reference
    compileOnly("net.thenextlvl.services:service-io:2.3.1")
    // WorldGuard is optional - used for region-based exchange tracking
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.10")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
}

tasks {
    processResources {
        filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
            expand(
                "version" to project.version,
                "project.version" to project.version
            )
        }
    }

    shadowJar {
        enableAutoRelocation = false
        archiveClassifier.set("all")
        archiveBaseName.set("CoinOfTheRealm")
        archiveVersion.set(project.version.toString())

        // Flyway/Hibernate discover plugins via META-INF/services ServiceLoader files. Several jars
        // (flyway-core + flyway-mysql, etc.) declare the SAME service path; with the default EXCLUDE
        // strategy those duplicates are dropped BEFORE mergeServiceFiles() can combine them, leaving
        // Flyway's PluginRegister empty -> NPE in DriverDataSource. INCLUDE lets the merge see them all.
        duplicatesStrategy = DuplicatesStrategy.INCLUDE

        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }

        relocate("com.zaxxer.hikari", "org.clockworx.cotr.lib.hikari")
        relocate("org.hibernate", "org.clockworx.cotr.lib.hibernate")
        relocate("org.jboss.logging", "org.clockworx.cotr.lib.jboss.logging")
        relocate("jakarta.persistence", "org.clockworx.cotr.lib.jakarta.persistence")
        relocate("org.flywaydb", "org.clockworx.cotr.lib.flywaydb")
        relocate("org.xerial.sqlite", "org.clockworx.cotr.lib.xerial.sqlite")
        relocate("com.mysql.cj", "org.clockworx.cotr.lib.mysql")

        // IMPORTANT: Specifically exclude the core SQLite package from relocation
        // to prevent breaking native library loading (JNI).
        exclude("org/sqlite/**")

        mergeServiceFiles()
    }

    jar {
        archiveBaseName.set("CoinOfTheRealm")
        archiveVersion.set(project.version.toString())
        manifest {
            attributes["paperweight-mappings-namespace"] = "mojang"
        }
        dependsOn("shadowJar")
    }
}

tasks.assemble {
    dependsOn(tasks.shadowJar)
}

tasks.build {
    dependsOn(tasks.shadowJar)
}
