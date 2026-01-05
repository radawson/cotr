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
  // Task to replace version placeholders in source and resource files
  val processVersion by registering {
    group = "build"
    description = "Replaces version placeholders in source and resource files"
    
    val version = project.property("version") as String
    
    doLast {
      // Process resource files
      val pluginYml = file("src/main/resources/plugin.yml")
      val paperPluginYml = file("src/main/resources/paper-plugin.yml")
      
      if (pluginYml.exists()) {
        pluginYml.writeText(pluginYml.readText().replace("\${version}", version))
      }
      
      if (paperPluginYml.exists()) {
        paperPluginYml.writeText(paperPluginYml.readText().replace("\${version}", version))
      }
      
      // Process Java source file (if version replacement is needed)
      // Note: CoinOfTheRealmPlugin doesn't currently use version placeholders
    }
  }
  
  // Make processResources and compileJava depend on processVersion
  processResources {
    dependsOn("processVersion")
  }
  
  compileJava {
    dependsOn("processVersion")
  }
  
  // Configure the JAR task to include plugin.yml
  jar {
    archiveBaseName.set("CoinOfTheRealm")
    archiveVersion.set(project.property("version") as String)
    
    // Include plugin.yml in the JAR
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