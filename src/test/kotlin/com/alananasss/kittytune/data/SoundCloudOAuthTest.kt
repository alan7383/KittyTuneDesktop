package com.alananasss.kittytune.data

import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URI
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.*
import org.junit.Test

class SoundCloudOAuthTest {
    @Test fun `a port published during composition stays registered until the attempt leaves`() {
        val port = androidx.compose.runtime.mutableStateOf<Int?>(null)
        val ready = androidx.compose.runtime.mutableStateOf(false)
        val registered = mutableListOf<Int>()
        val closed = mutableListOf<Int>()
        val scene = androidx.compose.ui.ImageComposeScene(width = 20, height = 20) {
            androidx.compose.runtime.DisposableEffect(Unit) {
                // LoginAttempt creates the callback server here, after the first composition.
                port.value = 45678
                onDispose { }
            }
            com.alananasss.kittytune.ui.login.SoundCloudProtocolRegistration(
                port = port.value,
                prepare = { value ->
                    registered.add(value)
                    AutoCloseable { closed.add(value) }
                },
                onReady = { ready.value = true },
                onError = { throw AssertionError(it) }
            )
            if (ready.value) androidx.compose.foundation.layout.Box(androidx.compose.ui.Modifier)
        }
        try {
            repeat(15) { scene.render(it * 16_000_000L).close() }
            assertTrue(ready.value)
            assertEquals(listOf(45678), registered)
            assertTrue(closed.isEmpty(), "Recomposition must keep the active callback registered")
            port.value = 45679
            repeat(15) { scene.render((it + 15) * 16_000_000L).close() }
            assertEquals(listOf(45678, 45679), registered)
            assertEquals(listOf(45678), closed, "Only the old attempt is closed")
        } finally {
            scene.close()
        }
        assertEquals(listOf(45678, 45679), closed)
    }

    @Test fun `a new login cannot retain the previous accounts refresh token or scope`() {
        // TokenManager is process-global: run this only in the isolated headless-test profile.
        org.junit.Assume.assumeTrue(java.io.File(System.getProperty("user.home")).canonicalFile ==
            java.io.File("build/test-profile/home").canonicalFile)
        try {
            TokenManager.replaceSession("account-a", "refresh-a", 3600, "scope-a")
            TokenManager.replaceSession("account-b", null, 120, null)
            assertEquals("account-b", TokenManager.getAccessToken())
            assertNull(TokenManager.getRefreshToken())
            assertNull(TokenManager.getTokenScope())
            assertFalse(TokenManager.isGuestMode())
            assertTrue(TokenManager.getAccessTokenExpiresAt() <= System.currentTimeMillis() + 120_000)
        } finally {
            TokenManager.clearTokens()
        }
    }

