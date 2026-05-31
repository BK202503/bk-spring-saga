plugins {
    kotlin("jvm")
    `java-library`
    `maven-publish`
}

dependencies {
    api(project(":saga-core"))
    api(project(":saga-storage-jdbc"))
    api(libs.spring.boot.starter)
    api(libs.spring.boot.autoconfigure)
    implementation(libs.spring.boot.starter.jdbc)
    implementation(libs.micrometer.core)
    annotationProcessor(libs.spring.boot.configuration.processor)

    testImplementation(libs.spring.boot.starter.test)
    testImplementation(libs.h2)
    testImplementation(libs.kotest.assertions)
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
