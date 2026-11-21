package com.vknxworks.immichctrl

import com.vknxworks.immichctrl.data.media.VideoTrimmer
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoTrimmerTest {
    @Test
    fun rangeStaysInsideTheVideo() {
        assertEquals(0L to 10_000L, VideoTrimmer.clampRange(-5, 99_000, 10_000, movedStart = true))
        assertEquals(2_000L to 8_000L, VideoTrimmer.clampRange(2_000, 8_000, 10_000, movedStart = false))
    }

    @Test
    fun handlesKeepAMinimumDistance() {
        // Start dragged onto the end: the end gives way.
        assertEquals(5_000L to 6_000L, VideoTrimmer.clampRange(5_000, 5_200, 10_000, movedStart = true))
        // End dragged onto the start: the start gives way.
        assertEquals(4_200L to 5_200L, VideoTrimmer.clampRange(5_000, 5_200, 10_000, movedStart = false))
        // At the very end there's no room after the start, so the start moves back.
        assertEquals(9_000L to 10_000L, VideoTrimmer.clampRange(9_800, 10_000, 10_000, movedStart = true))
        // At the very beginning the end moves forward.
        assertEquals(0L to 1_000L, VideoTrimmer.clampRange(0, 200, 10_000, movedStart = false))
    }

    @Test
    fun aVideoShorterThanTheMinimumKeepsItsWholeLength() {
        assertEquals(0L to 400L, VideoTrimmer.clampRange(100, 300, 400, movedStart = true))
    }

    @Test
    fun outputNames() {
        assertEquals("clip_trim.mp4", VideoTrimmer.outputName("clip.mov"))
        assertEquals("a.b_trim.mp4", VideoTrimmer.outputName("a.b.mp4"))
        assertEquals("clip_trim.mp4", VideoTrimmer.outputName("DCIM/Camera/clip.mp4"))
        assertEquals("video_trim.mp4", VideoTrimmer.outputName(null))
        assertEquals("video_trim.mp4", VideoTrimmer.outputName(""))
        assertEquals("noext_trim.mp4", VideoTrimmer.outputName("noext"))
    }
}
