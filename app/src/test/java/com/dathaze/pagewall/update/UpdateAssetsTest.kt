package com.dathaze.pagewall.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Installing the wrong build's APK does not fail politely: the applicationId and the signing key
 * both differ, so Android either refuses it or quietly installs a second copy of the app.
 */
class UpdateAssetsTest {

    private val split = listOf(
        "page-wallpaper-personal-v1.0.6.apk",
        "page-wallpaper-public-v1.0.6.apk",
    )

    @Test
    fun `each build takes its own APK out of a release carrying both`() {
        assertEquals("page-wallpaper-personal-v1.0.6.apk", UpdateAssets.pick(split, "personal"))
        assertEquals("page-wallpaper-public-v1.0.6.apk", UpdateAssets.pick(split, "public"))
    }

    @Test
    fun `releases from before the split still update the personal build`() {
        val legacy = listOf("page-wallpaper-v1.0.5.apk")
        assertEquals("page-wallpaper-v1.0.5.apk", UpdateAssets.pick(legacy, "personal"))
    }

    @Test
    fun `the public build never claims an unmarked APK`() {
        // Every release before the split is com.dathaze.pagewall signed with the repository's
        // debug key. A public install taking one would download an APK it cannot install.
        listOf(
            "page-wallpaper-v1.0.1.apk",
            "page-wallpaper-v1.0.4.apk",
            "page-wallpaper-v1.0.5.apk",
        ).forEach { assertNull(UpdateAssets.pick(listOf(it), "public")) }
    }

    @Test
    fun `the names actually published by v1_0_8 resolve correctly`() {
        // Taken from the real release rather than invented, so a change to how the workflow
        // names its assets fails here rather than on someone's phone.
        val shipped = listOf(
            "page-wallpaper-personal-v1.0.8.apk",
            "page-wallpaper-public-v1.0.8.apk",
        )
        assertEquals("page-wallpaper-personal-v1.0.8.apk", UpdateAssets.pick(shipped, "personal"))
        assertEquals("page-wallpaper-public-v1.0.8.apk", UpdateAssets.pick(shipped, "public"))
    }

    @Test
    fun `a release missing this build offers nothing rather than the other build`() {
        val onlyPublic = listOf("page-wallpaper-public-v1.0.6.apk")
        assertNull(UpdateAssets.pick(onlyPublic, "personal"))
        assertEquals("page-wallpaper-public-v1.0.6.apk", UpdateAssets.pick(onlyPublic, "public"))
    }

    @Test
    fun `an unmarked APK beside a marked one is not assumed to be ours`() {
        val mixed = listOf("page-wallpaper-v1.0.6.apk", "page-wallpaper-public-v1.0.6.apk")
        assertNull(UpdateAssets.pick(mixed, "personal"))
        assertEquals("page-wallpaper-public-v1.0.6.apk", UpdateAssets.pick(mixed, "public"))
    }

    @Test
    fun `non-APK assets are ignored`() {
        val noisy = listOf("checksums.txt", "source.zip", "page-wallpaper-personal-v1.0.6.apk")
        assertEquals("page-wallpaper-personal-v1.0.6.apk", UpdateAssets.pick(noisy, "personal"))
        assertNull(UpdateAssets.pick(listOf("notes.txt", "source.zip"), "personal"))
        assertNull(UpdateAssets.pick(emptyList(), "personal"))
    }

    @Test
    fun `the tag has to be a whole word, not a coincidence inside another one`() {
        // Two assets, so the single-unmarked-APK fallback cannot rescue the match.
        val lookalike = listOf(
            "page-wallpaper-publicity-v1.0.6.apk",
            "page-wallpaper-personal-v1.0.6.apk",
        )
        assertNull(UpdateAssets.pick(lookalike, "public"))
        assertEquals("page-wallpaper-personal-v1.0.6.apk", UpdateAssets.pick(lookalike, "personal"))
    }

    @Test
    fun `case in the file name does not decide whether an update is offered`() {
        val shouty = listOf("Page-Wallpaper-PUBLIC-v1.0.6.APK")
        assertEquals("Page-Wallpaper-PUBLIC-v1.0.6.APK", UpdateAssets.pick(shouty, "public"))
    }
}
