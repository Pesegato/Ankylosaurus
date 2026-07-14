plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.jib)
}

group = "com.pesegato.a10s"
version = "1.0-SNAPSHOT"

application {
    mainClass.set("com.pesegato.a10s.AnkylosaurusKt")
}

dependencies {

    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.ktor.server.auth.jwt)
    implementation(libs.ktor.serialization.kotlinx.json)
    implementation(libs.ktor.server.contentnegotiation)
    implementation(libs.ktor.server.cors)
    implementation(libs.ktor.server.auto.head.response)
    implementation(libs.ktor.network.tls.certificates)

    implementation(libs.webauthn4j)
    implementation(libs.exposed.core)
    implementation(libs.exposed.dao)
    implementation(libs.exposed.jdbc)
    implementation(libs.postgresql)
    implementation(libs.hikaricp)
    implementation(libs.tink)

    implementation(libs.mordant)
    implementation(libs.mordant.coroutines)
    implementation(libs.mordant.markdown)
    implementation(libs.logback)

}

kotlin {
    jvmToolchain(21)
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinJvmCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

jib {
    from {
        image = "eclipse-temurin:21-jre-alpine"
    }
    to {
        image = "velociraptor:latest" // Nome dell'immagine locale
    }
    container {
        mainClass = "com.pesegato.a10s.AnkylosaurusKt"
        ports = listOf("8080")
        user = "1000:1000" // Usa UID:GID invece del nome se l'utente non esiste nell'immagine base
        volumes = listOf("/app/data", "/app/certs")
    }
}

// The Ktor plugin provides a 'buildFatJar' task automatically.
// You can configure it like this:
ktor {
    fatJar {
        archiveFileName.set("a10s.jar")
    }
}
