package com.dathaze.pagewall.data

/**
 * Everything the wallpaper needs to know about a single home screen page.
 *
 * [imageFile] and [audioFile] are file names inside the app's private storage, not content
 * URIs: the picked media is copied in on import so a page keeps working after the user
 * deletes the original from their gallery or Android revokes the URI grant.
 */
data class PageConfig(
    val index: Int,
    val imageFile: String? = null,
    val audioFile: String? = null,
    val audioTitle: String? = null,
) {
    val hasImage: Boolean get() = imageFile != null
    val hasAudio: Boolean get() = audioFile != null
}
