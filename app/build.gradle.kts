plugins {
    id("urlshortener.spring-app-conventions")
    id("org.jetbrains.kotlinx.kover") version "0.7.4"
    kotlin("plugin.spring")
}

dependencies {
    implementation(project(":core"))
    implementation(project(":delivery"))
    implementation(project(":repositories"))
    implementation("org.springframework.boot:spring-boot-starter")
    implementation("org.webjars:bootstrap:${Version.BOOTSTRAP}")
    implementation("org.webjars:jquery:${Version.JQUERY}")
    implementation("org.webjars:sockjs-client:1.5.1")
    implementation("org.webjars:stomp-websocket:2.3.3")

    implementation("io.micrometer:micrometer-registry-prometheus:1.12.0") 
    implementation("org.springframework.boot:spring-boot-starter-actuator")

    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.2.0")

    implementation("org.springframework.boot:spring-boot-starter-websocket")

    runtimeOnly("org.hsqldb:hsqldb")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.springframework.boot:spring-boot-starter-web")
    testImplementation("org.springframework.boot:spring-boot-starter-jdbc")
    testImplementation("org.mockito.kotlin:mockito-kotlin:${Version.MOCKITO}")
    testImplementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    testImplementation("org.apache.httpcomponents.client5:httpclient5")
}

kover {
    excludeJavaCode()
}