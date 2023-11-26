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
    // Para hacer la generacion del Qr
    implementation ("com.google.zxing:core:3.4.1")
    // Para la cabecera User-Agent
    implementation ("eu.bitwalker:UserAgentUtils:1.21")
    // Para procesar el csv
    implementation("com.opencsv:opencsv:5.6")

    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.5.2")

}