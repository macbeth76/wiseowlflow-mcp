import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.2.0" apply false
    kotlin("plugin.serialization") version "2.2.0" apply false
}

allprojects {
    group = "com.wiseowlflow"
    version = "0.1.0"
}

subprojects {
    apply(plugin = "org.jetbrains.kotlin.jvm")
    apply(plugin = "org.jetbrains.kotlin.plugin.serialization")

    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }

    tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_21)
            freeCompilerArgs.addAll(
                "-Xcontext-receivers",
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
                "-opt-in=kotlinx.serialization.ExperimentalSerializationApi"
            )
        }
    }

    tasks.withType<Test> {
        useJUnitPlatform()
    }

    dependencies {
        val kotlinVersion: String by project
        val ktorVersion: String by project
        val exposedVersion: String by project

        // Common dependencies for all modules
        "implementation"(platform("org.jetbrains.kotlin:kotlin-bom:$kotlinVersion"))
        "implementation"("org.jetbrains.kotlin:kotlin-stdlib")
        "implementation"("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.1")
        "implementation"("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
        "implementation"("org.jetbrains.kotlinx:kotlinx-datetime:0.6.1")

        // Logging
        "implementation"("io.github.oshai:kotlin-logging-jvm:7.0.3")
        "implementation"("ch.qos.logback:logback-classic:1.5.16")

        // Testing
        "testImplementation"("org.jetbrains.kotlin:kotlin-test")
        "testImplementation"("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.1")
        "testImplementation"("io.mockk:mockk:1.13.16")
        "testImplementation"("org.junit.jupiter:junit-jupiter:5.11.4")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }
}

// Version catalog for shared dependencies
extra["versions"] = mapOf(
    "ktor" to "3.1.3",
    "exposed" to "0.60.0",
    "mcpSdk" to "0.8.3",
    "postgresql" to "42.7.5",
    "flyway" to "11.4.0",
    "lettuce" to "6.5.0",
    "kstatemachine" to "0.33.0",
    "ollama" to "0.2.0",
    "stripe" to "28.4.0",
    "bcrypt" to "0.10.2"
)
