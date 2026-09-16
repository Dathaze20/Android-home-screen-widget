package com.dathaze.pagewall.wallpaper

import android.media.AudioAttributes
import android.media.MediaPlayer
import android.util.Log
import android.view.SurfaceHolder
import java.io.File

/**
 * Plays a video page by handing the wallpaper surface straight to [MediaPlayer].
 *
 * This is the one thing that cannot share the canvas path. A surface can be driven either by
 * `lockCanvas`/`unlockCanvasAndPost` or by a decoder, never both at once, so the engine treats a
 * video page as a separate mode: it stops drawing, gives the surface to MediaPlayer, and takes it
 * back when the user swipes to a page that is not a video.
 *
 * Because a video owns the surface, crossfading into or out of a video page is not possible; the
 * swap is a straight cut.
 */
class VideoPageController {

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MOVIE)
        .build()

    private var player: MediaPlayer? = null
    private var playingPath: String? = null

    /** MediaPlayer throws if start/pause is called before prepareAsync has finished. */
    private var prepared = false

    /** True while MediaPlayer owns the surface, meaning the canvas must be left alone. */
    val isActive: Boolean get() = player != null

    /**
     * Makes sure [file] is looping on [holder]. Calling it again with the same file resumes rather
     * than restarting, so a redraw during a swipe does not jump the clip back to its first frame.
     */
    fun ensurePlaying(file: File, holder: SurfaceHolder, soundEnabled: Boolean) {
        if (playingPath == file.absolutePath && player != null) {
            resume()
            return
        }
        stop()

        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDisplay(holder)
                setDataSource(file.absolutePath)
                isLooping = true
                val volume = if (soundEnabled) 1f else 0f
                setVolume(volume, volume)
                setOnPreparedListener { ready ->
                    prepared = true
                    // Center-crop, matching how still photos are drawn. Without this the video is
                    // stretched to the screen's aspect ratio, which mangles anything landscape.
                    runCatching {
                        ready.setVideoScalingMode(
                            MediaPlayer.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
                        )
                    }
                    runCatching { ready.start() }
                }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "Video error what=$what extra=$extra for ${file.name}")
                    // Qualified: a bare stop() here would hit MediaPlayer.stop(), leaving this
                    // controller believing it still owns the surface.
                    this@VideoPageController.stop()
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            Log.w(TAG, "Could not open ${file.name}", it)
            return
        }

        player = created
        playingPath = file.absolutePath
    }

    /** Freezes on the current frame; the surface stays ours, so no repaint is needed. */
    fun pause() {
        if (!prepared) return
        runCatching { player?.takeIf { it.isPlaying }?.pause() }
    }

    /** Resumes a paused clip. A clip still preparing starts itself, so this leaves it alone. */
    fun resume() {
        if (!prepared) return
        runCatching { player?.takeIf { !it.isPlaying }?.start() }
    }

    /** Releases the surface back to the engine so it can draw again. */
    fun stop() {
        player?.let { active ->
            runCatching { if (active.isPlaying) active.stop() }
            runCatching { active.setDisplay(null) }
            runCatching { active.release() }
        }
        player = null
        playingPath = null
        prepared = false
    }

    private companion object {
        const val TAG = "VideoPage"
    }
}
