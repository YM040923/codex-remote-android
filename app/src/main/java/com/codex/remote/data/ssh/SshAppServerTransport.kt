package com.codex.remote.data.ssh

import android.content.Context
import com.codex.remote.domain.AuthType
import com.codex.remote.domain.ConnectionSecrets
import com.codex.remote.domain.RemotePlatform
import com.codex.remote.domain.SavedConnection
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.schmizz.sshj.SSHClient
import net.schmizz.sshj.DefaultSecurityProviderConfig
import net.schmizz.sshj.common.Buffer
import net.schmizz.sshj.connection.channel.direct.Session
import net.schmizz.sshj.transport.verification.HostKeyVerifier
import net.schmizz.sshj.userauth.keyprovider.KeyProviderUtil
import net.schmizz.sshj.userauth.keyprovider.OpenSSHKeyFile
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile
import net.schmizz.sshj.userauth.password.PasswordFinder
import net.schmizz.sshj.userauth.password.Resource
import java.io.Closeable
import java.io.StringReader
import java.security.MessageDigest
import java.security.PublicKey
import java.util.Base64
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.TimeUnit

private const val WEBSOCKET_HANDSHAKE_TIMEOUT_MS = 30_000

class HostKeyChangedException(
    expected: String,
    actual: String,
) : SecurityException("SSH 主机密钥已变更。已保存 $expected，当前为 $actual")

class UnknownHostKeyException(val fingerprint: String) :
    SecurityException("首次连接需要确认 SSH 主机指纹：$fingerprint")

class RemoteCodexUnavailableException(message: String) : IllegalStateException(message)

class ActiveSshTransport internal constructor(
    private val ssh: SSHClient,
    private val session: Session,
    private val command: Session.Command,
    val fingerprint: String,
    val remotePlatform: RemotePlatform,
    val codexVersion: String,
) : Closeable {
    private val webSocket = SshWebSocketChannel(command.inputStream, command.outputStream)
    val errorReader = command.errorStream.bufferedReader(Charsets.UTF_8)
    private val closed = AtomicBoolean(false)

    internal suspend fun connect() = withContext(Dispatchers.IO) {
        ssh.timeout = WEBSOCKET_HANDSHAKE_TIMEOUT_MS
        try {
            webSocket.connect()
        } finally {
            // The long-lived app-server stream must not inherit a socket-idle timeout.
            ssh.timeout = 0
        }
    }

    suspend fun writeMessage(message: String) = withContext(Dispatchers.IO) {
        webSocket.writeText(message)
    }

    suspend fun readMessage(): String = withContext(Dispatchers.IO) { webSocket.readText() }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        webSocket.close()
        runCatching { command.close() }
        runCatching { session.close() }
        runCatching { ssh.disconnect() }
        runCatching { ssh.close() }
    }
}

class SshAppServerTransportFactory(private val context: Context) {
    suspend fun open(
        connection: SavedConnection,
        secrets: ConnectionSecrets,
    ): ActiveSshTransport = withContext(Dispatchers.IO) {
        var observedFingerprint = ""
        val ssh = authenticatedClient(connection, secrets) { observedFingerprint = it }
        try {
            val remotePlatform = resolvePlatform(ssh, connection.platform)
            val codexVersion = readCodexVersion(ssh, remotePlatform)
            val daemon = readAppServerDaemon(ssh, remotePlatform)
            if (daemon.status != "running") {
                throw RemoteCodexUnavailableException(
                    "远端 Codex 共享 app-server 当前未运行。请在 Codex Desktop 所连接的远端环境启用共享服务，" +
                        "并确认两端使用同一远端用户和 CODEX_HOME；Android 不会擅自启动一个无法接管原对话的新服务。",
                )
            }
            ssh.timeout = 0
            val session = ssh.startSession()
            try {
                val command = session.exec(appServerProxyCommand(remotePlatform, daemon.socketPath))
                val transport = ActiveSshTransport(
                    ssh = ssh,
                    session = session,
                    command = command,
                    fingerprint = observedFingerprint,
                    remotePlatform = remotePlatform,
                    codexVersion = codexVersion,
                )
                try {
                    transport.connect()
                    transport
                } catch (error: Throwable) {
                    runCatching { transport.close() }
                    throw error
                }
            } catch (error: Throwable) {
                runCatching { session.close() }
                throw error
            }
        } catch (error: Throwable) {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
            throw error
        }
    }

