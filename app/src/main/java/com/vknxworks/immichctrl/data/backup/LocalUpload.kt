package com.vknxworks.immichctrl.data.backup

import okio.BufferedSink
import java.io.IOException
import java.io.InputStream

/**
 * The file on the phone couldn't be read while it was being uploaded: it was deleted or moved, can't be opened,
 * or its size no longer matches what Android's media list says. Not a lost connection, so the backup counts that
 * file as failed and goes on with the others instead of stopping and retrying the whole run (which would stop at
 * the same file again every time).
 */
class LocalReadException(message: String, cause: Throwable? = null) : IOException(message, cause) {
    companion object {
        /** True when [e] is, or was caused by, a [LocalReadException]. */
        fun isCause(e: Throwable): Boolean {
            var t: Throwable? = e
            var depth = 0
            while (t != null && depth < 8) {
                if (t is LocalReadException) return true
                t = t.cause
                depth++
            }
            return false
        }
    }
}

/**
 * Copies the local file from [open] to [sink], exactly [size] bytes (the length the request announced).
 * Problems on the phone's side throw [LocalReadException]; problems writing to [sink] (the network) stay plain
 * IOExceptions.
 */
internal fun writeLocalFile(sink: BufferedSink, size: Long, name: String, open: () -> InputStream?) {
    val input = try {
        open()
    } catch (e: IOException) {
        throw LocalReadException("can't open $name", e)
    } catch (e: SecurityException) {
        throw LocalReadException("no access to $name", e)
    } catch (e: IllegalArgumentException) {
        throw LocalReadException("can't open $name", e)
    } ?: throw LocalReadException("can't open $name")
    input.use { stream ->
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = try {
                stream.read(buffer)
            } catch (e: IOException) {
                throw LocalReadException("can't read $name", e)
            }
            if (n < 0) break
            if (total + n > size) throw LocalReadException("$name grew while uploading")
            sink.write(buffer, 0, n)
            total += n
        }
        if (total != size) throw LocalReadException("$name shrank while uploading")
    }
}
