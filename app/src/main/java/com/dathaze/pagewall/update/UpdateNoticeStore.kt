package com.dathaze.pagewall.update

import android.content.Context
import android.content.SharedPreferences

/**
 * What the quiet update check remembers between launches.
 *
 * Deliberately its own SharedPreferences file rather than a corner of the page store: none
 * of this is the user's data. It is a cached answer from GitHub plus two timestamps, it is
 * worth nothing once the app has been updated, and it has no business travelling in a backup.
 * Keeping it separate means a restore can never put a stale release notice back on the screen.
 *
 * Remembering the answer is what keeps this to one request a day, and it is also what makes the
 * notice work with no connection at all: the strip and the indicator come back on the next
 * launch from what is stored here, without asking anything.
 */
class UpdateNoticeStore(context: Context) {

    private val prefs: SharedPreferences = context.applicationContext
        .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    /** When GitHub was last given a settled answer, or 0 if it never has been. */
    var lastCheckedAt: Long
        get() = prefs.getLong(KEY_CHECKED_AT, 0L)
        set(value) = prefs.edit().putLong(KEY_CHECKED_AT, value).apply()

    /** The version a dismissal was about, or null if nothing has been dismissed. */
    val snoozedTag: String?
        get() = prefs.getString(KEY_SNOOZED_TAG, null)

    /** When that dismissal happened. */
    val snoozedAt: Long
        get() = prefs.getLong(KEY_SNOOZED_AT, 0L)

    /**
     * The release last found, or null if none is remembered.
     *
     * A record missing its tag or its download URL is treated as absent rather than patched up:
     * there is nothing to offer without both, and half an answer is how a button that does
     * nothing gets drawn.
     */
    fun cached(): AvailableUpdate? {
        val tag = prefs.getString(KEY_TAG, null)?.takeIf { it.isNotEmpty() } ?: return null
        val url = prefs.getString(KEY_APK_URL, null)?.takeIf { it.isNotEmpty() } ?: return null
        return AvailableUpdate(
            tag = tag,
            versionName = prefs.getString(KEY_VERSION_NAME, null) ?: tag.removePrefix("v"),
            notes = prefs.getString(KEY_NOTES, null) ?: "",
            apkUrl = url,
            apkName = prefs.getString(KEY_APK_NAME, null)?.takeIf { it.isNotEmpty() }
                ?: "page-wallpaper-update.apk",
            apkBytes = prefs.getLong(KEY_APK_BYTES, 0L),
        )
    }

    /** Stores [update] so the next launch inside the window needs no request. */
    fun remember(update: AvailableUpdate) {
        prefs.edit()
            .putString(KEY_TAG, update.tag)
            .putString(KEY_VERSION_NAME, update.versionName)
            .putString(KEY_NOTES, update.notes)
            .putString(KEY_APK_URL, update.apkUrl)
            .putString(KEY_APK_NAME, update.apkName)
            .putLong(KEY_APK_BYTES, update.apkBytes)
            .apply()
    }

    /** Throws the remembered release away, e.g. once the phone is on it. */
    fun forget() {
        prefs.edit()
            .remove(KEY_TAG)
            .remove(KEY_VERSION_NAME)
            .remove(KEY_NOTES)
            .remove(KEY_APK_URL)
            .remove(KEY_APK_NAME)
            .remove(KEY_APK_BYTES)
            .apply()
    }

    /** Records that [tag] was dismissed at [at], which holds it back for a day. */
    fun snooze(tag: String, at: Long) {
        prefs.edit()
            .putString(KEY_SNOOZED_TAG, tag)
            .putLong(KEY_SNOOZED_AT, at)
            .apply()
    }

    private companion object {
        const val PREFS_NAME = "update_notice"
        const val KEY_CHECKED_AT = "checked_at"
        const val KEY_TAG = "tag"
        const val KEY_VERSION_NAME = "version_name"
        const val KEY_NOTES = "notes"
        const val KEY_APK_URL = "apk_url"
        const val KEY_APK_NAME = "apk_name"
        const val KEY_APK_BYTES = "apk_bytes"
        const val KEY_SNOOZED_TAG = "snoozed_tag"
        const val KEY_SNOOZED_AT = "snoozed_at"
    }
}
