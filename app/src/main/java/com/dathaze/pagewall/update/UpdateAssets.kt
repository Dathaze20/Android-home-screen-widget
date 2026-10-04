package com.dathaze.pagewall.update

/**
 * Picks the APK in a release that belongs to *this* build.
 *
 * Once the same release carries both the personal and the public build, "the first file ending in
 * .apk" stops being an answer: the two have different applicationIds and different signing keys,
 * so installing the wrong one does not fail cleanly — Android simply refuses it, or installs a
 * second copy of the app alongside the real one.
 *
 * Releases up to v1.0.5 attached one unmarked APK, and those have to keep working, so an unmarked
 * lone APK is read as the build everyone was running at the time. Anything ambiguous returns null
 * rather than guessing: offering an update that cannot install is worse than offering none.
 */
object UpdateAssets {

    /** Markers a release asset may carry. A name carrying none of these is a legacy release. */
    val KNOWN_TAGS = listOf("personal", "public")

    /**
     * The asset [names] entry for the build tagged [flavorTag], or null when the release has
     * nothing this build can install.
     */
    fun pick(names: List<String>, flavorTag: String): String? {
        val apks = names.filter { it.endsWith(".apk", ignoreCase = true) }
        if (apks.isEmpty()) return null

        apks.firstOrNull { carriesTag(it, flavorTag) }?.let { return it }

        // No marked asset. A single unmarked APK is a release from before the split; anything
        // else means this build's APK is simply not in this release.
        val unmarked = apks.filter { name -> KNOWN_TAGS.none { carriesTag(name, it) } }
        return if (apks.size == 1 && unmarked.size == 1) unmarked.single() else null
    }

    /** True when [name] is marked for [tag], as `-tag-` in the middle or `-tag.apk` at the end. */
    private fun carriesTag(name: String, tag: String): Boolean {
        val lower = name.lowercase()
        val marker = "-${tag.lowercase()}"
        return lower.contains("$marker-") || lower.endsWith("$marker.apk")
    }
}
