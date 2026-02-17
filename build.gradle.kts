plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.ktor)
}

group = "com"
version = "0.0.1"

application {
    mainClass = "io.ktor.server.netty.EngineMain"
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.netty)
    implementation(libs.logback.classic)
    implementation(libs.ktor.server.core)
    implementation(libs.ktor.server.config.yaml)
    testImplementation(libs.ktor.server.test.host)
    testImplementation(libs.kotlin.test.junit)

    implementation(libs.postgresql)
    implementation(libs.dotenv.kotlin)
    implementation(libs.hikari)

    // Exposed ORM
    implementation(libs.exposed.core)
    implementation(libs.exposed.jdbc)
    implementation(libs.h2)
}

// ==========================
// Custom runDev task for development
// ==========================
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Run Ktor in development mode with auto-reload"
    mainClass.set("com.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("io.ktor.development", "true")
}

// ==========================
// Custom runDev task for development
// ==========================
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Run Ktor in development mode with auto-reload"
    mainClass.set("com.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("io.ktor.development", "true")
}

// ==========================
// Custom runDev task for development
// ==========================
tasks.register<JavaExec>("runDev") {
    group = "application"
    description = "Run Ktor in development mode with auto-reload"
    mainClass.set("com.ApplicationKt")
    classpath = sourceSets["main"].runtimeClasspath
    systemProperty("io.ktor.development", "true")
}
