package com.dathaze.pagewall.update

/**
 * The rules behind the quiet update notice: when to ask, what to remember, and what to say.
 *
 * All of it is pure, because every one of these decisions is about a clock — a day since the last
 * request, a day since a dismissal — and testing a clock by waiting for it is not testing it. The
 * Android-shaped parts live in [UpdateNoticeStore] (what is remembered between launches) and in
 * the banner itself (how it looks).
 *
 * Ported from the same feature in the Music Player Tagger app, which asks GitHub a few seconds
 * after it opens and says so in a strip at the bottom of its own screen. Nothing here downloads
 * or installs anything: the notice leads to the update row that has always done that, where the
 * download still starts on a press.
 */
object UpdateNotice {

    /** At most one automatic request a day. A release is not news that goes stale in an hour. */
    const val CHECK_EVERY_MS = 24L * 60 * 60 * 1000

    /** Dismissing means "later", not "never": that version is held back for a day. */
    const val SNOOZE_MS = 24L * 60 * 60 * 1000

    /**
     * How long after the app opens the check is made.
     *
     * Well clear of the launch on purpose. Opening the app already reads the pages, decodes the
     * thumbnails and talks to the wallpaper service; a network call thrown into that is the one
     * that makes the first frame late.
     */
    const val FIRST_DELAY_MS = 5_000L

    /** Said next to the version on the strip, and the only promise it makes. */
    private const val KEEPS_EVERYTHING = "Installs over this one; nothing is lost."

    /**
     * Whether the automatic check should ask GitHub at all.
     *
     * A clock that has moved backwards — a phone whose time was wrong and got corrected — would
     * otherwise read as "checked in the future" and never ask again, so that counts as due.
     */
    fun dueForCheck(lastCheckedAt: Long, now: Long): Boolean {
        if (lastCheckedAt <= 0L) return true
        if (lastCheckedAt > now) return true
        return now - lastCheckedAt >= CHECK_EVERY_MS
    }

    /**
     * Whether a dismissal still stands for [tag].
     *
     * Tied to the version, not to the app: dismissing v1.0.13 says nothing about v1.0.14, which
     * is news the person has not seen yet.
     */
    fun snoozeHolds(tag: String, snoozedTag: String?, snoozedAt: Long, now: Long): Boolean {
        if (snoozedTag.isNullOrEmpty() || snoozedTag != tag) return false
        if (snoozedAt <= 0L || snoozedAt > now) return false
        return now - snoozedAt < SNOOZE_MS
    }

    /**
     * Whether a remembered update is still worth offering.
     *
     * The answer is remembered so a launch inside the day-long window needs no request, which
     * means it can outlive the install it was about: someone who updates by any other route has
     * a cache describing the version they are now running. Compared against what is installed
     * rather than trusted.
     */
    fun stillNewer(cached: AvailableUpdate?, installedVersionCode: Int): Boolean =
        cached != null && UpdateVersion.isNewer(cached.tag, installedVersionCode)

    /**
     * Whether the strip should be up: something newer is known, and it has not been dismissed.
     *
     * The indicator beside Check for updates follows [stillNewer] instead, so dismissing the
     * strip does not make the news disappear with it.
     */
    fun bannerVisible(
        cached: AvailableUpdate?,
        installedVersionCode: Int,
        snoozedTag: String?,
        snoozedAt: Long,
        now: Long,
    ): Boolean {
        if (!stillNewer(cached, installedVersionCode)) return false
        return !snoozeHolds(cached!!.tag, snoozedTag, snoozedAt, now)
    }

    /**
     * What a finished check means for what is remembered.
     *
     * [NoticeOutcome.Silent] is the whole of requirement for an automatic check that fails: no
     * connection, GitHub having a bad day, a rate limit, or a release whose APK has not finished
     * building yet all land here. None of it is the user's problem, none of it is worth a word on
     * screen, and none of it is recorded as a check that happened — so the next launch tries
     * again rather than staying quiet for a day over a dropped connection.
     */
    fun outcome(result: UpdateState): NoticeOutcome = when (result) {
        is UpdateState.Available -> NoticeOutcome.Found(result.update)
        UpdateState.UpToDate -> NoticeOutcome.None
        else -> NoticeOutcome.Silent
    }

    /** "v1.0.13 is available". */
    fun bannerTitle(tag: String): String = "$tag is available"

    /**
     * "You have v1.0.12 • 12.4 MB. Installs over this one; nothing is lost."
     *
     * Either half can be missing — the GitHub API does not always report an asset size, and the
     * installed version name is not readable on every phone — and a sentence with a gap in it is
     * worse than a shorter sentence, so each part is only said when it is known.
     */
    fun bannerDetail(installedVersionName: String, readableSize: String): String {
        val parts = mutableListOf<String>()
        if (installedVersionName.isNotBlank() && installedVersionName != "unknown") {
            parts += "You have v$installedVersionName"
        }
        if (readableSize.isNotBlank()) parts += readableSize
        val head = parts.joinToString(" • ")
        return if (head.isEmpty()) KEEPS_EVERYTHING else "$head. $KEEPS_EVERYTHING"
    }
}

/** What an automatic check found, as far as what to remember and show is concerned. */
sealed interface NoticeOutcome {
    /** Something newer with an installable APK. Remember it and say so. */
    data class Found(val update: AvailableUpdate) : NoticeOutcome

    /** Nothing newer. Record the check and forget anything remembered earlier. */
    data object None : NoticeOutcome

    /** Say nothing, record nothing, try again next launch. */
    data object Silent : NoticeOutcome
}
