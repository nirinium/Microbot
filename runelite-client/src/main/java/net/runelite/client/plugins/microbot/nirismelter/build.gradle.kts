plugins {
    java
}

group = "net.runelite.client.plugins.microbot"
version = "1.0.0"

repositories {
    mavenCentral()
}

dependencies {
    // Core dependencies needed for the plugin
    compileOnly(project(":runelite-api"))
    compileOnly(project(":runelite-client"))
}

tasks {
    jar {
        archiveBaseName.set("nirismelter")
        archiveVersion.set("1.0.0")
        
        // Include all classes from the nirismelter package
        from(sourceSets.main.get().output)
        
        manifest {
            attributes(
                "Plugin-Name" to "Niri Smelter",
                "Plugin-Description" to "Automated smelting script",
                "Plugin-Version" to "1.0.0",
                "Plugin-Provider" to "Microbot"
            )
        }
    }
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("."))
            include("*.java")
        }
    }
}
