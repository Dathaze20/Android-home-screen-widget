package com.dathaze.pagewall.backup

import com.dathaze.pagewall.data.PageStore

/**
 * Which preferences belong in a backup, and how they survive the trip.
 *
 * A deliberate allow-list rather than "everything in SharedPreferences". Most of what the app
 * stores is a reading taken from the phone it is running on — how many offset reports this
 * launcher has sent, which page the engine last drew, how many touches it saw. Restoring those
 * onto another phone, or onto the same phone months later, would be restoring somebody's
 * diagnostics as if they were settings. Only choices a person actually made are carried.
 *
 * Page assignments are not here either. They travel as structure in [BackupManifest.pages] so a
 * restore can merge them page by page, rather than as one opaque blob that could only be
 * restored by overwriting the lot.
 */
object BackupSettings {

    /** Preferences that represent a choice, and nothing that represents a measurement. */
    val KEYS: List<String> = listOf(
        PageStore.KEY_PAGE_COUNT,
        PageStore.KEY_CROSSFADE,
        PageStore.KEY_PARALLAX,
        PageStore.KEY_MOTION,
        PageStore.KEY_VIDEO_SOUND,
        PageStore.KEY_AUDIO,
        PageStore.KEY_AUDIO_LOOP,
        PageStore.KEY_AUDIO_VOLUME,
        PageStore.KEY_PHOTO_FIT,
        PageStore.KEY_TOUCH_MODE,
        PageStore.KEY_DEFAULT_HOME,
        PageStore.KEY_SYNC_HOME,
        PageStore.KEY_HOME_JUMP,
        PageStore.KEY_CALIBRATION_MIN,
        PageStore.KEY_CALIBRATION_MAX,
        PageStore.KEY_ONBOARDED,
    )

    /**
     * Encodes a preference value with its type.
     *
     * SharedPreferences is typed, and reading an Int back out of a slot written as a String
     * throws. The prefix keeps the type with the value so a restore puts each one back as what
     * it was, and keeps the file readable by eye.
     */
    fun encode(value: Any?): String? = when (value) {
        is Boolean -> "b:$value"
        is Int -> "i:$value"
        is Long -> "l:$value"
        // Float.toString is locale-independent; "%f" would write 0,5 on a French phone and the
        // backup would stop being portable.
        is Float -> "f:${value.toString()}"
        is String -> "s:$value"
        else -> null
    }

    /** Decodes what [encode] wrote, or null if it is not something this build recognises. */
    fun decode(encoded: String): Any? {
        if (encoded.length < 2 || encoded[1] != ':') return null
        val body = encoded.substring(2)
        return when (encoded[0]) {
            'b' -> body.toBooleanStrictOrNull()
            'i' -> body.toIntOrNull()
            'l' -> body.toLongOrNull()
            'f' -> body.toFloatOrNull()
            's' -> body
            else -> null
        }
    }

    /** Drops anything not on the allow-list, so a hand-edited backup cannot set arbitrary keys. */
    fun sanitise(settings: Map<String, String>): Map<String, Any> = settings
        .filterKeys { it in KEYS }
        .mapNotNull { (key, raw) -> decode(raw)?.let { key to it } }
        .toMap()
}