    private fun authenticatedClient(
        connection: SavedConnection,
        secrets: ConnectionSecrets,
        onFingerprint: (String) -> Unit,
    ): SSHClient {
        val ssh = SSHClient(androidCompatibleSshConfig())
        var unknownFingerprint: String? = null
        var changedFingerprint: String? = null
        ssh.connectTimeout = 15_000
        ssh.timeout = 30_000
        ssh.addHostKeyVerifier(object : HostKeyVerifier {
            override fun verify(hostname: String, port: Int, key: PublicKey): Boolean {
                val actual = sha256Fingerprint(key)
                onFingerprint(actual)
                val expected = connection.hostKeyFingerprint
                if (expected.isBlank()) {
                    unknownFingerprint = actual
                    return false
                }
                if (expected != actual) {
                    changedFingerprint = actual
                    return false
                }
                return true
            }

            override fun findExistingAlgorithms(hostname: String, port: Int): List<String> = emptyList()
        })

        try {
            ssh.connect(connection.host, connection.port)
            when (connection.authType) {
                AuthType.PASSWORD -> ssh.authPassword(connection.username, secrets.password)
                AuthType.PRIVATE_KEY -> authenticatePrivateKey(ssh, connection.username, secrets)
            }
            return ssh
        } catch (error: Throwable) {
            runCatching { ssh.disconnect() }
            runCatching { ssh.close() }
            unknownFingerprint?.let { throw UnknownHostKeyException(it) }
            changedFingerprint?.let { throw HostKeyChangedException(connection.hostKeyFingerprint, it) }
            throw error
        }
    }

    private fun authenticatePrivateKey(
        ssh: SSHClient,
        username: String,
        secrets: ConnectionSecrets,
    ) {
        val provider = when (
            KeyProviderUtil.detectKeyFileFormat(StringReader(secrets.privateKey), true)
        ) {
            net.schmizz.sshj.userauth.keyprovider.KeyFormat.PKCS8 -> PKCS8KeyFile()
            else -> OpenSSHKeyFile()
        }
        provider.init(
            StringReader(secrets.privateKey),
            if (secrets.passphrase.isBlank()) null else object : PasswordFinder {
                override fun reqPassword(resource: Resource<*>?): CharArray = secrets.passphrase.toCharArray()
                override fun shouldRetry(resource: Resource<*>?): Boolean = false
            },
        )
        ssh.authPublickey(username, provider)
    }

    private fun sha256Fingerprint(key: PublicKey): String {
        val wireKey = Buffer.PlainBuffer().putPublicKey(key).compactData
        val digest = MessageDigest.getInstance("SHA-256").digest(wireKey)
        return "SHA256:" + Base64.getEncoder().withoutPadding().encodeToString(digest)
    }

    private fun resolvePlatform(ssh: SSHClient, configured: RemotePlatform): RemotePlatform {
        if (configured != RemotePlatform.AUTO) return configured
        val probe = runCommand(ssh, "printf '__CODEX_POSIX__'")
        return if (probe.exitStatus == 0 && probe.stdout.contains("__CODEX_POSIX__")) {
            RemotePlatform.POSIX
        } else {
            RemotePlatform.WINDOWS
        }
    }

    private fun readCodexVersion(ssh: SSHClient, platform: RemotePlatform): String {
        val probe = runCommand(ssh, codexVersionCommand(platform))
        val version = probe.stdout.lineSequence()
            .map(String::trim)
            .firstOrNull { it.startsWith("codex-cli ") || it.startsWith("codex ") }
        if (probe.exitStatus != 0 || version == null) {
            val detail = probe.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                ?: probe.stdout.lineSequence().lastOrNull { it.isNotBlank() }
                ?: "codex --version 未返回版本"
            throw RemoteCodexUnavailableException(
                "远端登录 shell 找不到可用的 Codex CLI。请先在远端运行 codex --version 并完成安装。$detail",
            )
        }
        return version.substringAfter(' ').trim()
    }

