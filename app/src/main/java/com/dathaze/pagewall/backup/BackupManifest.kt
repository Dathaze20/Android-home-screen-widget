package com.dathaze.pagewall.backup

import org.json.JSONArray
import org.json.JSONObject

/**
 * What a backup says it contains.
 *
 * Deliberately free of Android imports so the whole format — writing it, reading it back, and
 * deciding whether a file is trustworthy — is covered by ordinary unit tests rather than only
 * ever exercised on a phone holding the only copy of someone's pictures.
 *
 * The format is portable between the personal and public builds on purpose. Nothing in it names
 * an application ID except [appId], which is recorded for information and never used to decide
 * whether a restore may proceed, so a backup taken from one build restores into the other.
 */
data class BackupManifest(
    val version: Int,
    val exported: String,
    val appId: String,
    val appVersion: String,
    val pageCount: Int,
    val pages: List<BackupPage>,
    val settings: Map<String, String>,
    val files: List<BackupEntry>,
) {
    /** Files the pages actually refer to, which is what a restore needs to find. */
    fun referencedFiles(): Set<String> = pages.flatMap {
        listOfNotNull(it.mediaFile, it.posterFile, it.audioFile)
    }.toSet()

    fun toJson(): String = JSONObject().apply {
        put(KEY_VERSION, version)
        put(KEY_EXPORTED, exported)
        put(KEY_APP_ID, appId)
        put(KEY_APP_VERSION, appVersion)
        put(KEY_PAGE_COUNT, pageCount)
        put(KEY_PAGES, JSONArray().apply { pages.forEach { put(it.toJson()) } })
        put(KEY_SETTINGS, JSONObject().apply { settings.forEach { (k, v) -> put(k, v) } })
        put(KEY_FILES, JSONArray().apply { files.forEach { put(it.toJson()) } })
    }.toString(2)

    companion object {
        /** Bumped only for a change a previous version could not read correctly. */
        const val CURRENT_VERSION = 1

        private const val KEY_VERSION = "version"
        private const val KEY_EXPORTED = "exported"
        private const val KEY_APP_ID = "appId"
        private const val KEY_APP_VERSION = "appVersion"
        private const val KEY_PAGE_COUNT = "pageCount"
        private const val KEY_PAGES = "pages"
        private const val KEY_SETTINGS = "settings"
        private const val KEY_FILES = "files"

        /**
         * Parses [json], or returns null when it is not a backup this build understands.
         *
         * Null rather than an exception because every caller's answer to "this is not a backup"
         * is the same, and because the input is a file the user picked out of a file manager:
         * being handed a photo or a half-downloaded zip is ordinary, not exceptional.
         */
        fun parse(json: String): BackupManifest? = runCatching {
            val root = JSONObject(json)
            val version = root.getInt(KEY_VERSION)
            if (version !in 1..CURRENT_VERSION) return null

            val pages = root.optJSONArray(KEY_PAGES).orEmpty().mapNotNull { BackupPage.parse(it) }
            val files = root.optJSONArray(KEY_FILES).orEmpty().mapNotNull { BackupEntry.parse(it) }

            val settingsJson = root.optJSONObject(KEY_SETTINGS) ?: JSONObject()
            val settings = settingsJson.keys().asSequence()
                .mapNotNull { key -> settingsJson.optString(key).takeIf { it.isNotEmpty() }?.let { key to it } }
                .toMap()

            BackupManifest(
                version = version,
                exported = root.optString(KEY_EXPORTED),
                appId = root.optString(KEY_APP_ID),
                appVersion = root.optString(KEY_APP_VERSION),
                pageCount = root.optInt(KEY_PAGE_COUNT, pages.size),
                pages = pages,
                settings = settings,
                files = files,
            ).takeIf { it.isSelfConsistent() }
        }.getOrNull()

        private fun JSONArray?.orEmpty(): List<JSONObject> =
            if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
    }
}

/**
 * True when the manifest does not contradict itself.
 *
 * A manifest that parses is not yet a manifest worth acting on: page indices have to be distinct
 * and non-negative, and every file a page refers to has to be declared in [BackupManifest.files].
 * A backup naming a picture it does not carry is incomplete, and incomplete has to fail here
 * rather than halfway through a restore.
 */
internal fun BackupManifest.isSelfConsistent(): Boolean {
    if (pages.any { it.index < 0 }) return false
    if (pages.map { it.index }.toSet().size != pages.size) return false
    if (files.any { !it.isWellFormed() }) return false
    if (files.map { it.name }.toSet().size != files.size) return false
    val declared = files.map { it.name }.toSet()
    return referencedFiles().all { it in declared }
}

/** One page's assignment, by file name rather than by path. */
data class BackupPage(
    val index: Int,
    val mediaFile: String? = null,
    val mediaKind: String = "IMAGE",
    val posterFile: String? = null,
    val audioFile: String? = null,
    val audioTitle: String? = null,
) {
    val hasMedia: Boolean get() = mediaFile != null

    fun toJson(): JSONObject = JSONObject().apply {
        put("index", index)
        mediaFile?.let { put("mediaFile", it) }
        put("mediaKind", mediaKind)
        posterFile?.let { put("posterFile", it) }
        audioFile?.let { put("audioFile", it) }
        audioTitle?.let { put("audioTitle", it) }
    }

    companion object {
        fun parse(o: JSONObject): BackupPage? {
            if (!o.has("index")) return null
            return BackupPage(
                index = o.optInt("index", -1),
                mediaFile = o.optString("mediaFile").takeIf { it.isNotEmpty() },
                mediaKind = o.optString("mediaKind").takeIf { it.isNotEmpty() } ?: "IMAGE",
                posterFile = o.optString("posterFile").takeIf { it.isNotEmpty() },
                audioFile = o.optString("audioFile").takeIf { it.isNotEmpty() },
                audioTitle = o.optString("audioTitle").takeIf { it.isNotEmpty() },
            )
        }
    }
}

/** One file carried in the archive, with what it should weigh and hash to. */
data class BackupEntry(
    val name: String,
    val sizeBytes: Long,
    val sha256: String,
) {
    /** Where this file lives inside the zip. */
    val path: String get() = "$MEDIA_PREFIX$name"

    fun toJson(): JSONObject = JSONObject().apply {
        put("name", name)
        put("size", sizeBytes)
        put("sha256", sha256)
    }

    companion object {
        const val MEDIA_PREFIX = "media/"
        private val HEX = Regex("^[0-9a-f]{64}$")

        fun parse(o: JSONObject): BackupEntry? {
            val name = o.optString("name").takeIf { it.isNotEmpty() } ?: return null
            return BackupEntry(name, o.optLong("size", -1), o.optString("sha256").lowercase())
        }

        /** A file name that cannot escape the directory it is extracted into. */
        fun isSafeName(name: String): Boolean =
            name.isNotEmpty() &&
                !name.contains('/') &&
                !name.contains('\\') &&
                name != "." &&
                name != ".." &&
                !name.startsWith(".")
    }

    /**
     * A zip is an untrusted file picked from a file manager, so a name is checked for traversal
     * before it is ever joined to a path, and a hash that is not a hash means nothing can be
     * verified against it.
     */
    fun isWellFormed(): Boolean =
        isSafeName(name) && sizeBytes >= 0 && HEX.matches(sha256)
}
