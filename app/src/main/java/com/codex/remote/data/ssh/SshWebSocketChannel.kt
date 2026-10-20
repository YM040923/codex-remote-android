package com.codex.remote.data.ssh

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.CharsetDecoder
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * WebSocket client framing over an SSH exec channel.
 *
 * `codex app-server proxy` forwards its stdin/stdout bytes to the shared
 * app-server Unix socket. That socket speaks WebSocket, not JSONL, so the
 * Android client must perform the upgrade and frame JSON-RPC messages itself.
 */
internal class SshWebSocketChannel(
    private val input: InputStream,
    private val output: OutputStream,
    private val random: SecureRandom = SecureRandom(),
) {
    private val stateLock = Any()
    @Volatile
    private var connected = false
    @Volatile
    private var closeSent = false

    fun connect() {
        check(!connected) { "WebSocket channel is already connected" }
        val keyBytes = ByteArray(16).also(random::nextBytes)
        val key = Base64.getEncoder().encodeToString(keyBytes)
        val request = buildString {
            append("GET / HTTP/1.1\r\n")
            append("Host: localhost\r\n")
            append("Upgrade: websocket\r\n")
            append("Connection: Upgrade\r\n")
            append("Sec-WebSocket-Key: ").append(key).append("\r\n")
            append("Sec-WebSocket-Version: 13\r\n\r\n")
        }
        synchronized(output) {
            output.write(request.toByteArray(StandardCharsets.US_ASCII))
            output.flush()
        }

        val response = readHttpHeaders()
        validateUpgrade(response, key)
        connected = true
    }

    fun writeText(message: String) {
        synchronized(stateLock) {
            check(connected && !closeSent) { "WebSocket channel is not connected" }
            writeFrame(OPCODE_TEXT, message.toByteArray(StandardCharsets.UTF_8))
        }
    }

    /** Sends an unsolicited ping frame to keep the proxy channel from going idle. */
    fun writePing() {
        synchronized(stateLock) {
            if (!connected || closeSent) return
            writeFrame(OPCODE_PING, byteArrayOf())
        }
    }

    /** Reads one complete UTF-8 text message, answering server pings inline. */
    fun readText(): String {
        check(connected) { "WebSocket channel is not connected" }
        var fragmentedMessage: ByteArrayOutputStream? = null
        while (true) {
            val frame = readFrame()
            when (frame.opcode) {
                OPCODE_TEXT -> {
                    if (fragmentedMessage != null) throw IOException("Unexpected nested WebSocket data frame")
                    if (frame.final) return decodeUtf8(frame.payload)
                    fragmentedMessage = ByteArrayOutputStream(frame.payload.size).apply {
                        write(frame.payload)
                    }
                }

                OPCODE_CONTINUATION -> {
                    val message = fragmentedMessage
                        ?: throw IOException("Unexpected WebSocket continuation frame")
                    appendBounded(message, frame.payload)
                    if (frame.final) return decodeUtf8(message.toByteArray())
                }

                OPCODE_PING -> writeFrame(OPCODE_PONG, frame.payload)
                OPCODE_PONG -> Unit
                OPCODE_CLOSE -> {
                    synchronized(stateLock) {
                        if (!closeSent) {
                            closeSent = true
                            runCatching { writeFrame(OPCODE_CLOSE, frame.payload) }
                        }
                        connected = false
                    }
                    throw EOFException("Codex app-server closed the WebSocket connection")
                }

                OPCODE_BINARY -> throw IOException("Unexpected binary frame from Codex app-server")
                else -> throw IOException("Unsupported WebSocket opcode ${frame.opcode}")
            }
        }
    }

    fun close() {
        synchronized(stateLock) {
            if (!connected || closeSent) {
                connected = false
                return
            }
            closeSent = true
            runCatching { writeFrame(OPCODE_CLOSE, byteArrayOf()) }
            connected = false
        }
    }

    private fun readHttpHeaders(): String {
        val bytes = ByteArrayOutputStream()
        var matched = 0
        while (bytes.size() < MAX_HTTP_HEADER_BYTES) {
            val next = input.read()
            if (next < 0) throw EOFException("SSH channel closed during WebSocket handshake")
            bytes.write(next)
            matched = when {
                matched == 0 && next == '\r'.code -> 1
                matched == 1 && next == '\n'.code -> 2
                matched == 2 && next == '\r'.code -> 3
                matched == 3 && next == '\n'.code -> return bytes.toString(StandardCharsets.US_ASCII.name())
                next == '\r'.code -> 1
                else -> 0
            }
        }
        throw IOException("WebSocket handshake headers exceeded the size limit")
    }

    private fun validateUpgrade(response: String, key: String) {
        val lines = response.split("\r\n")
        val status = lines.firstOrNull().orEmpty()
        if (!status.matches(Regex("HTTP/1\\.[01] 101(?: .*)?"))) {
            throw IOException("Codex app-server proxy rejected WebSocket upgrade: ${status.take(120)}")
        }
        val headers = lines.drop(1).mapNotNull { line ->
            val separator = line.indexOf(':')
            if (separator <= 0) null else line.substring(0, separator).trim().lowercase() to
                line.substring(separator + 1).trim()
        }.groupBy({ it.first }, { it.second })
        if (headers["upgrade"].orEmpty().none { it.equals("websocket", ignoreCase = true) }) {
            throw IOException("Codex app-server proxy returned an invalid WebSocket upgrade response")
        }
        val connectionUpgrade = headers["connection"].orEmpty().any { value ->
            value.split(',').any { token -> token.trim().equals("upgrade", ignoreCase = true) }
        }
        if (!connectionUpgrade) {
            throw IOException("Codex app-server proxy returned an invalid WebSocket connection header")
        }
        val expectedAccept = Base64.getEncoder().encodeToString(
            MessageDigest.getInstance("SHA-1")
                .digest((key + WEBSOCKET_GUID).toByteArray(StandardCharsets.US_ASCII)),
        )
        val actualAccept = headers["sec-websocket-accept"]?.firstOrNull()
        if (actualAccept != expectedAccept) {
            throw IOException("Codex app-server proxy returned an invalid WebSocket accept key")
        }
    }

    private fun readFrame(): Frame {
        val first = readByte()
        val second = readByte()
        val final = (first and FIN_BIT) != 0
        if ((first and RESERVED_BITS) != 0) throw IOException("Unsupported WebSocket frame extensions")
        val opcode = first and OPCODE_MASK
        val masked = (second and MASK_BIT) != 0
        if (masked) throw IOException("Codex app-server sent a masked server frame")

        val payloadLength = when (val shortLength = second and LENGTH_MASK) {
            LENGTH_16_BIT -> readUnsignedShort().toLong()
            LENGTH_64_BIT -> readUnsignedLong()
            else -> shortLength.toLong()
        }
        if (payloadLength > MAX_FRAME_BYTES) throw IOException("WebSocket frame exceeded the size limit")
        if (opcode >= OPCODE_CLOSE && (!final || payloadLength > MAX_CONTROL_FRAME_BYTES)) {
            throw IOException("Invalid WebSocket control frame")
        }
        val payload = ByteArray(payloadLength.toInt())
        readFully(payload)
        return Frame(final = final, opcode = opcode, payload = payload)
    }

    private fun readUnsignedShort(): Int = (readByte() shl 8) or readByte()

    private fun readUnsignedLong(): Long {
        var result = 0L
        repeat(8) {
            val next = readByte()
            if (it == 0 && (next and 0x80) != 0) throw IOException("Invalid WebSocket payload length")
            result = (result shl 8) or next.toLong()
        }
        if (result > MAX_FRAME_BYTES) throw IOException("WebSocket frame exceeded the size limit")
        return result
    }

    private fun readByte(): Int {
        val result = input.read()
        if (result < 0) throw EOFException("SSH channel closed while reading a WebSocket frame")
        return result
    }

    private fun readFully(destination: ByteArray) {
        var offset = 0
        while (offset < destination.size) {
            val count = input.read(destination, offset, destination.size - offset)
            if (count < 0) throw EOFException("SSH channel closed while reading a WebSocket frame")
            if (count == 0) continue
            offset += count
        }
    }

    private fun writeFrame(opcode: Int, payload: ByteArray) {
        if (payload.size > MAX_FRAME_BYTES) throw IOException("WebSocket message exceeded the size limit")
        val mask = ByteArray(4).also(random::nextBytes)
        synchronized(output) {
            output.write(FIN_BIT or opcode)
            when {
                payload.size < LENGTH_16_BIT -> output.write(MASK_BIT or payload.size)
                payload.size <= MAX_UNSIGNED_SHORT -> {
                    output.write(MASK_BIT or LENGTH_16_BIT)
                    output.write((payload.size ushr 8) and 0xff)
                    output.write(payload.size and 0xff)
                }

                else -> {
                    output.write(MASK_BIT or LENGTH_64_BIT)
                    for (shift in 56 downTo 0 step 8) {
                        output.write((payload.size.toLong() ushr shift).toInt() and 0xff)
                    }
                }
            }
            output.write(mask)
            val maskedPayload = ByteArray(payload.size) { index ->
                (payload[index].toInt() xor mask[index % mask.size].toInt()).toByte()
            }
            output.write(maskedPayload)
            output.flush()
        }
    }

    private fun appendBounded(target: ByteArrayOutputStream, bytes: ByteArray) {
        if (target.size().toLong() + bytes.size > MAX_MESSAGE_BYTES) {
            throw IOException("WebSocket message exceeded the size limit")
        }
        target.write(bytes)
    }

    private fun decodeUtf8(bytes: ByteArray): String {
        val decoder: CharsetDecoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
        return try {
            decoder.decode(java.nio.ByteBuffer.wrap(bytes)).toString()
        } catch (error: Exception) {
            throw IOException("Codex app-server sent invalid UTF-8", error)
        }
    }

    private data class Frame(val final: Boolean, val opcode: Int, val payload: ByteArray)

    private companion object {
        const val WEBSOCKET_GUID = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11"
        const val MAX_HTTP_HEADER_BYTES = 16 * 1024
        const val MAX_FRAME_BYTES = 32 * 1024 * 1024L
        const val MAX_MESSAGE_BYTES = 64 * 1024 * 1024L
        const val MAX_CONTROL_FRAME_BYTES = 125L
        const val MAX_UNSIGNED_SHORT = 65_535
        const val FIN_BIT = 0x80
        const val RESERVED_BITS = 0x70
        const val OPCODE_MASK = 0x0f
        const val MASK_BIT = 0x80
        const val LENGTH_MASK = 0x7f
        const val LENGTH_16_BIT = 126
        const val LENGTH_64_BIT = 127
        const val OPCODE_CONTINUATION = 0
        const val OPCODE_TEXT = 1
        const val OPCODE_BINARY = 2
        const val OPCODE_CLOSE = 8
        const val OPCODE_PING = 9
        const val OPCODE_PONG = 10
    }
}
