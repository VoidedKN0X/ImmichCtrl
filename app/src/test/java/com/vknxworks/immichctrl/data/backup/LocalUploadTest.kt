package com.vknxworks.immichctrl.data.backup

import okio.Buffer
import okio.buffer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream
import java.net.SocketException

class LocalUploadTest {
    private val bytes = ByteArray(200_000) { (it % 251).toByte() }

    @Test fun copiesTheWholeFile() {
        val sink = Buffer()
        writeLocalFile(sink, bytes.size.toLong(), "a.jpg") { ByteArrayInputStream(bytes) }
        assertTrue(bytes.contentEquals(sink.readByteArray()))
    }

    private fun assertLocal(block: () -> Unit) {
        try {
            block()
            fail("no exception")
        } catch (e: IOException) {
            assertTrue("$e", LocalReadException.isCause(e))
        }
    }

    @Test fun fileThatCantBeOpenedIsALocalProblem() {
        assertLocal { writeLocalFile(Buffer(), 10, "gone.jpg") { null } }
        assertLocal { writeLocalFile(Buffer(), 10, "gone.jpg") { throw FileNotFoundException("gone") } }
        assertLocal { writeLocalFile(Buffer(), 10, "denied.jpg") { throw SecurityException("denied") } }
    }

    @Test fun readErrorIsALocalProblem() {
        val broken = object : InputStream() {
            var n = 0
            override fun read(): Int = if (n++ < 5) 1 else throw IOException("I/O error")
            override fun read(b: ByteArray, off: Int, len: Int): Int = if (n++ < 1) { b[off] = 1; 1 } else throw IOException("I/O error")
        }
        assertLocal { writeLocalFile(Buffer(), 100, "bad.jpg") { broken } }
    }

    @Test fun sizeChangesAreLocalProblems() {
        assertLocal { writeLocalFile(Buffer(), bytes.size + 1L, "short.jpg") { ByteArrayInputStream(bytes) } }
        assertLocal { writeLocalFile(Buffer(), bytes.size - 1L, "long.jpg") { ByteArrayInputStream(bytes) } }
    }

    @Test fun networkErrorsStayNetworkErrors() {
        val failingSink = object : okio.Sink {
            override fun write(source: Buffer, byteCount: Long) {
                throw SocketException("connection reset")
            }
            override fun flush() {}
            override fun timeout() = okio.Timeout.NONE
            override fun close() {}
        }
        val sink = failingSink.buffer()
        try {
            writeLocalFile(sink, bytes.size.toLong(), "a.jpg") { ByteArrayInputStream(bytes) }
            sink.flush()
            fail("no exception")
        } catch (e: IOException) {
            assertFalse(LocalReadException.isCause(e))
        }
    }

    @Test fun causeIsFoundWhenWrapped() {
        assertTrue(LocalReadException.isCause(IOException("outer", LocalReadException("inner"))))
        assertFalse(LocalReadException.isCause(IOException("plain")))
        assertEquals(false, LocalReadException.isCause(SocketException("x")))
    }
}
