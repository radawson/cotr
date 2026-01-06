plugins {
    id("java")
    id("com.gradleup.shadow") version "9.0.0-beta12"
    id("io.papermc.paperweight.userdev") version "2.0.0-beta.18"
}

group = "org.clockworx.cotr"

repositories {
  maven {
    name = "papermc"
    url = uri("https://repo.papermc.io/repository/maven-public/")
  }
  maven {
    name = "thenextlvl"
    url = uri("https://repo.thenextlvl.net/releases")
  }
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  // ServiceIO is optional - only needed at compile time for API reference
  // The plugin uses reflection to interact with ServiceIO at runtime
  compileOnly("net.thenextlvl.services:service-io:2.3.1")
  
  // Database dependencies
  implementation("com.zaxxer:HikariCP:5.1.0")
  implementation("org.xerial:sqlite-jdbc:3.45.3.0")
  // MySQL connector (optional, only needed if using MySQL)
  implementation("com.mysql:mysql-connector-j:9.1.0")
  // SLF4J implementation - bridges SLF4J to java.util.logging (used by HikariCP)
  implementation("org.slf4j:slf4j-jdk14:2.0.16")
  
  paperweight.paperDevBundle("1.21.11-R0.1-SNAPSHOT")
}

java {
  toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
  // Process resources at build time to replace version placeholders
  // This modifies files in the build output, NOT the source files
  processResources {
    // Process YAML resource files for version expansion
    filesMatching(listOf("plugin.yml", "paper-plugin.yml")) {
      // Replace ${version} and ${project.version} with actual version
      // The expand() method processes files during build and outputs to build/resources
      expand(
        "version" to project.version,
        "project.version" to project.version
      )
    }
  }
  
  // Configure the JAR task
  jar {
    archiveBaseName.set("CoinOfTheRealm")
    archiveVersion.set(project.version.toString())
    
    // Include processed resources in the JAR
    from(sourceSets.main.get().output)
    
    // Copy resources (like plugin.yml) into the JAR
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
  }
}

tasks.jar {
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }
}
// Configure shadowJar to include dependencies
tasks.shadowJar {
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }
  
  // Relocate database dependencies to avoid conflicts with other plugins
  relocate("com.zaxxer.hikari", "org.clockworx.cotr.libs.hikari")
  relocate("org.sqlite", "org.clockworx.cotr.libs.sqlite")
  relocate("com.mysql.cj", "org.clockworx.cotr.libs.mysql") // MySQL connector
  relocate("org.slf4j", "org.clockworx.cotr.libs.slf4j") // HikariCP uses SLF4J
  // Note: slf4j-jdk14 implementation classes are automatically relocated with org.slf4j
  
  // Merge service files - critical for JDBC driver service provider loading
  // This ensures META-INF/services files are properly merged when relocating classes
  mergeServiceFiles()
  
  // Archive configuration
  archiveBaseName.set("CoinOfTheRealm")
  archiveVersion.set(project.version.toString())
  
  // Use "-all" classifier to make it clear this is the fat JAR with dependencies
  // This prevents confusion with the regular JAR (which doesn't have dependencies)
  archiveClassifier.set("all")
  
  // Shadow plugin automatically includes all runtime dependencies by default
  // No need to explicitly set configurations
}

// Ensure shadowJar is executed during the build process
tasks.build {
  dependsOn(tasks.shadowJar)
}