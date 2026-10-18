plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

import java.util.jar.Attributes
import java.util.jar.JarOutputStream
import java.util.jar.Manifest
import java.util.zip.ZipEntry

val appVersionName = "0.1.6"

val releaseKeystorePath = providers.environmentVariable("CODEX_REMOTE_KEYSTORE_PATH").orNull
val releaseKeystorePassword = providers.environmentVariable("CODEX_REMOTE_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("CODEX_REMOTE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("CODEX_REMOTE_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(
    releaseKeystorePath,
    releaseKeystorePassword,
    releaseKeyAlias,
    releaseKeyPassword,
)
val hasReleaseSigning = releaseSigningValues.all { !it.isNullOrBlank() }

require(releaseSigningValues.none { !it.isNullOrBlank() } || hasReleaseSigning) {
    "Release signing requires all CODEX_REMOTE_KEYSTORE_* and CODEX_REMOTE_KEY_* variables."
}

android {
    namespace = "com.codex.remote"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.codex.remote"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables.useSupportLibrary = true
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = file(releaseKeystorePath!!)
                storePassword = releaseKeystorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += setOf(
            "META-INF/DEPENDENCIES",
            "META-INF/LICENSE*",
            "META-INF/NOTICE*",
            "META-INF/versions/9/OSGI-INF/MANIFEST.MF",
        )
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.10.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.hierynomus:sshj:0.39.0")
    implementation("org.slf4j:slf4j-nop:2.0.13")
    implementation("io.noties.markwon:core:4.6.2")
    implementation("io.noties.markwon:inline-parser:4.6.2")
    implementation("io.noties.markwon:ext-latex:4.6.2")
    implementation("io.noties.markwon:ext-strikethrough:4.6.2")
    implementation("io.noties.markwon:ext-tables:4.6.2")

    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test:rules:1.6.1")
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
}

val namedApkDirectory = layout.buildDirectory.dir("outputs/named-apk")

tasks.register<Copy>("copyNamedDebugApk") {
    from(layout.buildDirectory.file("outputs/apk/debug/app-debug.apk"))
    into(namedApkDirectory)
    rename { "Codex-Remote-v$appVersionName-debug.apk" }
}

tasks.register<Copy>("copyNamedReleaseApk") {
    from(layout.buildDirectory.dir("outputs/apk/release")) {
        include("app-release*.apk")
    }
    into(namedApkDirectory)
    rename { sourceName ->
        val signingLabel = if (sourceName.contains("unsigned", ignoreCase = true)) "-unsigned" else ""
        "Codex-Remote-v$appVersionName-release$signingLabel.apk"
    }
}

tasks.configureEach {
    when (name) {
        "assembleDebug" -> finalizedBy("copyNamedDebugApk")
        "assembleRelease" -> finalizedBy("copyNamedReleaseApk")
    }
}

tasks.withType<Test>().configureEach {
    // Gradle writes this classpath into a UTF-8 @argfile, but this Windows JVM
    // reads argfiles as GBK; a manifest jar keeps the argument ASCII-only.
    doFirst {
        val manifestJar = File.createTempFile("codex-remote-", "-classpath.jar").apply { deleteOnExit() }
        val attributes = Attributes()
        attributes.putValue("Manifest-Version", "1.0")
        attributes.putValue(
            "Class-Path",
            classpath.files.joinToString(" ") { it.toURI().toASCIIString() },
        )
        val output = JarOutputStream(manifestJar.outputStream().buffered())
        output.use {
            it.putNextEntry(ZipEntry("META-INF/MANIFEST.MF"))
            Manifest().apply { mainAttributes.putAll(attributes) }.write(it)
        }
        classpath = files(manifestJar)
    }
}