    private fun readAppServerDaemon(ssh: SSHClient, platform: RemotePlatform): AppServerDaemonStatus {
        val probe = runCommand(ssh, appServerDaemonVersionCommand(platform))
        if (probe.exitStatus != 0) {
            val detail = probe.stderr.lineSequence().lastOrNull { it.isNotBlank() }
                ?: probe.stdout.lineSequence().lastOrNull { it.isNotBlank() }
                ?: "codex app-server daemon version 未返回状态"
            throw RemoteCodexUnavailableException(
                "远端 Codex 不支持共享 app-server 连接（需要支持 codex app-server proxy/daemon 的版本）。$detail",
            )
        }
        val result = runCatching { Json.parseToJsonElement(probe.stdout.trim()).jsonObject }
            .getOrElse {
                throw RemoteCodexUnavailableException("远端 Codex app-server daemon 返回了无法识别的状态")
            }
        val status = result["status"]?.jsonPrimitive?.contentOrNull
            ?: throw RemoteCodexUnavailableException("远端 Codex app-server daemon 未报告运行状态")
        if (!status.equals("running", ignoreCase = true)) {
            return AppServerDaemonStatus(status = status.lowercase(), socketPath = "")
        }
        val socketPath = result["socketPath"]?.jsonPrimitive?.contentOrNull
            ?.takeIf(String::isNotBlank)
            ?: throw RemoteCodexUnavailableException("远端 Codex app-server daemon 未报告控制套接字")
        return AppServerDaemonStatus(status = status.lowercase(), socketPath = socketPath)
    }

    private fun runCommand(ssh: SSHClient, commandLine: String): ProbeResult {
        val session = ssh.startSession()
        return try {
            val command = session.exec(commandLine)
            try {
                val stdout = StringBuilder()
                val stderr = StringBuilder()
                val stdoutReader = Thread {
                    command.inputStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { stdout.appendLine(it) }
                    }
                }
                val stderrReader = Thread {
                    command.errorStream.bufferedReader(Charsets.UTF_8).use { reader ->
                        reader.forEachLine { stderr.appendLine(it) }
                    }
                }
                stdoutReader.start()
                stderrReader.start()
                command.join(PROBE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (command.isOpen) {
                    throw RemoteCodexUnavailableException("远端 Codex 预检超时")
                }
                stdoutReader.join(TimeUnit.SECONDS.toMillis(2))
                stderrReader.join(TimeUnit.SECONDS.toMillis(2))
                ProbeResult(
                    exitStatus = command.exitStatus ?: -1,
                    stdout = stdout.toString(),
                    stderr = stderr.toString(),
                )
            } finally {
                runCatching { command.close() }
            }
        } finally {
            runCatching { session.close() }
        }
    }

    private data class ProbeResult(
        val exitStatus: Int,
        val stdout: String,
        val stderr: String,
    )

    private data class AppServerDaemonStatus(
        val status: String,
        val socketPath: String,
    )

    companion object {
        private const val PROBE_TIMEOUT_SECONDS = 15L
    }
}

internal fun codexVersionCommand(platform: RemotePlatform): String = when (platform) {
    RemotePlatform.AUTO -> error("AUTO platform must be resolved before building a Codex command")
    RemotePlatform.POSIX -> "exec \"\${SHELL:-/bin/sh}\" -lc 'codex --version'"
    RemotePlatform.WINDOWS ->
        "powershell.exe -NoLogo -NonInteractive -Command \"& { codex --version }\""
}

internal fun appServerDaemonVersionCommand(platform: RemotePlatform): String = when (platform) {
    RemotePlatform.AUTO -> error("AUTO platform must be resolved before building a Codex command")
    RemotePlatform.POSIX -> "exec \"\${SHELL:-/bin/sh}\" -lc 'exec codex app-server daemon version'"
    RemotePlatform.WINDOWS ->
        "powershell.exe -NoLogo -NonInteractive -Command \"& { codex app-server daemon version; exit \$LASTEXITCODE }\""
}

internal fun appServerProxyCommand(platform: RemotePlatform, socketPath: String): String = when (platform) {
    RemotePlatform.AUTO -> error("AUTO platform must be resolved before building a Codex command")
    RemotePlatform.POSIX -> {
        val proxyCommand = "exec codex app-server proxy --sock ${posixShellQuote(socketPath)}"
        "exec \"\${SHELL:-/bin/sh}\" -lc ${posixShellQuote(proxyCommand)}"
    }
    RemotePlatform.WINDOWS -> {
        "powershell.exe -NoLogo -NonInteractive -Command \"& { & codex app-server proxy --sock " +
            "${powershellSingleQuote(socketPath)}; exit \$LASTEXITCODE }\""
    }
}

private fun posixShellQuote(value: String): String = "'${value.replace("'", "'\"'\"'")}'"

private fun powershellSingleQuote(value: String): String = "'${value.replace("'", "''")}'"

internal fun androidCompatibleSshConfig() = DefaultSecurityProviderConfig().apply {
    keyExchangeFactories = keyExchangeFactories.filterNot {
        it.name.contains("curve25519", ignoreCase = true)
    }
}
