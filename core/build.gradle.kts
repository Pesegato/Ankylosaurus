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

            pom {
                name.set("Ankylosaurus Core")
                description.set("Core library for Jurassic projects")
                url.set("https://github.com/pesegato/Ankylosaurus")
                licenses {
                    license {
                        name.set("BSD 3-Clause License")
                        url.set("https://opensource.org/licenses/BSD-3-Clause")
                    }
                }
            }
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