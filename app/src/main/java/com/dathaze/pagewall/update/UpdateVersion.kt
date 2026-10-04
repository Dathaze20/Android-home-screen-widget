package com.dathaze.pagewall.update

import java.util.Locale

/**
 * Turns a release tag into the same ascending integer the release workflow builds into the APK.
 *
 * Android decides whether an install is an upgrade by versionCode alone, so a tag has to map onto
 * that number by a rule both sides agree on. Keeping the rule here means it is not written out
 * twice and cannot drift between the workflow and the check.
 *
 * Mirrors the scheme already in use in the Music Player Tagger app: "1.4.2" becomes 10402.
 */
object UpdateVersion {

    private val TAG_PATTERN = Regex("""^v?(\d+)\.(\d+)\.(\d+)$""")

    /** The version code for [tag], or null when the tag is not of the form v1.2.3. */
    fun codeFromTag(tag: String?): Int? {
        val match = TAG_PATTERN.find((tag ?: "").trim()) ?: return null
        val (major, minor, patch) = match.destructured
        val m = major.toIntOrNull() ?: return null
        val mi = minor.toIntOrNull() ?: return null
        val p = patch.toIntOrNull() ?: return null
        // Minor and patch each get two digits, so 1.4.2 < 1.5.0 < 2.0.0 as integers.
        if (mi > 99 || p > 99) return null
        return m * 10000 + mi * 100 + p
    }

    /** True when [tag] names a release newer than [installedCode]. */
    fun isNewer(tag: String?, installedCode: Int): Boolean {
        val released = codeFromTag(tag) ?: return false
        return released > installedCode
    }
}

/** A release that has an installable APK attached. */
data class AvailableUpdate(
    val tag: String,
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkBytes: Long,
) {
    /** "14.4 MB", or empty when the API did not report a size. */
    val readableSize: String
        // Locale.US on purpose: the decimal separator has to be a dot whatever the phone's
        // language is set to, or UpdateVersionTest passes only on some machines.
        get() = if (apkBytes > 0) {
            String.format(Locale.US, "%.1f MB", apkBytes / 1048576.0)
        } else {
            ""
        }
}

/** Where a check ended up, so the UI never has to infer it from nulls. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val update: AvailableUpdate) : UpdateState
    data class Downloading(val percent: Int) : UpdateState

    /** The download finished and Android's installer has been handed the file. */
    data object ReadyToInstall : UpdateState

    /** Installing is blocked until the user allows this app to install apps. */
    data object NeedsInstallPermission : UpdateState
    data class Failed(val message: String) : UpdateState
}
