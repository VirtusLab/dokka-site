import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.BufferedInputStream
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import kotlin.collections.toByteArray

plugins {
    kotlin("jvm")
    id("java")
    id("maven-publish")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
}

group = "com.virtuslab.dokka"


fun String.run(): String? {
    val proc = Runtime.getRuntime()?.exec(this)
    if (proc?.waitFor() != 0) return null
    val os = proc.inputStream
    return os.let {
        val line = BufferedReader(InputStreamReader(it)).readLines().joinToString("\n")
        os.close()
        return line.trim()
    }
}

val ciGeneratedFiles = listOf("settings.xml")

fun getVersion(): String {
    val base = "git describe --tags --exact-match".run() ?: "git describe --tags".run() ?: "0.1.0-SNAPSHOT"

    val changedFiles = "git status --porcelain".run()?.lines()
        ?.filterNot { line -> line.isEmpty() || ciGeneratedFiles.find { file -> line.endsWith(" $file") } != null }

    val statusStr = changedFiles?.let { if (it.isEmpty()) "" else "-SNAPSHOT" } ?: "-SNAPSHOT"
    val v = base.removePrefix("v") + statusStr
    println("Using $v version.")
    return v
}

version = getVersion()

tasks.withType(KotlinCompile::class).all {
    val language_version: String by project
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
    mavenLocal()
}

val dokka_version: String by project

dependencies {
    compileOnly("org.jetbrains.dokka:dokka-core:$dokka_version")

    // Expose dependency to dokka in .pom file
    apiElements("org.jetbrains.dokka:dokka-core:$dokka_version")
    apiElements("org.jetbrains.dokka:dokka-base:$dokka_version")

    implementation("org.jetbrains.dokka:dokka-base:$dokka_version")
    implementation("com.vladsch.flexmark:flexmark-all:0.42.12")
    implementation("nl.big-o:liqp:0.6.7")
    implementation("org.jetbrains.kotlinx:kotlinx-html-jvm:0.7.2")

    implementation("junit:junit:4.13")

    dokkaHtmlPlugin(project(":"))
    testImplementation("org.jetbrains.dokka:dokka-core:$dokka_version")
}

// Gradle metadata
java {
    @Suppress("UnstableApiUsage")
    withSourcesJar()
    targetCompatibility = JavaVersion.VERSION_1_8
}


// Workaround for https://github.com/bintray/gradle-bintray-plugin/issues/267
// Manually disable bintray tasks added to the root project
tasks.whenTaskAdded {
    if ("bintray" in name) {
        enabled = false
    }
}

val bintrayUser: String? by project
val bintrayKey: String? by project

bintray {
    user = project.findProperty("bintray_user") as String? ?: bintrayUser
    key = project.findProperty("bintray_api_key") as String? ?: bintrayKey
    setPublications("MavenJava")
    publish = true
    pkg(delegateClosureOf<com.jfrog.bintray.gradle.BintrayExtension.PackageConfig> {
        repo = "dokka"
        userOrg = "virtuslab"
        name = project.name
        setLicenses("Apache-2.0")
        vcsUrl = "https://github.com/VirtusLab/dokka-site.git"
    })
}

publishing {
    publications {
        register<MavenPublication>("MavenJava") {
            from(components["java"])
        }
    }
}

// Configure dokka
tasks.dokkaHtml {
    // TODO (#37): use pluginConfiguration
    pluginsMapConfiguration.put("ExternalDocsTooKey", "documentation")
}
