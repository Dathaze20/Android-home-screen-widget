package com.dathaze.pagewall.wallpaper

/**
 * Whether the engine is in a state where drawing a frame makes sense.
 *
 * Three separate conditions, kept in one tested place because they used to be spelled out
 * separately at each of the four call sites — and that is how they drifted. "The wallpaper is
 * hidden" and "the surface does not exist" are different facts with different lifetimes, and
 * conflating them cost the engine its ability to come back: [onSurfaceDestroyed] cleared the
 * visibility flag, nothing restored it when the surface returned, and every draw path stayed
 * shut until the wallpaper happened to be hidden and shown again.
 */
object FrameGate {

    /**
     * Whether the engine may touch the surface at all — to draw on it, or to hand it to the
     * video player. Deciding which of those to do is exactly what the renderer does when it is
     * already in video mode, so this question cannot include [canDraw]'s video check.
     *
     * @param visible the wallpaper is on screen, as Android last reported
     * @param surfaceAlive a surface exists; false between destroy and create
     */
    fun hasSurface(visible: Boolean, surfaceAlive: Boolean): Boolean = visible && surfaceAlive

    /**
     * Whether the canvas may be locked and painted.
     *
     * @param videoMode MediaPlayer owns the surface, and a surface has one owner at a time
     */
    fun canDraw(visible: Boolean, surfaceAlive: Boolean, videoMode: Boolean): Boolean =
        hasSurface(visible, surfaceAlive) && !videoMode
}
