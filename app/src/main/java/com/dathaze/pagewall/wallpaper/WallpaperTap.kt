package com.dathaze.pagewall.wallpaper

/**
 * Whether a launcher's wallpaper-tap report should open the app.
 *
 * Android gives a live wallpaper one command for "the user touched the wallpaper",
 * [COMMAND_TAP], and says nothing about what kind of touch it was. Which gesture produces it is
 * entirely the launcher's choice, and launchers disagree: AOSP-derived ones — Sony's among them
 * — send it for a *single* tap on empty space, and send it again when a long press ends and the
 * finger comes up.
 *
 * This app used to open its setup screen whenever that command arrived, on the assumption it
 * meant a double tap. On a launcher that sends it for every tap, that made the home screen
 * unusable: one tap on empty space opened the app, and a long press ended by opening the app
 * over the launcher's own menu (issue #7, Sony Xperia 10 VII).
 *
 * There is no way to tell the gestures apart from this command, so the shortcut is off unless
 * somebody asks for it. A wallpaper that opens an app when you touch your home screen is a
 * surprise; one that does nothing is not.
 */
object WallpaperTap {

    /** `WallpaperManager.COMMAND_TAP`. The only thing the launcher tells us about a touch. */
    const val COMMAND_TAP = "android.wallpaper.tap"

    /**
     * Whether [action] should open the setup screen.
     *
     * @param enabled the user has asked for tap-to-open. Off by default, which is what makes an
     *   ordinary tap on an empty home screen do nothing at all.
     */
    fun opensApp(action: String?, enabled: Boolean): Boolean = enabled && action == COMMAND_TAP
}
