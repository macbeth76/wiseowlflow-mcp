plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core"))

    // Redis
    implementation("io.lettuce:lettuce-core:6.3.2.RELEASE")
}
