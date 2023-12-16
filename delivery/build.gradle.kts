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

    implementation("ch.qos.logback:logback-classic:1.2.6")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Version.MOCKITO}")
    testImplementation("org.apache.httpcomponents.client5:httpclient5")

    testImplementation("org.testng:testng:7.1.0")
}

kover {
    excludeJavaCode()
}
