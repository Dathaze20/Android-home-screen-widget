package com.dathaze.pagewall.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub for a newer release and installs it, without leaving the app.
 *
 * A port of the same mechanism used in the Music Player Tagger app: ask the releases API for the
 * latest tag, compare it against the installed versionCode, download the attached APK, and hand
 * the file to Android's installer. Everything here blocks, so it is called from a background
 * dispatcher; progress is reported through [onProgress] rather than by returning repeatedly.
 */
class AppUpdater(private val context: Context) {

    /** The versionCode baked into the running APK, which the tag is compared against. */
    val installedVersionCode: Int
        get() = runCatching {
            // minSdk is 28, so longVersionCode is always available and versionCode is never read.
            context.packageManager.getPackageInfo(context.packageName, 0).longVersionCode.toInt()
        }.getOrDefault(0)

    val installedVersionName: String
        get() = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: "unknown"

    /** True once the user has allowed this app to install apps; the switch lives in Settings. */
    fun canInstallPackages(): Boolean = context.packageManager.canRequestPackageInstalls()

    /**
     * Opens the settings screen where installing from this app is allowed.
     *
     * Returns false if no such screen exists on this phone, so the caller can say so instead of
     * leaving the user staring at a button that did nothing.
     */
    fun openInstallPermissionSettings(): Boolean {
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}"),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { context.startActivity(intent) }.isSuccess
    }

    /**
     * Asks GitHub for the newest release.
     *
     * Returns [UpdateState.UpToDate] when the tag is not newer than what is installed, and
     * [UpdateState.Failed] rather than throwing, so the caller has one thing to render.
     */
    fun check(repo: String): UpdateState {
        val body = runCatching {
            fetchText("https://api.github.com/repos/$repo/releases/latest")
        }.getOrElse { failure ->
            Log.w(TAG, "Could not reach GitHub", failure)
            // A 404 here is not a network problem: it is what GitHub answers when a repository
            // has no published release at all, which is the ordinary state before the first one.
            // Saying "check your connection" for it sends the user chasing the wrong thing.
            val status = (failure as? HttpStatusException)?.code
            return when (status) {
                null -> UpdateState.Failed("Could not reach GitHub — check your connection")
                404 -> UpdateState.Failed("No release has been published yet.")
                403, 429 -> UpdateState.Failed("GitHub is rate-limiting us — try again later.")
                else -> UpdateState.Failed("GitHub answered $status")
            }
        }

        val release = runCatching { JSONObject(body) }.getOrElse {
            return UpdateState.Failed("GitHub sent something unreadable")
        }

        val tag = release.optString("tag_name").takeIf { it.isNotEmpty() }
            ?: return UpdateState.Failed("That release has no version tag")

        if (!UpdateVersion.isNewer(tag, installedVersionCode)) {
            return UpdateState.UpToDate
        }

        val assets = release.optJSONArray("assets")
        val apk = (0 until (assets?.length() ?: 0))
            .mapNotNull { assets?.optJSONObject(it) }
            .firstOrNull { it.optString("name").endsWith(".apk", ignoreCase = true) }
            ?: return UpdateState.Failed("$tag is out, but has no APK attached yet")

        return UpdateState.Available(
            AvailableUpdate(
                tag = tag,
                versionName = tag.removePrefix("v"),
                notes = release.optString("body").take(NOTES_LIMIT).trim(),
                apkUrl = apk.optString("browser_download_url"),
                apkName = apk.optString("name").ifEmpty { "page-wallpaper-update.apk" },
                apkBytes = apk.optLong("size"),
            )
        )
    }

    /**
     * Downloads [update] and hands it to Android's installer.
     *
     * The download is verified against the length the server reported: a truncated APK must never
     * reach the installer, because it fails there with a message that looks like the app is
     * broken rather than like the download was.
     */
    fun downloadAndInstall(update: AvailableUpdate, onProgress: (Int) -> Unit): UpdateState {
        if (!update.apkUrl.startsWith("https://")) {
            return UpdateState.Failed("An update must be fetched over https")
        }
        if (!canInstallPackages()) return UpdateState.NeedsInstallPermission

        val dir = File(context.cacheDir, "updates")
        if (!dir.exists() && !dir.mkdirs()) {
            return UpdateState.Failed("Could not make room for the download")
        }
        // Keep one update on disk, so a half-finished earlier attempt can never be mistaken for
        // a complete file.
        dir.listFiles()?.forEach { if (!it.delete()) Log.w(TAG, "stale update left: ${it.name}") }

        val target = File(dir, update.apkName.replace(Regex("[^A-Za-z0-9._-]"), "_"))
        var connection: HttpURLConnection? = null
        try {
            connection = openFollowingRedirects(update.apkUrl)
            val total = connection.contentLengthLong
            var done = 0L
            var lastPercent = -1

            connection.inputStream.buffered().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        done += read
                        if (total > 0) {
                            val percent = (done * 100 / total).toInt()
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            if (total > 0 && target.length() != total) {
                target.delete()
                return UpdateState.Failed(
                    "The download arrived cut short (${target.length()} of $total bytes)"
                )
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                target,
            )
            val install = Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(install)
            return UpdateState.ReadyToInstall
        } catch (e: Exception) {
            Log.w(TAG, "Update download failed", e)
            target.delete()
            return UpdateState.Failed("Update failed — ${e.message ?: "unknown error"}")
        } finally {
            connection?.disconnect()
        }
    }

    private fun fetchText(url: String): String {
        val connection = openFollowingRedirects(url, accept = "application/vnd.github+json")
        return try {
            connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    /**
     * HttpURLConnection will not follow a redirect across hosts by itself, and GitHub answers an
     * asset request with exactly that: a redirect to its download host.
     *
     * Every hop is checked to still be https. Following a redirect down to plain http would hand
     * an installable APK to whatever is on the network between here and there.
     */
    private fun openFollowingRedirects(
        url: String,
        accept: String = "application/octet-stream, */*",
    ): HttpURLConnection {
        var current = URL(url)
        var hops = 0
        while (true) {
            if (!current.protocol.equals("https", ignoreCase = true)) {
                throw IOException("refusing to fetch an update over ${current.protocol}")
            }
            val connection = (current.openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 30_000
                readTimeout = 60_000
                setRequestProperty("Accept", accept)
                setRequestProperty("User-Agent", "PageWallpaper")
            }
            when (val code = connection.responseCode) {
                HttpURLConnection.HTTP_MOVED_PERM,
                HttpURLConnection.HTTP_MOVED_TEMP,
                HttpURLConnection.HTTP_SEE_OTHER,
                307,
                308,
                -> {
                    val next = connection.getHeaderField("Location")
                    connection.disconnect()
                    if (next == null) throw IOException("a redirect with nowhere to go")
                    if (++hops > MAX_REDIRECTS) throw IOException("too many redirects")
                    // Resolved against the current URL, because a Location header is allowed to
                    // be a path rather than a whole address.
                    current = URL(current, next)
                }

                HttpURLConnection.HTTP_OK -> return connection

                else -> {
                    connection.disconnect()
                    throw HttpStatusException(code)
                }
            }
        }
    }

    /** Carries the status code so the caller can say something true about a 404 or a 403. */
    private class HttpStatusException(val code: Int) :
        IOException("the server answered $code")

    companion object {
        /** Where releases are published. The update button has nothing to find anywhere else. */
        const val GITHUB_REPO = "Dathaze20/Android-home-screen-widget"

        private const val TAG = "AppUpdater"
        private const val MAX_REDIRECTS = 5
        private const val NOTES_LIMIT = 400
    }
}
