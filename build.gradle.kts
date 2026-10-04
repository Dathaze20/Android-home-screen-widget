// No kotlin.android here. Since AGP 9 the Android plugin compiles Kotlin itself, and applying
// the standalone plugin on top of it fails outright rather than being merely redundant.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
}
