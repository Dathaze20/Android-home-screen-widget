plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// Releases are tagged v<major>.<minor>.<patch> and the tag is turned into a versionCode by the
// same rule the in-app updater uses to read it back: major * 10000 + minor * 100 + patch.
// Keeping the two in step is what lets the update button tell "newer" from "same".
// See UpdateVersion.codeFromTag and .github/workflows/release.yml.
val appVersionName: String = System.getenv("APP_VERSION_NAME") ?: "1.0.0"
val appVersionCode: Int = System.getenv("APP_VERSION_CODE")?.toIntOrNull() ?: 10000

android {
    namespace = "com.dathaze.pagewall"
    // Compiled against 37, still targeting 35. Compose BOM 2026.09 refuses to build against
    // anything lower — its AAR metadata says so outright. compileSdk only decides which APIs are
    // on the compile classpath; targetSdk is the one that changes how Android treats the app at
    // runtime, so it stays where it is. Raising that is a behaviour change and needs its own
    // release to be tested in.
    compileSdk = 37

    defaultConfig {
        applicationId = "com.dathaze.pagewall"
        // 28 is the floor for ImageDecoder/AnimatedImageDrawable, which is how GIFs
        // are animated. Every phone this targets is well past it.
        minSdk = 28
        targetSdk = 35
        versionCode = appVersionCode
        versionName = appVersionName

        // Which release asset belongs to this build. The product flavours override it; the
        // default is the personal build, which is what every release so far has been.
        buildConfigField("String", "UPDATE_ASSET_TAG", "\"personal\"")
    }

    signingConfigs {
        getByName("debug") {
            // Checked in on purpose. Without a fixed key every CI build is signed with a freshly
            // generated one, Android refuses to install it over the previous build, and the only
            // way to update is to uninstall first — which wipes every saved page assignment.
            // This is a debug key with the standard debug password: it is not a release secret.
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            // Signed with the same checked-in key every build has used. Without this the release
            // variant would be signed differently from what is already installed and Android
            // would refuse the update, forcing an uninstall — which wipes every page assignment.
            signingConfig = signingConfigs.getByName("debug")

            // Deliberately off, and not an oversight.
            //
            // PhotoFit, TouchCompatibility and MediaKind are persisted to SharedPreferences by
            // Enum.name and read back with valueOf. R8 is free to rename those constants, and a
            // renamed constant means valueOf throws on a value written by the previous build:
            // photo fit, Samsung compatibility and every page's media kind silently reset. The
            // saving from shrinking is real but it is not worth risking settings on a change
            // that cannot be tested here. If it is ever turned on it needs explicit keep rules
            // for com.dathaze.pagewall.data and its own release to be tested in.
            isMinifyEnabled = false
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
}

kotlin {
    // kotlinOptions is gone in the Kotlin 2.x Gradle plugin; this is the same setting.
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.exifinterface)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation(libs.junit)
}