    @Test fun `Windows handler forwards once and ignores expired callbacks without script errors`() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Windows"))
        val dir = java.nio.file.Files.createTempDirectory("sc-oauth-handler").toFile()
        val portFile = java.io.File(dir, "port.txt")
        val handler = java.io.File(dir, "handler.vbs")
        handler.writeText(WindowsSoundCloudOAuth.script(portFile))
        val received = LinkedBlockingQueue<SoundCloudOAuthCallback.Result>()
        fun runHandler(url: String) {
            // Do not use //B here: otherwise WSH would hide a regression in script error handling.
            val process = ProcessBuilder("cscript.exe", "//NoLogo", handler.absolutePath, url)
                .redirectErrorStream(true).start()
            assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Handler did not terminate")
            assertEquals("", process.inputStream.bufferedReader().readText().trim())
            assertEquals(0, process.exitValue())
        }
        try {
            SoundCloudOAuthServer("s") { received.add(it) }.use { server ->
                portFile.writeText(server.port.toString())
                runHandler("sc://auth")
                assertNull(received.poll())
                runHandler("sc://auth?code=ok&state=s")
                assertEquals("ok", received.poll(1, TimeUnit.SECONDS)?.code)
                runHandler("sc://auth?code=duplicate&state=s")
                assertNull(received.poll())
            }
            runHandler("sc://auth?code=late&state=s")
            SoundCloudOAuthServer("next") { received.add(it) }.use { server ->
                portFile.writeText(server.port.toString())
                runHandler("sc://auth/?code=with-slash&state=next")
                assertEquals("with-slash", received.poll(1, TimeUnit.SECONDS)?.code)
            }
            portFile.writeText("invalid")
            runHandler("sc://auth?code=late&state=s")
            portFile.delete()
            runHandler("sc://auth?code=late&state=s")
        } finally {
            dir.listFiles()?.forEach { it.delete() }
            dir.delete()
        }
    }

    @Test fun `decode values once and preserve encoded separators`() {
        val result = SoundCloudOAuthCallback.parse("sc://auth?code=a%26b%3Dc%2B%2520&state=s%2Bt", "s+t")
        assertEquals("a&b=c+%20", result?.code)
        assertEquals("denied", SoundCloudOAuthCallback.parse("sc://auth?error=denied&state=s", "s")?.error)
        assertEquals("ok", SoundCloudOAuthCallback.parse("/callback?code=ok&state=s", "s")?.code)
    }

    @Test fun `reject probes wrong state malformed or ambiguous callbacks`() {
        listOf(
            "sc://auth", "sc://auth?code=x", "sc://auth?code=x&state=wrong",
            "sc://evil?code=x&state=s", "https://auth?code=x&state=s",
            "sc://auth/path?code=x&state=s", "sc://auth?code=x&state=s#fragment",
            "sc://auth?code=x&state=s&state=s", "sc://auth?code=x&%73tate=s&state=s",
            "sc://auth?code=x&state=s&error=denied", "sc://auth?code=&state=s",
            "sc://auth?code=%ZZ&state=s", "//evil/callback?code=x&state=s",
            "sc://user@auth?code=x&state=s", "sc://auth:123?code=x&state=s",
            "sc://auth?code=" + "x".repeat(17000) + "&state=s"
        ).forEach { assertNull(SoundCloudOAuthCallback.parse(it, "s"), it.take(100)) }
    }

    @Test fun `loopback accepts one matching callback and closes on disposal`() {
        val received = LinkedBlockingQueue<SoundCloudOAuthCallback.Result>()
        val server = SoundCloudOAuthServer("s") { received.add(it) }
        val port = server.port
        fun request(body: String, path: String = "/callback", method: String = "POST"): Int {
            val connection = URI("http://127.0.0.1:$port$path").toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
            try {
                connection.connectTimeout = 1000
                connection.readTimeout = 1000
                connection.requestMethod = method
                if (method == "POST") {
                    connection.doOutput = true
                    connection.outputStream.use { it.write(body.toByteArray()) }
                }
                return connection.responseCode
            } finally { connection.disconnect() }
        }
        try {
            assertEquals(400, request("sc://auth?code=x&state=wrong"))
            assertEquals(400, request("sc://auth?code=x&state=s", "/callback/other"))
            assertEquals(400, request("x".repeat(17000)))
            assertEquals(400, request("", method = "PUT"))
            assertNull(received.poll())
            assertEquals(200, request("sc://auth?code=a%26b&state=s"))
            assertEquals("a&b", received.poll(1, TimeUnit.SECONDS)?.code)
            assertEquals(409, request("sc://auth?code=again&state=s"))
            assertNull(received.poll())
        } finally { server.close(); server.close() }
        assertFails { request("sc://auth?code=late&state=s") }
    }

    @Test fun `a stale attempt cannot authenticate a new server`() {
        val received = LinkedBlockingQueue<SoundCloudOAuthCallback.Result>()
        SoundCloudOAuthServer("new-state") { received.add(it) }.use { server ->
            val connection = URI("http://127.0.0.1:${server.port}/callback?code=old&state=old-state")
                .toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
            try { assertEquals(400, connection.responseCode) } finally { connection.disconnect() }
            assertNull(received.poll())
        }
    }

    @Test fun `macOS native registration symbols are available`() {
        org.junit.Assume.assumeTrue(System.getProperty("os.name").startsWith("Mac"))
        // Resolve the real frameworks without changing the user's default sc handler.
        val cf = com.sun.jna.NativeLibrary.getInstance("CoreFoundation")
        val ls = com.sun.jna.NativeLibrary.getInstance("CoreServices")
        listOf("CFURLCreateFromFileSystemRepresentation", "CFStringCreateWithCString", "CFRelease")
            .forEach { assertNotNull(cf.getFunction(it)) }
        listOf("LSRegisterURL", "LSSetDefaultHandlerForURLScheme")
            .forEach { assertNotNull(ls.getFunction(it)) }
    }

    @Test fun `provider denial is delivered as an error`() {
        val received = LinkedBlockingQueue<SoundCloudOAuthCallback.Result>()
        SoundCloudOAuthServer("s") { received.add(it) }.use { server ->
            val connection = URI("http://127.0.0.1:${server.port}/callback?error=access_denied&state=s")
                .toURL().openConnection(Proxy.NO_PROXY) as HttpURLConnection
            try { assertEquals(200, connection.responseCode) } finally { connection.disconnect() }
            assertEquals("access_denied", received.poll(1, TimeUnit.SECONDS)?.error)
        }
    }
}
