import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    kotlin("jvm")
    id("java")
    id("maven-publish")
    id("org.jetbrains.dokka")
    id("com.jfrog.bintray")
}

group = "com.virtuslab.dokka"
version = "0.1.3"

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
}

dependencies {
    val dokka_version: String by project
    compileOnly("org.jetbrains.dokka:dokka-core:$dokka_version")
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

val bintrayKey: String? by project

bintray {
    user = "kromanowski"
    key = project.findProperty("bintray_api_key") as String? ?: bintrayKey
    setPublications("MavenJava")
    publish = true
    pkg(delegateClosureOf<com.jfrog.bintray.gradle.BintrayExtension.PackageConfig> {
        repo = "releases"
        userOrg = "romanowski"
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
    pluginsConfiguration.put("ExternalDocsTooKey",  "documentation")
}
