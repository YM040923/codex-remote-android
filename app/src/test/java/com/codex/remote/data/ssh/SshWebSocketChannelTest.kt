package com.codex.remote.data.ssh

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

class SshWebSocketChannelTest {
    @Test
    fun upgradesAndUsesMaskedClientFrames() {
        val outgoing = ByteArrayOutputStream()
        val input = ByteArrayInputStream(
            handshakeResponse() + serverFrame(opcode = 0x1, final = true, text = "server message"),
        )
        val channel = SshWebSocketChannel(input, outgoing, FixedRandom())

        channel.connect()
        assertEquals("server message", channel.readText())
        channel.writeText("client message")

        val written = outgoing.toByteArray()
        assertTrue(String(written, StandardCharsets.US_ASCII).startsWith("GET / HTTP/1.1\r\n"))
        val frameOffset = written.indexOfHeaderEnd() + 4
        val frame = written.copyOfRange(frameOffset, written.size)
        assertEquals(0x81, frame[0].toInt() and 0xff)
        assertTrue((frame[1].toInt() and 0x80) != 0)
        val length = frame[1].toInt() and 0x7f
        assertEquals("client message", unmask(frame, 2, length))
    }

    @Test
    fun reassemblesFragmentsAndAnswersPing() {
        val outgoing = ByteArrayOutputStream()
        val serverFrames = serverFrame(0x1, final = false, text = "Codex") +
            serverFrame(0x9, final = true, text = "ping") +
            serverFrame(0x0, final = true, text = " remote")
        val channel = SshWebSocketChannel(
            ByteArrayInputStream(handshakeResponse() + serverFrames),
            outgoing,
            FixedRandom(),
        )

        channel.connect()
        assertEquals("Codex remote", channel.readText())

        val written = outgoing.toByteArray()
        val frame = written.copyOfRange(written.indexOfHeaderEnd() + 4, written.size)
        assertEquals(0x8a, frame[0].toInt() and 0xff)
        val length = frame[1].toInt() and 0x7f
        assertEquals("ping", unmask(frame, 2, length))
    }

    @Test
    fun rejectsInvalidAcceptAndMissingConnectionUpgradeToken() {
        val badAccept = response(accept = "not-the-expected-key")
        val acceptChannel = SshWebSocketChannel(
            ByteArrayInputStream(badAccept),
            ByteArrayOutputStream(),
            FixedRandom(),
        )
        assertThrows(IOException::class.java) { acceptChannel.connect() }

        val noConnectionUpgrade = response(connection = "keep-alive")
        val connectionChannel = SshWebSocketChannel(
            ByteArrayInputStream(noConnectionUpgrade),
            ByteArrayOutputStream(),
            FixedRandom(),
        )
        assertThrows(IOException::class.java) { connectionChannel.connect() }
    }

    @Test
    fun rejectsOversizedServerFrameBeforeAllocatingItsPayload() {
        val oversizedLength = 32L * 1024 * 1024 + 1
        val header = byteArrayOf(0x81.toByte(), 0x7f) + ByteArray(8) { index ->
            (oversizedLength ushr (56 - index * 8)).toByte()
        }
        val channel = SshWebSocketChannel(
            ByteArrayInputStream(handshakeResponse() + header),
            ByteArrayOutputStream(),
            FixedRandom(),
        )

        channel.connect()
        assertThrows(IOException::class.java) { channel.readText() }
    }

    @Test
    fun closeIsIdempotentAndBlocksLaterWrites() {
        val outgoing = ByteArrayOutputStream()
        val channel = SshWebSocketChannel(
            ByteArrayInputStream(handshakeResponse()),
            outgoing,
            FixedRandom(),
        )

        channel.connect()
        channel.close()
        channel.close()

        val written = outgoing.toByteArray()
        val frame = written.copyOfRange(written.indexOfHeaderEnd() + 4, written.size)
        assertEquals(0x88, frame[0].toInt() and 0xff)
        assertEquals(0, frame[1].toInt() and 0x7f)
        assertEquals(6, frame.size)
        assertThrows(IllegalStateException::class.java) { channel.writeText("too late") }
    }

    private fun handshakeResponse(): ByteArray = response()

    private fun response(
        accept: String = expectedAccept(),
        connection: String = "keep-alive, Upgrade",
    ): ByteArray = (
        "HTTP/1.1 101 Switching Protocols\r\n" +
            "Upgrade: websocket\r\n" +
            "Connection: $connection\r\n" +
            "Sec-WebSocket-Accept: $accept\r\n\r\n"
        ).toByteArray(StandardCharsets.US_ASCII)

    private fun expectedAccept(): String {
        val key = Base64.getEncoder().encodeToString(ByteArray(16) { it.toByte() })
        val digest = MessageDigest.getInstance("SHA-1").digest(
            (key + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(StandardCharsets.US_ASCII),
        )
        return Base64.getEncoder().encodeToString(digest)
    }

    private fun serverFrame(opcode: Int, final: Boolean, text: String): ByteArray {
        val payload = text.toByteArray(StandardCharsets.UTF_8)
        require(payload.size < 126)
        return byteArrayOf(
            ((if (final) 0x80 else 0) or opcode).toByte(),
            payload.size.toByte(),
        ) + payload
    }

    private fun unmask(frame: ByteArray, maskOffset: Int, length: Int): String {
        val payloadOffset = maskOffset + 4
        val mask = frame.copyOfRange(maskOffset, payloadOffset)
        val payload = ByteArray(length) { index ->
            (frame[payloadOffset + index].toInt() xor mask[index % 4].toInt()).toByte()
        }
        return String(payload, StandardCharsets.UTF_8)
    }

    private fun ByteArray.indexOfHeaderEnd(): Int {
        val marker = byteArrayOf('\r'.code.toByte(), '\n'.code.toByte(), '\r'.code.toByte(), '\n'.code.toByte())
        return indices.first { index ->
            index + marker.size <= size && marker.indices.all { offset -> this[index + offset] == marker[offset] }
        }
    }

    private class FixedRandom : SecureRandom() {
        override fun nextBytes(bytes: ByteArray) {
            bytes.indices.forEach { index -> bytes[index] = index.toByte() }
        }
    }
}
