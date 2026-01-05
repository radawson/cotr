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
}

dependencies {
  compileOnly("io.papermc.paper:paper-api:1.21.11-R0.1-SNAPSHOT")
  
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
// if you have shadowJar configured
tasks.shadowJar {
  manifest {
    attributes["paperweight-mappings-namespace"] = "mojang"
  }
}