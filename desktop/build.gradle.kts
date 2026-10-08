import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose.multiplatform)
}

// ---------------------------------------------------------------------------------------------
// Shared code
//
// The forensic DSP core, the data models and the theme are plain Kotlin/Compose and are shared
// verbatim with the Android app by pointing this module's source set at those directories. The
// Android-only IO helpers live in com.example.audio and com.example.data, which are not included,
// so no source filtering is needed: every file under com.example.dsp is platform neutral.
// ---------------------------------------------------------------------------------------------
kotlin {
    // The JDK running Gradle compiles the module; only the bytecode target is pinned so the app runs
    // on any Java 17+ runtime. Avoiding jvmToolchain keeps the build from provisioning a second JDK.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }

    sourceSets["main"].kotlin.srcDir(rootProject.file("app/src/main/java/com/example/dsp"))
    sourceSets["main"].kotlin.srcDir(rootProject.file("app/src/main/java/com/example/model"))
    sourceSets["main"].kotlin.srcDir(rootProject.file("app/src/main/java/com/example/ui/theme"))
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation(libs.kotlinx.coroutines.swing)

    // javax.sound only decodes WAV/AU/AIFF on its own. These Service Providers add the compressed
    // formats analysts actually receive (MP3 voice memos, FLAC archival, OGG), so importing them
    // works exactly like it does on Android through MediaCodec.
    implementation(libs.mp3spi)
    implementation(libs.vorbisspi)
    implementation(libs.jflac.codec)

    testImplementation(libs.junit)
    testImplementation(kotlin("test-junit"))
}

tasks.test {
    // Optional fixtures: real compressed files to decode. Pass -Dfixture.mp3= / -Dfixture.flac= /
    // -Dfixture.ogg= to cover those paths; the WAV tests always run.
    listOf("mp3", "flac", "ogg").forEach { format ->
        systemProperty("fixture.$format", System.getProperty("fixture.$format") ?: "")
    }
}

compose.desktop {
    application {
        mainClass = "com.example.desktop.MainKt"

        // The app image is a self-contained folder with an .exe launcher, so it runs on a machine
        // without a JDK installed. An MSI needs the WiX toolset to be present on the build machine.
        nativeDistributions {
            targetFormats(TargetFormat.Exe, TargetFormat.Msi)
            packageName = "AudioDetectivePro"
            packageVersion = "1.0.0"
            description = "Forensic audio analysis, speech isolation and DSP filtering"
            vendor = "AudioDetective"
            windows {
                menu = true
                shortcut = true
                // A stable UUID lets a newer build replace an older installation.
                upgradeUuid = "3f0a1d2c-9b47-4f6e-8c31-5e2a7d4b6f10"
            }
        }
    }
}
