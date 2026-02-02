plugins {
    kotlin("jvm")
    kotlin("plugin.serialization")
}

val exposedVersion: String by project

dependencies {
    implementation(project(":core"))

    // Database
    implementation("org.jetbrains.exposed:exposed-core:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-dao:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-jdbc:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-json:$exposedVersion")
    implementation("org.jetbrains.exposed:exposed-kotlin-datetime:$exposedVersion")

    // PostgreSQL
    implementation("org.postgresql:postgresql:42.7.5")

    // Connection pooling
    implementation("com.zaxxer:HikariCP:6.2.1")

    // Migrations
    implementation("org.flywaydb:flyway-core:11.4.0")
    implementation("org.flywaydb:flyway-database-postgresql:11.4.0")
}
