import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm") apply false
    id("java")
}


group = "org.jetbrains.dokka"
version = "0.1.0"

val language_version: String by project

tasks.withType(KotlinCompile::class).all {
    kotlinOptions {
        freeCompilerArgs += "-Xjsr305=strict -Xskip-metadata-version-check -Xopt-in=kotlin.RequiresOptIn."
        languageVersion = language_version
        apiVersion = language_version
        jvmTarget = "1.8"
    }
}

repositories {
    jcenter()
    mavenCentral()
    maven(url = "https://dl.bintray.com/kotlin/kotlin-eap")
    maven(url = "https://dl.bintray.com/kotlin/kotlin-dev")
}

dependencies {
    implementation("org.jetbrains.dokka:dokka-core:1.4-mc-1")
    implementation("org.jetbrains.dokka:dokka-base:1.4-mc-1")
    implementation("com.vladsch.flexmark:flexmark-all:0.42.12")
    implementation("nl.big-o:liqp:0.6.7")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.6.10")

    implementation("junit:junit:4.13")
}

apply {
    plugin("org.jetbrains.kotlin.jvm")
    plugin("java")
}

// Gradle metadata
java {
    @Suppress("UnstableApiUsage")
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_1_8
}


// Workaround for https://github.com/bintray/gradle-bintray-plugin/issues/267
//  Manually disable bintray tasks added to the root project
tasks.whenTaskAdded {
    if ("bintray" in name) {
        enabled = false
    }
}
