package com.codex.remote.data.ssh

import com.codex.remote.domain.RemotePlatform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SshConfigTest {
    @Test
    fun excludesCurve25519ThatAndroidCannotInstantiate() {
        val names = androidCompatibleSshConfig().keyExchangeFactories.map { it.name }

        assertFalse(names.any { it.contains("curve25519", ignoreCase = true) })
        assertTrue(names.any { it.contains("ecdh", ignoreCase = true) || it.contains("group14", ignoreCase = true) })
    }

    @Test
    fun posixCommandsUseTheRemoteLoginShell() {
        assertEquals(
            "exec \"\${SHELL:-/bin/sh}\" -lc 'codex --version'",
            codexVersionCommand(RemotePlatform.POSIX),
        )
        assertEquals(
            "exec \"\${SHELL:-/bin/sh}\" -lc 'exec codex app-server daemon version'",
            appServerDaemonVersionCommand(RemotePlatform.POSIX),
        )
        val proxy = appServerProxyCommand(RemotePlatform.POSIX, "/run/codex/app-server.sock")
        assertTrue(proxy.contains("codex app-server proxy --sock"))
        assertTrue(proxy.contains("/run/codex/app-server.sock"))
        assertTrue(proxy.contains("-lc '"))
    }

    @Test
    fun windowsCommandsUseTheDaemonAndQuoteSocketPaths() {
        val versionCommand = appServerDaemonVersionCommand(RemotePlatform.WINDOWS)
        val proxyCommand = appServerProxyCommand(RemotePlatform.WINDOWS, "C:\\Users\\Test O'Neil\\codex.sock")

        assertTrue(versionCommand.contains("powershell.exe"))
        assertTrue(versionCommand.contains("codex app-server daemon version"))
        assertFalse(versionCommand.contains("--listen stdio://"))
        assertTrue(proxyCommand.contains("codex app-server proxy --sock"))
        assertTrue(proxyCommand.contains("'C:\\Users\\Test O''Neil\\codex.sock'"))
    }
}
