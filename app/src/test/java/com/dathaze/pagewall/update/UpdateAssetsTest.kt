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
    fun `a release carrying only the public APK updates the public build`() {
        // The shape every release takes from v1.0.10 onwards: one asset, the public build.
        // The name is produced by .github/workflows/release.yml, and this is the contract
        // between that workflow and every copy of the app already installed. If the workflow
        // ever names the file differently, "Check for updates" goes quiet on every phone and
        // nothing else reports it — so it is pinned here, spelled out, rather than derived.
        val release = listOf("page-wallpaper-public-v1.0.10.apk")
        assertEquals("page-wallpaper-public-v1.0.10.apk", UpdateAssets.pick(release, "public"))
    }

    @Test
    fun `a public-only release offers the personal build nothing at all`() {
        // The personal build is no longer published. It must be told there is no update rather
        // than handed an APK under a different application ID and a different signing key,
        // which Android would refuse to install over it.
        val release = listOf("page-wallpaper-public-v1.0.10.apk")
        assertNull(UpdateAssets.pick(release, "personal"))
    }

    @Test
    fun `a two-digit patch number does not confuse the asset name`() {
        // v1.0.9 -> v1.0.10 is the first time the patch number grows a digit. The marker is
        // matched as a whole word, so the digits after it cannot affect the choice.
        listOf("1.0.9", "1.0.10", "1.0.11", "1.1.0", "2.0.0").forEach { version ->
            val name = "page-wallpaper-public-v$version.apk"
            assertEquals(name, UpdateAssets.pick(listOf(name), "public"))
        }
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
