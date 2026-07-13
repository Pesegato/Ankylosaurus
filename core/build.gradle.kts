plugins {
    alias(libs.plugins.kotlin.jvm)
    id("java-library")
    `maven-publish`
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
}

group = "com.pesegato"
version = "1.0-SNAPSHOT"

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
            groupId = "com.pesegato"
            artifactId = "a10s-core"
            version = "1.0-SNAPSHOT"
        }
    }
    repositories {
        maven {
            name = "GitHubPackages"
            url = uri("https://maven.pkg.github.com/pesegato/Ankylosaurus")
            credentials {
                username = System.getenv("GITHUB_ACTOR")
                password = System.getenv("GITHUB_TOKEN")
            }
        }
    }
}