package com.darkrockstudios.apps.pcvolumemixer.protocol

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.StringReader

class BoundedLineReaderTest {

    @Test
    fun `splits lines and drops carriage returns`() {
        val reader = BoundedLineReader(StringReader("one\ntwo\r\n\nthree"))

        assertEquals("one", reader.readLine())
        assertEquals("two", reader.readLine())
        assertEquals("", reader.readLine())
        assertEquals("three", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun `reads lines far larger than its buffer`() {
        val big = "x".repeat(900 * 1024)
        val reader = BoundedLineReader(StringReader("$big\nafter\n"))

        assertEquals(big, reader.readLine())
        assertEquals("after", reader.readLine())
        assertNull(reader.readLine())
    }

    @Test
    fun `accepts a line of exactly the limit`() {
        val reader = BoundedLineReader(StringReader("12345\r\n"), maxChars = 5)

        assertEquals("12345", reader.readLine())
    }

    @Test(expected = LineTooLongException::class)
    fun `rejects a line over the limit`() {
        BoundedLineReader(StringReader("123456\n"), maxChars = 5).readLine()
    }

    @Test(expected = LineTooLongException::class)
    fun `rejects an unterminated stream over the limit`() {
        BoundedLineReader(StringReader("y".repeat(100_000)), maxChars = 50_000).readLine()
    }

    @Test
    fun `default limit allows the protocol minimum`() {
        val oneMiB = "z".repeat(1024 * 1024)

        assertEquals(oneMiB, BoundedLineReader(StringReader("$oneMiB\n")).readLine())
    }
}
