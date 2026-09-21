package com.dathaze.pagewall.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.util.Log
import java.io.File

/**
 * Plays the track attached to the page you are standing on.
 *
 * Deliberately scoped to "while the home screen wallpaper is visible": it starts when a page
 * with a track becomes current and stops the moment the wallpaper is hidden (app opened, screen
 * off). That keeps this out of foreground-service territory entirely, and matches what a theme
 * per page should feel like — a cue when you land, not a background player.
 *
 * It also stands down whenever something else is already playing, so swiping home during a
 * podcast does not start a fight over the speaker.
 */
class PageAudioController(context: Context) {

    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private val attributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_MEDIA)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
        .build()

    private var player: MediaPlayer? = null
    private var focusRequest: AudioFocusRequest? = null
    private var playingFile: String? = null

    /** The volume the user chose, so it can be restored after a duck. */
    private var requestedVolume = 1f

    private val focusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> stop()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> player?.setVolume(DUCKED, DUCKED)
            // Without this the track stays ducked forever after the interruption ends.
            AudioManager.AUDIOFOCUS_GAIN ->
                player?.setVolume(requestedVolume, requestedVolume)
        }
    }

    /** Starts [file] unless it is already the playing track or another app owns the speaker. */
    fun play(file: File, volume: Float, looping: Boolean) {
        requestedVolume = volume
        // Already playing this track: apply the new settings to the live player rather than
        // returning early and leaving a stale volume or loop flag in place.
        if (playingFile == file.absolutePath && player?.isPlaying == true) {
            runCatching {
                player?.setVolume(volume, volume)
                player?.isLooping = looping
            }
            return
        }
        if (isSomeoneElsePlaying()) return

        stop()
        if (!requestFocus()) return

        val created = runCatching {
            MediaPlayer().apply {
                setAudioAttributes(attributes)
                setDataSource(file.absolutePath)
                isLooping = looping
                setVolume(volume, volume)
                setOnPreparedListener { it.start() }
                // Qualified: a bare stop() inside this apply block would hit MediaPlayer.stop()
                // rather than releasing the player and the audio focus.
                setOnCompletionListener { if (!looping) this@PageAudioController.stop() }
                setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "MediaPlayer error what=$what extra=$extra")
                    this@PageAudioController.stop()
                    true
                }
                prepareAsync()
            }
        }.getOrElse {
            Log.w(TAG, "Could not start ${file.name}", it)
            abandonFocus()
            return
        }

        player = created
        playingFile = file.absolutePath
    }

    fun stop() {
        player?.let { active ->
            runCatching {
                if (active.isPlaying) active.stop()
            }
            active.release()
        }
        player = null
        playingFile = null
        abandonFocus()
    }

    /** True when audio is coming out of some other app, which we never interrupt. */
    private fun isSomeoneElsePlaying(): Boolean =
        audioManager.isMusicActive && player == null

    private fun requestFocus(): Boolean {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
            .setAudioAttributes(attributes)
            .setOnAudioFocusChangeListener(focusListener)
            .setWillPauseWhenDucked(false)
            .build()
        focusRequest = request
        return audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonFocus() {
        focusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
        focusRequest = null
    }

    private companion object {
        const val TAG = "PageAudio"
        const val DUCKED = 0.2f
    }
}
