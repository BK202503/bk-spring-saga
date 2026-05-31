rootProject.name = "spring-saga-kt"

include(
    "saga-core",
    "saga-storage-jdbc",
    "saga-events-kafka",
    "saga-spring-boot-starter",
    "examples:order-saga",
)

dependencyResolutionManagement {
    repositories {
        mavenCentral()
    }
    versionCatalogs {
        create("libs") {
            version("kotlin", "2.0.21")
            version("coroutines", "1.9.0")
            version("springBoot", "3.3.5")
            version("jackson", "2.18.0")
            version("kotest", "5.9.1")
            version("h2", "2.3.232")
            version("hikari", "5.1.0")
            version("slf4j", "2.0.16")
            version("micrometer", "1.13.6")
            version("testcontainers", "1.20.4")
            version("postgres", "42.7.4")
            version("springKafka", "3.2.4")

            library("kotlinx-coroutines-core", "org.jetbrains.kotlinx", "kotlinx-coroutines-core").versionRef("coroutines")
            library("kotlinx-coroutines-jdk8", "org.jetbrains.kotlinx", "kotlinx-coroutines-jdk8").versionRef("coroutines")
            library("kotlinx-coroutines-slf4j", "org.jetbrains.kotlinx", "kotlinx-coroutines-slf4j").versionRef("coroutines")
            library("jackson-module-kotlin", "com.fasterxml.jackson.module", "jackson-module-kotlin").versionRef("jackson")
            library("jackson-datatype-jsr310", "com.fasterxml.jackson.datatype", "jackson-datatype-jsr310").versionRef("jackson")
            library("slf4j-api", "org.slf4j", "slf4j-api").versionRef("slf4j")
            library("micrometer-core", "io.micrometer", "micrometer-core").versionRef("micrometer")

            library("spring-boot-starter", "org.springframework.boot", "spring-boot-starter").versionRef("springBoot")
            library("spring-boot-autoconfigure", "org.springframework.boot", "spring-boot-autoconfigure").versionRef("springBoot")
            library("spring-boot-configuration-processor", "org.springframework.boot", "spring-boot-configuration-processor").versionRef("springBoot")
            library("spring-boot-starter-jdbc", "org.springframework.boot", "spring-boot-starter-jdbc").versionRef("springBoot")
            library("spring-boot-starter-web", "org.springframework.boot", "spring-boot-starter-web").versionRef("springBoot")
            library("spring-boot-starter-test", "org.springframework.boot", "spring-boot-starter-test").versionRef("springBoot")

            library("h2", "com.h2database", "h2").versionRef("h2")
            library("hikari", "com.zaxxer", "HikariCP").versionRef("hikari")

            library("kotest-runner", "io.kotest", "kotest-runner-junit5").versionRef("kotest")
            library("kotest-assertions", "io.kotest", "kotest-assertions-core").versionRef("kotest")
            library("kotest-property", "io.kotest", "kotest-property").versionRef("kotest")

            library("testcontainers-junit", "org.testcontainers", "junit-jupiter").versionRef("testcontainers")
            library("testcontainers-postgres", "org.testcontainers", "postgresql").versionRef("testcontainers")
            library("testcontainers-kafka", "org.testcontainers", "kafka").versionRef("testcontainers")
            library("postgres-driver", "org.postgresql", "postgresql").versionRef("postgres")
            library("spring-kafka", "org.springframework.kafka", "spring-kafka").versionRef("springKafka")
            library("spring-kafka-test", "org.springframework.kafka", "spring-kafka-test").versionRef("springKafka")
        }
    }
}
