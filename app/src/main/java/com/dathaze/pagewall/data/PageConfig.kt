package com.dathaze.pagewall.data

/** What kind of thing is assigned to a page. Each kind is drawn a different way. */
enum class MediaKind {
    /** A still photo. Decoded once, drawn to the wallpaper canvas. */
    IMAGE,

    /** An animated GIF or animated WebP. Decoded as an animated drawable and looped on canvas. */
    GIF,

    /** A video clip. Handed to MediaPlayer, which renders straight onto the wallpaper surface. */
    VIDEO;

    companion object {
        /** Maps a content MIME type onto a kind, defaulting to a still image. */
        fun fromMimeType(mime: String?): MediaKind = when {
            mime == null -> IMAGE
            mime.startsWith("video/") -> VIDEO
            mime == "image/gif" -> GIF
            mime == "image/webp" -> GIF // May be animated; the decoder settles it either way.
            else -> IMAGE
        }
    }
}

/**
 * Everything the wallpaper needs to know about a single home screen page.
 *
 * File names refer to the app's private storage, not content URIs: picked media is copied in on
 * import so a page keeps working after the original is deleted from the gallery or Android
 * revokes the temporary URI grant.
 *
 * [posterFile] is a still frame saved for videos, so the settings screen and the widget have
 * something to show without spinning up a decoder.
 */
data class PageConfig(
    val index: Int,
    val mediaFile: String? = null,
    val mediaKind: MediaKind = MediaKind.IMAGE,
    val posterFile: String? = null,
    val audioFile: String? = null,
    val audioTitle: String? = null,
) {
    val hasMedia: Boolean get() = mediaFile != null
    val hasAudio: Boolean get() = audioFile != null

    /** The file to show as a thumbnail: the poster frame for video, the media itself otherwise. */
    val thumbnailFile: String? get() = posterFile ?: mediaFile

    /** Short human label for the settings screen and the widget. */
    val kindLabel: String
        get() = when {
            mediaFile == null -> "Nothing yet"
            mediaKind == MediaKind.VIDEO -> "Video"
            mediaKind == MediaKind.GIF -> "GIF"
            else -> "Photo"
        }
}

/** One page's worth of a bulk assignment, applied together by [PageStore.setMediaBatch]. */
data class PageAssignment(
    val index: Int,
    val fileName: String,
    val kind: MediaKind,
    val posterFile: String?,
)
