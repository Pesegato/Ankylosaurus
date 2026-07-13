// settings.gradle.kts
rootProject.name = "Ankylosaurus"

include("core")

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google() // optional, but harmless
    }
}
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "0.10.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google() // Aggiungi questo
        mavenCentral() // Aggiungi questo
        maven("https://maven.pkg.jetbrains.space/public/p/compose/dev")
    }
}