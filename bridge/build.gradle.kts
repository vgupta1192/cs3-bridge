plugins {
    kotlin("jvm")
    application
}

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
    }
}

dependencies {
    implementation(project(":library"))
    implementation(project(":plugin-runtime"))
    implementation(project(":android-stubs"))
    implementation(project(":common"))

    implementation("org.bouncycastle:bcprov-jdk18on:1.77")
    implementation(libs.nicehttp)
    implementation("io.ktor:ktor-http-jvm:3.5.0")
    implementation("com.fleeksoft.ksoup:ksoup-jvm:0.2.6")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.okhttp3:okhttp-dnsoverhttps:4.12.0")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin:2.13.1")
    implementation(kotlin("reflect"))
    implementation("org.json:json:20240303")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")
    implementation("org.xerial:sqlite-jdbc:3.46.1.0")
    implementation("me.xdrop:fuzzywuzzy:1.4.0")
    implementation(libs.slf4j.api)
    implementation("org.slf4j:slf4j-simple:2.0.13")
}

application {
    mainClass.set("com.kissmissi.csbridge.MainKt")
}
