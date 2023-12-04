plugins {
    id("urlshortener.spring-library-conventions")
    kotlin("plugin.spring")
    id("org.jetbrains.kotlinx.kover") version "0.7.4"
}

dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-hateoas")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("commons-validator:commons-validator:${Version.COMMONS_VALIDATOR}")
    implementation("com.google.guava:guava:${Version.GUAVA}")

    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("com.opencsv:opencsv:5.6")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    implementation("org.springframework.boot:spring-boot-starter-websocket")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Version.MOCKITO}")
    //testImplementation("org.mock-server:mockserver-netty:5.3.0")
    testImplementation("org.testng:testng:7.1.0")
}
