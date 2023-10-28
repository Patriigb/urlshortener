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
    //Para hacer la generacion del Qr
    implementation ("com.google.zxing:core:3.4.1")
}