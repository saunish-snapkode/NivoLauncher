import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.snapkode.nivolauncher"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.snapkode.nivolauncher"
        minSdk = 24
        targetSdk = 37
        // CI passes -PversionCode / -PversionName (from the run number and the release tag); local builds keep the defaults.
        versionCode = (project.findProperty("versionCode") as String?)?.toIntOrNull() ?: 1
        versionName = (project.findProperty("versionName") as String?)?.takeIf { it.isNotBlank() } ?: "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // MET Norway requires an identifying User-Agent with real contact details. Put `met.contact=you@example.com` (or a
        // URL) in the git-ignored local.properties; it is baked into the build and sent only to api.met.no.
        val localProps = Properties().apply {
            rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
        }
        buildConfigField("String", "MET_CONTACT", "\"${localProps.getProperty("met.contact", "").replace("\"", "")}\"")
    }

    // Release signing comes from keystore.properties (git-ignored) so no secrets live in the repo.
    val keystoreProps = Properties().apply {
        rootProject.file("keystore.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfigs.findByName("release")?.let { signingConfig = it }
            optimization {
                enable = true
                packageScope = setOf("androidx.**", "kotlin.**", "kotlinx.**")
            }
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.icons)
    implementation(libs.androidx.compose.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.core.ktx)
    // Steps from Health Connect (read-only). The library needs API 26; every call is guarded and the manifest overrides it.
    implementation("androidx.health.connect:connect-client:1.1.0-alpha10")
    // Installs the baseline profile (src/main/baseline-prof.txt) so the first launch runs precompiled, not interpreted.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    testImplementation(libs.junit)
    // A real org.json for JVM tests: the Android stub jar throws "not mocked".
    testImplementation("org.json:json:20240303")
    // A real XML pull parser for JVM tests (on a phone the platform provides one).
    testImplementation("net.sf.kxml:kxml2:2.3.0")
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}