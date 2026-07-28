plugins {
    // Apply the application plugin to add support for building a CLI application in Java.
    // application plugin includes java plugin
    application
}

group = "ns.forge"
version = "0.1.0"

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

dependencies {
    // This dependency is used by the application.
    implementation(libs.guava)
    implementation("com.anthropic:anthropic-java:2.51.0")
    implementation("io.github.cdimascio:dotenv-java:3.2.0")
}

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    standardInput = System.`in`
}

testing {
    suites {
        // Configure the built-in test suite
        val test by getting(JvmTestSuite::class) {
            // Use JUnit Jupiter test framework
            useJUnitJupiter("6.0.1")
        }
    }
}

// Apply a specific Java toolchain to ease working on different environments.
java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(24)
    }
}

application {
    // Define the main class for the application.
    mainClass = "ns.forge.Main"
}
