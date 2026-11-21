package com.vknxworks.immichctrl.data.media

import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMetadataRetriever
import android.media.MediaMuxer
import android.net.Uri
import java.io.File
import java.io.IOException
import java.nio.ByteBuffer

/**
 * Cuts a piece out of a video without re-encoding it: the picture and sound are copied as they are into a new
 * MP4, so nothing is lost and it takes seconds. A copy can only begin on a key frame, so the piece starts at
 * the last key frame at or before the chosen start (see [Plan]).
 */
object VideoTrimmer {

    /** The video can't be copied into an MP4 (an unusual picture or sound format). */
    class UnsupportedVideo(message: String) : IOException(message)

    /** Where a cut really starts and ends, in microseconds of the original. */
    data class Plan(val startUs: Long, val endUs: Long) {
        val lengthUs: Long get() = (endUs - startUs).coerceAtLeast(0L)
    }

    /** Shortest piece the screen lets you choose. */
    const val MIN_LENGTH_MS = 1_000L

    /**
     * Keeps a chosen range valid: inside the video, start before end, and at least [MIN_LENGTH_MS] long when the
     * video itself is. [movedStart] says which handle the user dragged; the other one gives way.
     */
    fun clampRange(startMs: Long, endMs: Long, durationMs: Long, movedStart: Boolean): Pair<Long, Long> {
        val total = durationMs.coerceAtLeast(0L)
        val min = MIN_LENGTH_MS.coerceAtMost(total)
        var s = startMs.coerceIn(0L, total)
        var e = endMs.coerceIn(0L, total)
        if (e - s < min) {
            if (movedStart) {
                e = (s + min).coerceAtMost(total)
                s = (e - min).coerceAtLeast(0L)
            } else {
                s = (e - min).coerceAtLeast(0L)
                e = (s + min).coerceAtMost(total)
            }
        }
        return s to e
    }

    /** "clip.mov" -> "clip_trim.mp4" (the result is always an MP4). */
    fun outputName(original: String?): String {
        val file = original.orEmpty().substringAfterLast('/').trim()
        val base = if (file.contains('.')) file.substringBeforeLast('.') else file
        return base.ifEmpty { "video" } + "_trim.mp4"
    }

    /**
     * Writes the part of [source] between [startMs] and [endMs] to [out]. [onProgress] gets 0..1.
     * Returns where the copy really starts and ends. Throws [UnsupportedVideo] when a track can't go into an MP4.
     */
    fun trim(context: Context, source: Uri, out: File, startMs: Long, endMs: Long, onProgress: (Float) -> Unit = {}): Plan {
        val extractor = MediaExtractor()
        var muxer: MediaMuxer? = null
        var started = false
        try {
            if (source.scheme == "file") extractor.setDataSource(requireNotNull(source.path))
            else extractor.setDataSource(context, source, null)

            val mux = MediaMuxer(out.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            muxer = mux
            val rotation = rotationOf(context, source) ?: 0
            if (rotation != 0) mux.setOrientationHint(rotation)

            // Picture and sound tracks only; anything else (timed metadata, camera motion data) is left out.
            val trackMap = HashMap<Int, Int>()
            var hasVideo = false
            var bufferSize = 1 shl 20
            for (i in 0 until extractor.trackCount) {
                val format = extractor.getTrackFormat(i)
                val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()
                val isVideo = mime.startsWith("video/")
                if (!isVideo && !mime.startsWith("audio/")) continue
                val target = try {
                    mux.addTrack(format)
                } catch (e: RuntimeException) {
                    throw UnsupportedVideo(mime)
                }
                extractor.selectTrack(i)
                trackMap[i] = target
                if (isVideo) hasVideo = true
                if (format.containsKey(MediaFormat.KEY_MAX_INPUT_SIZE)) bufferSize = maxOf(bufferSize, format.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE))
            }
            if (!hasVideo) throw UnsupportedVideo("no video track")

            val startUs = startMs.coerceAtLeast(0L) * 1000L
            val endUs = endMs * 1000L
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_PREVIOUS_SYNC)
            // Everything is shifted so the first copied sample is at zero.
            val baseUs = extractor.sampleTime.coerceAtLeast(0L)
            mux.start()
            started = true

            var buffer = ByteBuffer.allocate(bufferSize)
            val info = MediaCodec.BufferInfo()
            var lastUs = baseUs
            var wrote = false
            val finished = HashSet<Int>()
            val span = (endUs - baseUs).coerceAtLeast(1L).toFloat()
            while (true) {
                val track = extractor.sampleTrackIndex
                if (track < 0) break
                val timeUs = extractor.sampleTime
                val target = trackMap[track]
                if (target == null || track in finished || timeUs > endUs) {
                    // The first sample past the end closes its track. Later samples of that track are left
                    // out even when they belong before the end (frames stored out of order), because the
                    // frame they depend on isn't copied; that only drops a few frames at the very end.
                    if (target != null && finished.add(track) && finished.size == trackMap.size) break
                    if (!extractor.advance()) break
                    continue
                }
                val size = try {
                    extractor.readSampleData(buffer, 0)
                } catch (e: IllegalArgumentException) {
                    // A sample bigger than the track said it could be: take a bigger buffer and read it again.
                    if (buffer.capacity() >= (64 shl 20)) throw IOException("sample too large")
                    buffer = ByteBuffer.allocate(buffer.capacity() * 2)
                    continue
                }
                if (size < 0) break
                info.set(
                    0,
                    size,
                    (timeUs - baseUs).coerceAtLeast(0L),
                    if (extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) MediaCodec.BUFFER_FLAG_KEY_FRAME else 0,
                )
                mux.writeSampleData(target, buffer, info)
                wrote = true
                if (timeUs > lastUs) {
                    lastUs = timeUs
                    onProgress(((timeUs - baseUs) / span).coerceIn(0f, 1f))
                }
                if (!extractor.advance()) break
            }
            if (!wrote) throw IOException("nothing to copy")
            mux.stop()
            started = false
            onProgress(1f)
            return Plan(baseUs, lastUs)
        } catch (e: Throwable) {
            if (started) runCatching { muxer?.stop() }
            out.delete()
            throw e
        } finally {
            runCatching { muxer?.release() }
            runCatching { extractor.release() }
        }
    }

    /** How the picture has to be turned to be upright (0, 90, 180, 270), or null when unknown. */
    private fun rotationOf(context: Context, source: Uri): Int? {
        val r = MediaMetadataRetriever()
        return try {
            if (source.scheme == "file") r.setDataSource(source.path) else r.setDataSource(context, source)
            r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_ROTATION)?.toIntOrNull()
        } catch (e: RuntimeException) {
            null
        } finally {
            runCatching { r.release() }
        }
    }
}
