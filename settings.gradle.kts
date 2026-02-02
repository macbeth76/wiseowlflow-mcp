rootProject.name = "wiseowlflow-mcp"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositories {
        mavenCentral()
        maven("https://jitpack.io")
    }
}

include(
    ":core",
    ":persistence",
    ":session",
    ":mcp-proxy",
    ":workflow-engine",
    ":auth",
    ":billing",
    ":api",
    ":app"
)
