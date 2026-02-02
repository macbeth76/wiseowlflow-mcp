plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

dependencies {
    implementation(project(":core"))

    // Stripe
    implementation("com.stripe:stripe-java:28.4.0")
}
