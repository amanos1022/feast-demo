plugins {
    java
    id("com.diffplug.spotless") version "7.0.4"
    // id("net.ltgt.errorprone") version "4.2.0"
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "com.diffplug.spotless")
    apply(plugin = "checkstyle")

    // apply(plugin = "net.ltgt.errorprone")

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(21))
        }
    }

    // --- Spotless (formatting) ---
    spotless {
        java {
            googleJavaFormat()
            removeUnusedImports()
        }
    }

    // --- Checkstyle (style rules) ---
    configure<CheckstyleExtension> {
        toolVersion = "13.5.0"
        maxWarnings = 0
    }


    // --- Error Prone (compile-time bug detection) ---
    dependencies {
        implementation("com.fasterxml.jackson.core:jackson-databind:2.22.0")
        implementation("org.apache.kafka:kafka-clients:4.3.0")
        // "errorprone"("com.google.errorprone:error_prone_core:2.38.0")
    }
}
