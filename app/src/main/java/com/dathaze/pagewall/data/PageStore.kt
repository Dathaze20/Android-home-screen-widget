package com.dathaze.pagewall.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the page -> media assignments.
 *
 * Lives in SharedPreferences so the wallpaper engine can read it synchronously on the render
 * thread without pulling in a coroutine-based store. The engine also registers a change listener,
 * so an edit made in the settings screen shows up on the home screen immediately.
 */
class PageStore(context: Context) {

    private val appContext = context.applicationContext
    private val prefs: SharedPreferences =
        appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    val mediaDir: File = File(appContext.filesDir, "pages").apply { mkdirs() }

    /**
     * How many home screen pages the launcher actually has, worked out by the engine from the
     * scroll step it is given. 0 until the wallpaper has run and been swiped at least once —
     * there is no API that reports this before then.
     */
    var detectedPageCount: Int
        get() = prefs.getInt(KEY_DETECTED_PAGES, 0)
        set(value) = prefs.edit().putInt(KEY_DETECTED_PAGES, value.coerceIn(0, MAX_PAGES)).apply()

    /** Set only when the user corrects the count by hand. 0 means "trust the detection". */
    var pageCountOverride: Int
        get() = prefs.getInt(KEY_PAGE_COUNT, 0)
        set(value) = prefs.edit().putInt(KEY_PAGE_COUNT, value.coerceIn(0, MAX_PAGES)).apply()

    /** The count to actually use: the user's correction, else what was detected, else a guess. */
    val pageCount: Int
        get() = when {
            pageCountOverride > 0 -> pageCountOverride
            detectedPageCount > 0 -> detectedPageCount
            else -> DEFAULT_PAGE_COUNT
        }.coerceIn(MIN_PAGES, MAX_PAGES)

    /** True once the count came from the launcher rather than from the fallback guess. */
    val pageCountIsDetected: Boolean
        get() = pageCountOverride <= 0 && detectedPageCount > 0

    /** Crossfade duration between pages, in milliseconds. 0 disables the fade. */
    var crossfadeMillis: Int
        get() = prefs.getInt(KEY_CROSSFADE, DEFAULT_CROSSFADE)
        set(value) = prefs.edit().putInt(KEY_CROSSFADE, value.coerceIn(0, MAX_CROSSFADE)).apply()

    /** Whether a still image should drift with the swipe (parallax) instead of sitting still. */
    var parallaxEnabled: Boolean
        get() = prefs.getBoolean(KEY_PARALLAX, true)
        set(value) = prefs.edit().putBoolean(KEY_PARALLAX, value).apply()

    /** Whether videos and GIFs animate. Off means they hold on their first frame, saving battery. */
    var motionEnabled: Boolean
        get() = prefs.getBoolean(KEY_MOTION, true)
        set(value) = prefs.edit().putBoolean(KEY_MOTION, value).apply()

    /** Whether a video's own soundtrack is audible. Off by default: silent video is the sane default. */
    var videoSoundEnabled: Boolean
        get() = prefs.getBoolean(KEY_VIDEO_SOUND, false)
        set(value) = prefs.edit().putBoolean(KEY_VIDEO_SOUND, value).apply()

