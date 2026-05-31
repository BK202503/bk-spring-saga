import java.io.File

plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":saga-core"))
    api(libs.spring.kafka)
    implementation(libs.jackson.module.kotlin)
    implementation(libs.jackson.datatype.jsr310)
    implementation(libs.kotlinx.coroutines.jdk8)
    implementation(libs.slf4j.api)
    compileOnly(libs.spring.boot.autoconfigure)

    testImplementation(kotlin("test"))
    testImplementation(libs.kotest.runner)
    testImplementation(libs.kotest.assertions)
    testImplementation(libs.testcontainers.junit)
    testImplementation(libs.testcontainers.kafka)
    testImplementation(libs.spring.kafka.test)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.test {
    if (System.getenv("DOCKER_HOST") == null) {
        val candidates = listOf(
            "${System.getProperty("user.home")}/.docker/run/docker.sock",
            "${System.getProperty("user.home")}/.colima/default/docker.sock",
            "/var/run/docker.sock",
        )
        candidates.firstOrNull { File(it).exists() }?.let { sock ->
            environment("DOCKER_HOST", "unix://$sock")
        }
    }
}
