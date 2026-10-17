package com.vknxworks.immichctrl.data.share

import android.app.DownloadManager
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.content.FileProvider
import com.vknxworks.immichctrl.data.api.ImmichClient
import com.vknxworks.immichctrl.data.api.asset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okio.buffer
import okio.sink
import java.io.File
import java.io.IOException

/** Getting photos out of Immich: saving originals to the phone and sharing them to other apps. */
object Exporter {

    /** Saves originals to Pictures/ImmichCtrl with the system download manager (shows its own progress). */
    suspend fun download(context: Context, client: ImmichClient, ids: Collection<String>): Int = coroutineScope {
        val dm = context.getSystemService(DownloadManager::class.java)
        val gate = Semaphore(4)
        val names = ids.map { id -> async { gate.withPermit { id to runCatching { client.asset(id).originalFileName }.getOrNull() } } }.awaitAll()
        names.forEach { (id, name) ->
            val file = (name?.takeIf { it.isNotBlank() } ?: "$id.jpg").replace('/', '_')
            val (h, v) = client.authHeader
            val req = DownloadManager.Request(Uri.parse(client.originalUrl(id)))
                .addRequestHeader(h, v)
                .setTitle(file)
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setDestinationInExternalPublicDir(Environment.DIRECTORY_PICTURES, "ImmichCtrl/$file")
            dm.enqueue(req)
        }
        names.size
    }

    /** Fetches originals into the app's cache and opens the share sheet with them. */
    suspend fun shareFiles(context: Context, client: ImmichClient, ids: Collection<String>) {
        val dir = File(context.cacheDir, "shared").apply {
            // Old shares are no longer needed once a new one starts.
            listFiles()?.forEach { it.deleteRecursively() }
            mkdirs()
        }
        val files = coroutineScope {
            val gate = Semaphore(3)
            ids.map { id ->
                async(Dispatchers.IO) {
                    gate.withPermit {
                        val dto = client.asset(id)
                        val name = dto.originalFileName.ifBlank { "$id.jpg" }.replace('/', '_')
                        val target = File(File(dir, id).apply { mkdirs() }, name)
                        val req = client.request(client.url("/assets/$id/original")).build()
                        client.execute(req).use { resp ->
                            val body = resp.body ?: throw IOException("Empty response")
                            target.sink().buffer().use { it.writeAll(body.source()) }
                        }
                        target to (dto.originalMimeType ?: if (dto.isImage) "image/*" else "video/*")
                    }
                }
            }.awaitAll()
        }
        if (files.isEmpty()) return
        val uris = files.map { FileProvider.getUriForFile(context, "${context.packageName}.files", it.first) }
        val type = files.map { it.second }.distinct().singleOrNull() ?: "*/*"
        val intent = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris.first())
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, ArrayList(uris))
        }
        intent.type = type
        intent.clipData = ClipData.newRawUri(null, uris.first()).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    fun shareText(context: Context, text: String) {
        val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
        context.startActivity(Intent.createChooser(intent, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** Public address of a shared link: the server's external domain if set, else the address the app uses. */
    fun sharedLinkUrl(client: ImmichClient, externalDomain: String?, key: String, slug: String?): String {
        val base = externalDomain?.trim()?.trimEnd('/')?.ifBlank { null } ?: client.baseUrl
        return if (!slug.isNullOrBlank()) "$base/s/$slug" else "$base/share/$key"
    }
}
