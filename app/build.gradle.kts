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

// The private key for the public build, supplied by CI from GitHub Secrets and never checked in.
// Absent on any ordinary checkout, which is deliberate: the public flavour then simply goes
// unsigned, rather than the whole build failing for everyone who does not hold the key. The
// release workflow refuses to publish without it — see .github/workflows/release.yml.
val releaseKeystore: File? = System.getenv("RELEASE_KEYSTORE_PATH")
    ?.let { rootProject.file(it) }
    ?.takeIf { it.exists() }

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
        // Only configured when CI has supplied the key. Referenced by the public flavour below,
        // and only when it actually exists.
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }

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

    // One codebase, two identities — but only one of them is distributed.
    //
    // The public build is the product: its own applicationId, and a private key, because on
    // Android the signing key is the app's identity and the debug one here is published in this
    // repository — anyone could sign an APK that Android would accept as an update to it. It is
    // the only build attached to a release.
    //
    // The personal flavour stays for the installs that predate the split and for working on the
    // app without the release key. It is no longer published. The two install side by side and
    // neither can update the other, which is why moving between them goes through a backup.
    flavorDimensions += "distribution"
    productFlavors {
        create("personal") {
            dimension = "distribution"
            signingConfig = signingConfigs.getByName("debug")
            buildConfigField("String", "UPDATE_ASSET_TAG", "\"personal\"")
        }
        create("public") {
            dimension = "distribution"
            applicationId = "io.github.dathaze20.pagewallpaper"
            buildConfigField("String", "UPDATE_ASSET_TAG", "\"public\"")
            // Left unsigned when the key is absent, so a checkout without the secrets still
            // builds everything else instead of failing at configuration time.
            if (releaseKeystore != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildTypes {
        debug {
            // A debug build is never the official app, and must not be able to pose as it.
            //
            // Without this, a CI branch build of the public flavour carries the real
            // applicationId but the debug signing key. Installing one would occupy the official
            // package name with the wrong signature: the release APK could then not be installed
            // over it at all, and the only way out would be uninstalling and losing every saved
            // page. The suffix makes a development build a separate app that sits beside the
            // real one. Release builds are untouched, so what is published keeps its identity.
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }

        release {
            // Signing is set per flavour above, not here: a build type's signingConfig would
            // override both flavours and hand the public build the published debug key.

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
    // The real org.json. Android ships it, but the unit-test classpath has only stubs that
    // throw, so without this the backup manifest could not be tested off a device — and the
    // manifest is exactly the part that must not be discovered to be wrong on a phone holding
    // the only copy of someone's pictures.
    testImplementation(libs.json)
}
