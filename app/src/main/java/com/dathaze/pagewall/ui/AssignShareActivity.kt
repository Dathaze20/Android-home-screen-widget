package com.dathaze.pagewall.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.dathaze.pagewall.data.ImportResult
import com.dathaze.pagewall.data.MediaImporter
import com.dathaze.pagewall.data.PageStore
import com.dathaze.pagewall.widget.PageWidgetProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Share target: send a photo, GIF, video (or a track) here from the gallery and pick which page
 * it belongs to.
 *
 * This is the quickest route once the wallpaper is set up — Gallery ⇒ Share ⇒ Page Wallpaper ⇒
 * tap a page — with no need to go looking for the app.
 */
class AssignShareActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val uri = incomingUri()
        val isAudio = intent?.type?.startsWith("audio/") == true
        if (uri == null) {
            Toast.makeText(this, "Nothing to assign", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val store = PageStore(this)

        setContent {
            PageWallTheme {
                val scrollState = rememberScrollState()
                AlertDialog(
                    onDismissRequest = { finish() },
                    title = { Text(if (isAudio) "Add track to page" else "Use on page") },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 360.dp)
                                .verticalScroll(scrollState),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            (0 until store.pageCount).forEach { page ->
                                OutlinedButton(
                                    onClick = { assign(store, uri, page, isAudio) },
                                    modifier = Modifier.fillMaxWidth(),
                                ) { Text("Page ${page + 1}") }
                            }
                        }
                    },
                    confirmButton = {},
                    dismissButton = {
                        TextButton(onClick = { finish() }) { Text("Cancel") }
                    },
                )
            }
        }
    }

    private fun assign(store: PageStore, uri: Uri, page: Int, isAudio: Boolean) {
        val metrics = resources.displayMetrics
        lifecycleScope.launch {
            val message = withContext(Dispatchers.IO) {
                if (isAudio) {
                    val imported = MediaImporter.importAudio(this@AssignShareActivity, store, uri, page)
                    if (imported == null) {
                        "Could not read that track"
                    } else {
                        store.setAudio(page, imported.first, imported.second)
                        "Saved to page ${page + 1}"
                    }
                } else {
                    when (
                        val result = MediaImporter.importMedia(
                            context = this@AssignShareActivity,
                            store = store,
                            uri = uri,
                            pageIndex = page,
                            targetWidth = metrics.widthPixels,
                            targetHeight = metrics.heightPixels,
                        )
                    ) {
                        is ImportResult.Success -> {
                            store.setMedia(page, result.fileName, result.kind, result.posterFile)
                            "Saved to page ${page + 1}"
                        }
                        is ImportResult.Failure -> result.message
                    }
                }
            }
            PageWidgetProvider.notifyPageChanged(this@AssignShareActivity, store.currentPage)
            Toast.makeText(this@AssignShareActivity, message, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    @Suppress("DEPRECATION")
    private fun incomingUri(): Uri? {
        val source = intent ?: return null
        if (source.action != Intent.ACTION_SEND) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            source.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            source.getParcelableExtra(Intent.EXTRA_STREAM)
        }
    }
}
