plugins {
    id("urlshortener.kotlin-common-conventions")
}

repositories {
    gradlePluginPortal()
}

val kotlinVersion by extra("1.9.10")
val springBootVersion by extra("3.1.3")
val detektVersion by extra("1.23.1")

dependencies {
    implementation("org.springframework:spring-core:5.3.12")
    implementation("org.springframework:spring-beans:5.3.12")
    implementation("org.springframework:spring-context:5.3.9")

    // Qr
    implementation ("com.google.zxing:core:3.4.1")

    // User-Agent
    implementation ("eu.bitwalker:UserAgentUtils:1.21")

    // CSV
    implementation("com.opencsv:opencsv:5.6")

    // Queues
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

    // Logs
    implementation("ch.qos.logback:logback-classic:1.2.6")

    // Metrics
    implementation("org.springframework.boot:spring-boot-starter-actuator:2.7.0")
    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0")
}
