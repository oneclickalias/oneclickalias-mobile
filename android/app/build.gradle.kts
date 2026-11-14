import java.util.zip.ZipFile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ktlint)
}

/**
 * Reads a base URL from a Gradle property (`-PocaApiBase=...`), falls back to
 * the production origin and fails the build early on malformed values.
 */
fun baseUrlProperty(name: String, default: String): String {
    val value = providers.gradleProperty(name).getOrElse(default).trim()
    require(Regex("^https?://[^/\\s]+$").matches(value)) {
        "Gradle property '$name' must be an origin like https://example.com " +
            "(http or https, no path, no trailing slash), got '$value'"
    }
    return value
}

val productionOrigin = "https://oneclickalias.com"

/** Generated API models (`api/kotlin/` in the repo root, see api/README.md). */
val generatedApiModels: File = rootProject.file("../api/kotlin")

android {
    namespace = "com.oneclickalias.app"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.oneclickalias.app"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Endpoints are compiled in, like the browser extension's OCA_*_BASE defines.
        buildConfigField("String", "API_BASE", "\"${baseUrlProperty("ocaApiBase", productionOrigin)}\"")
        buildConfigField("String", "AUTH_BASE", "\"${baseUrlProperty("ocaAuthBase", productionOrigin)}\"")
        buildConfigField("String", "WEB_BASE", "\"${baseUrlProperty("ocaWebBase", productionOrigin)}\"")
    }

    // English UI on every device: drop the translations that libraries
    // bring along (e.g. Material's screen-reader texts), so nothing switches language.
    androidResources {
        localeFilters += "en"
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    // Reproducible builds for F-Droid: no Google-signed dependency metadata blob.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all {
                // Robolectric touches FileDescriptor internals on SDK 36+ images.
                it.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
            }
        }
    }

    lint {
        abortOnError = true
        warningsAsErrors = true
        checkDependencies = true
        // Version-freshness checks depend on the day the build runs, not on the code.
        disable += setOf("GradleDependency", "AndroidGradlePluginVersion", "NewerVersionAvailable")
    }
}

ktlint {
    version.set(libs.versions.ktlintCli)
}

// API models generated from the OpenAPI specs, copied by scripts/sync-api-models.sh. Compiled
// straight from the repo's api/ folder, never copied into android/. Added through the
// variant API on purpose: the ktlint plugin only sees the DSL source sets, so
// ktlintCheck/ktlintFormat leave the generated files exactly as generated.
androidComponents {
    onVariants { variant ->
        variant.sources.kotlin?.addStaticSourceDirectory(generatedApiModels.path)
    }
}

// QR login: the adb hook for automated device tests (src/debug, "DebugScan…") must never
// reach a release APK. Runs after every assembleRelease and fails it if any file in the
// APK (dex, manifest) names the hook.
val checkReleaseHasNoScanHook = tasks.register("checkReleaseHasNoScanHook") {
    group = "verification"
    description = "Fails if the release APK contains the debug-only QR scan hook."
    val apk = layout.buildDirectory.file("outputs/apk/release/app-release-unsigned.apk")
    inputs.file(apk)
    doLast {
        val marker = "DebugScan"
        val patterns = listOf(marker.toByteArray(Charsets.UTF_8), marker.toByteArray(Charsets.UTF_16LE))
        fun ByteArray.contains(pattern: ByteArray): Boolean = (0..size - pattern.size).any { start ->
            pattern.indices.all { this[start + it] == pattern[it] }
        }
        ZipFile(apk.get().asFile).use { zip ->
            val hits = zip.entries().asSequence()
                .filter { entry ->
                    zip.getInputStream(entry).use { it.readBytes() }.let { b -> patterns.any { b.contains(it) } }
                }
                .map { it.name }
                .toList()
            if (hits.isNotEmpty()) throw GradleException("Release APK contains the debug scan hook in: $hits")
        }
        println("checkReleaseHasNoScanHook: no '$marker' in ${apk.get().asFile.name}")
    }
}
tasks.matching { it.name == "assembleRelease" }.configureEach { finalizedBy(checkReleaseHasNoScanHook) }

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.datastore.preferences)
    // Delivers copied aliases to the server once online (ready aliases), no Play Services.
    implementation(libs.androidx.work.runtime)
    implementation(libs.androidx.biometric)
    // Biometric 1.1.0 asks for fragment 1.2.5, whose FragmentActivity crashes on the
    // Activity Result API of the current activity library; use a current fragment.
    implementation(libs.androidx.fragment)
    // QR login: CameraX preview + ImageAnalysis, decoded on the device with ZXing core.
    implementation(libs.androidx.camera.core)
    implementation(libs.androidx.camera.camera2)
    implementation(libs.androidx.camera.lifecycle)
    implementation(libs.androidx.camera.compose)
    implementation(libs.zxing.core)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    debugImplementation(libs.compose.ui.tooling)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    testImplementation(libs.androidx.work.testing)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)
    testImplementation(libs.androidx.test.espresso.core)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
    debugImplementation(libs.compose.ui.test.manifest)

    // Instrumented tests on a device (Android Keystore): ./gradlew connectedDebugAndroidTest
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
}
