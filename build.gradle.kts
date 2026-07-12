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

    // Shared Clockworx data layer. Its own classes are bundled; its heavy Maven deps
    // (Hibernate/Flyway/HikariCP/JDBC) are NOT shaded (isTransitive=false) -- they are loaded
    // at runtime by Paper's library-loader (see CotrLoader). Removes the per-plugin relocation
    // + service-file merge and shrinks the jar from ~35 MB to a few hundred KB.
    implementation("org.clockworx:clockworx-data:0.1.0-SNAPSHOT") { isTransitive = false }

    // DB stack -- compile-only: compiled against, but provided at runtime by the library-loader,
    // not bundled. Keep in sync with clockworx-data's api() deps and CotrLoader.LIBRARIES.
    compileOnly("org.hibernate:hibernate-core:6.6.40.Final")
    compileOnly("org.hibernate:hibernate-community-dialects:6.6.40.Final")
    compileOnly("org.hibernate.orm:hibernate-hikaricp:6.6.40.Final")
    compileOnly("jakarta.persistence:jakarta.persistence-api:3.1.0")
    compileOnly("org.flywaydb:flyway-core:12.10.0")
    compileOnly("org.flywaydb:flyway-mysql:12.10.0")
    compileOnly("com.zaxxer:HikariCP:7.1.0")
    compileOnly("org.jboss.logging:jboss-logging:3.6.1.Final")
    compileOnly("org.xerial:sqlite-jdbc:3.53.2.0")
    compileOnly("com.mysql:mysql-connector-j:9.1.0")
    compileOnly("org.postgresql:postgresql:42.7.11")

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

        // The DB stack (Hibernate/Flyway/HikariCP/JDBC) is loaded at runtime via the
        // library-loader (CotrLoader), so it is neither bundled nor relocated here.

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
