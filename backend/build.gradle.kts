plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
    alias(ktorLibs.plugins.ktor)
    alias(libs.plugins.ksp)
    application
}

group = "ai.railio.invoice"
version = "0.1.0"

kotlin {
    jvmToolchain(21)
}

dependencies {
    // Ktor server (all Ktor artifacts come from the official `ktorLibs` catalog)
    implementation(ktorLibs.server.config.yaml)
    implementation(ktorLibs.server.core)
    implementation(ktorLibs.server.netty)
    implementation(ktorLibs.server.contentNegotiation)
    implementation(ktorLibs.server.cors)
    implementation(ktorLibs.server.sse)
    implementation(ktorLibs.server.statusPages)
    implementation(ktorLibs.server.callLogging)
    implementation(ktorLibs.server.openapi)
    implementation(ktorLibs.server.swagger)
    implementation(ktorLibs.serialization.kotlinx.json)

    // Koog agent framework
    implementation(libs.koog.agents)

    // PDF text extraction (text-extractable PDFs only)
    implementation(libs.pdfbox)

    // Koin DI + annotations via KSP (versions pinned in the catalog; see note there)
    implementation(libs.koin.core)
    implementation(libs.koin.ktor)
    implementation(libs.koin.logger.slf4j)
    implementation(libs.koin.annotations)
    ksp(libs.koin.ksp.compiler)

    // Coroutines / serialization / logging
    implementation(libs.kotlinx.coroutines)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.datetime)
    implementation(libs.logback.classic)

    // Testing
    testImplementation(ktorLibs.server.testHost)
    testImplementation(ktorLibs.client.contentNegotiation)
    testImplementation(libs.koin.test)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

// Koin annotations: let KSP-generated code be visible to the compiler.
kotlin.sourceSets.main {
    kotlin.srcDir("build/generated/ksp/main/kotlin")
}

ksp {
    // Verify Koin modules and definitions at compile time.
    arg("KOIN_CONFIG_CHECK", "true")
}

tasks.test {
    useJUnitPlatform()
}