    /** Master switch for per-page audio. Off by default: nobody wants surprise music. */
    var audioEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUDIO, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO, value).apply()

    /** Whether a page's track keeps looping while you stay on that page. */
    var audioLooping: Boolean
        get() = prefs.getBoolean(KEY_AUDIO_LOOP, false)
        set(value) = prefs.edit().putBoolean(KEY_AUDIO_LOOP, value).apply()

    /** Playback volume for per-page audio, 0f..1f. */
    var audioVolume: Float
        get() = prefs.getFloat(KEY_AUDIO_VOLUME, 0.5f)
        set(value) = prefs.edit().putFloat(KEY_AUDIO_VOLUME, value.coerceIn(0f, 1f)).apply()

    /**
     * The page the wallpaper is currently showing. Written by the engine, read by the widget so
     * a tap on the widget can assign media to the page you are actually looking at.
     */
    var currentPage: Int
        get() = prefs.getInt(KEY_CURRENT_PAGE, 0)
        set(value) = prefs.edit().putInt(KEY_CURRENT_PAGE, value).apply()

    /**
     * True once the launcher has reported a real scroll offset. Used to warn the user that
     * wallpaper scrolling is switched off in their launcher, which is the one setting that
     * stops this whole thing from working.
     */
    var sawScrollOffsets: Boolean
        get() = prefs.getBoolean(KEY_SAW_OFFSETS, false)
        set(value) = prefs.edit().putBoolean(KEY_SAW_OFFSETS, value).apply()

    /**
     * What the launcher has actually told the wallpaper, so a phone where pages never change can
     * say why instead of leaving everyone guessing. Written by the engine, read by the settings
     * screen.
     */
    var offsetEventCount: Int
        get() = prefs.getInt(KEY_OFFSET_EVENTS, 0)
        set(value) = prefs.edit().putInt(KEY_OFFSET_EVENTS, value).apply()

    /** The most recent horizontal offset, 0f..1f across the whole scroll range. */
    var lastOffset: Float
        get() = prefs.getFloat(KEY_LAST_OFFSET, -1f)
        set(value) = prefs.edit().putFloat(KEY_LAST_OFFSET, value).apply()

    /** The most recent scroll step. 0 means the launcher is reporting no pages to scroll. */
    var lastOffsetStep: Float
        get() = prefs.getFloat(KEY_LAST_STEP, -1f)
        set(value) = prefs.edit().putFloat(KEY_LAST_STEP, value).apply()

    /** Records one batch of offset diagnostics in a single write. */
    fun recordOffsets(count: Int, offset: Float, step: Float) {
        prefs.edit()
            .putInt(KEY_OFFSET_EVENTS, count)
            .putFloat(KEY_LAST_OFFSET, offset)
            .putFloat(KEY_LAST_STEP, step)
            .apply()
    }

    fun pages(): List<PageConfig> {
        val byIndex = readPages().associateBy { it.index }
        return (0 until pageCount).map { byIndex[it] ?: PageConfig(it) }
    }

    fun page(index: Int): PageConfig =
        readPages().firstOrNull { it.index == index } ?: PageConfig(index)

    /** Assigns media to a page. [posterFile] is the extracted still frame, for videos only. */
    fun setMedia(index: Int, fileName: String?, kind: MediaKind, posterFile: String? = null) {
        update(index) { it.copy(mediaFile = fileName, mediaKind = kind, posterFile = posterFile) }
    }

    /**
     * Assigns several pages in one write.
     *
     * Doing this as a loop of [setMedia] calls meant one read-modify-write of the whole page list
     * per photo, so any single failure mid-way left the rest of the batch unapplied. One write
     * either lands completely or not at all.
     */
    fun setMediaBatch(assignments: List<PageAssignment>) {
        if (assignments.isEmpty()) return
        val pages = readPages().toMutableList()
        assignments.forEach { assignment ->
            val position = pages.indexOfFirst { it.index == assignment.index }
            val old = if (position >= 0) pages[position] else null
            val updated = (old ?: PageConfig(assignment.index)).copy(
                mediaFile = assignment.fileName,
                mediaKind = assignment.kind,
                posterFile = assignment.posterFile,
            )
            if (old != null) {
                deleteIfReplaced(old.mediaFile, updated.mediaFile)
                deleteIfReplaced(old.posterFile, updated.posterFile)
                pages[position] = updated
            } else {
                pages.add(updated)
            }
        }
        writePages(pages)
    }

    fun setAudio(index: Int, fileName: String?, title: String?) {
        update(index) { it.copy(audioFile = fileName, audioTitle = title) }
    }

    /** Clears every slot for a page and deletes the files it owned. */
    fun clearPage(index: Int) {
        val existing = page(index)
        listOfNotNull(existing.mediaFile, existing.posterFile, existing.audioFile)
            .forEach { File(mediaDir, it).delete() }
        writePages(readPages().filterNot { it.index == index })
    }

    fun fileFor(name: String?): File? =
        name?.let { File(mediaDir, it) }?.takeIf { it.exists() }

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }

    private fun update(index: Int, transform: (PageConfig) -> PageConfig) {
        val pages = readPages().toMutableList()
        val position = pages.indexOfFirst { it.index == index }
        val updated = transform(if (position >= 0) pages[position] else PageConfig(index))
        if (position >= 0) {
            // Replacing a slot orphans the file it used to hold, so delete it as we go.
            val old = pages[position]
            deleteIfReplaced(old.mediaFile, updated.mediaFile)
            deleteIfReplaced(old.posterFile, updated.posterFile)
            deleteIfReplaced(old.audioFile, updated.audioFile)
            pages[position] = updated
        } else {
            pages.add(updated)
        }
        writePages(pages)
    }

    private fun deleteIfReplaced(old: String?, new: String?) {
        if (old != null && old != new) File(mediaDir, old).delete()
    }

    private fun readPages(): List<PageConfig> {
        val raw = prefs.getString(KEY_PAGES, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).map { i ->
                val obj = array.getJSONObject(i)
                PageConfig(
                    index = obj.getInt("index"),
                    // "image" is the pre-video key name, kept so an existing install is not lost.
                    mediaFile = obj.optString("media").takeIf { it.isNotEmpty() }
                        ?: obj.optString("image").takeIf { it.isNotEmpty() },
                    mediaKind = runCatching {
                        MediaKind.valueOf(obj.optString("kind", MediaKind.IMAGE.name))
                    }.getOrDefault(MediaKind.IMAGE),
                    posterFile = obj.optString("poster").takeIf { it.isNotEmpty() },
                    audioFile = obj.optString("audio").takeIf { it.isNotEmpty() },
                    audioTitle = obj.optString("audioTitle").takeIf { it.isNotEmpty() },
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun writePages(pages: List<PageConfig>) {
        val array = JSONArray()
        pages.sortedBy { it.index }.forEach { page ->
            array.put(
                JSONObject().apply {
                    put("index", page.index)
                    page.mediaFile?.let { put("media", it) }
                    put("kind", page.mediaKind.name)
                    page.posterFile?.let { put("poster", it) }
                    page.audioFile?.let { put("audio", it) }
                    page.audioTitle?.let { put("audioTitle", it) }
                }
            )
        }
        prefs.edit().putString(KEY_PAGES, array.toString()).apply()
    }

    companion object {
        const val PREFS_NAME = "pagewall"
        const val KEY_PAGES = "pages"
        const val KEY_PAGE_COUNT = "page_count"
        const val KEY_DETECTED_PAGES = "detected_pages"
        const val KEY_OFFSET_EVENTS = "offset_events"
        const val KEY_LAST_OFFSET = "last_offset"
        const val KEY_LAST_STEP = "last_step"
        const val KEY_CROSSFADE = "crossfade_ms"
        const val KEY_PARALLAX = "parallax"
        const val KEY_MOTION = "motion"
        const val KEY_VIDEO_SOUND = "video_sound"
        const val KEY_AUDIO = "audio_enabled"
        const val KEY_AUDIO_LOOP = "audio_loop"
        const val KEY_AUDIO_VOLUME = "audio_volume"
        const val KEY_CURRENT_PAGE = "current_page"
        const val KEY_SAW_OFFSETS = "saw_offsets"

        const val MIN_PAGES = 1
        const val MAX_PAGES = 12
        const val DEFAULT_PAGE_COUNT = 5
        const val DEFAULT_CROSSFADE = 350
        const val MAX_CROSSFADE = 2000
    }
}
