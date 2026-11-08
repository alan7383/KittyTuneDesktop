package com.alananasss.kittytune.audio

import com.alananasss.kittytune.data.drm.HlsEncryptionInfo
import com.alananasss.kittytune.data.drm.PlayReadyInfo
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress
import java.nio.file.Files
import java.util.Base64

class WindowsProtectedMediaTest {
    private val info = PlayReadyInfo("https://license.media-streaming.soundcloud.cloud/playback/playready",
        "16fb34888bfa5904a6716e32caa329ae", "207b5456-08db-4b24-9177-4e8b6aab368f", "AESCTR", emptyMap())

    @Test fun cencInitializationPreservesThePlaylistKeyAndDomainByteOrder() {
        val data = WindowsProtectedMedia.initializationData(info)
        val xml = requireNotNull(HlsEncryptionInfo.playReadyHeader(data))
        assertTrue(xml.contains("<KID>iDT7FvqLBFmmcW4yyqMprg==</KID>"))
        assertEquals(info.keyIdHex, HlsEncryptionInfo.playReadyInfo(xml).keyIdHex)
        assertEquals(info.domainServiceId, HlsEncryptionInfo.playReadyInfo(xml).domainServiceId)
    }

    @Test fun encryptedFragmentsRemainByteIdenticalAndOrdered() = withFixture(false) { root, url ->
        val result = WindowsProtectedMedia.prepare(root, url)
        assertEquals(2500L, result.getLong("durationMs"))
        assertArrayEquals(byteArrayOf(1, 2, 3, 8, 7, 6, 9, 5), java.io.File(root, result.getString("mediaFile")).readBytes())
        assertTrue(java.io.File(root, "initData.bin").isFile)
        assertFalse(root.listFiles()!!.any { it.name.startsWith("fragments-") || it.name.endsWith(".part") })
    }

    @Test fun refusedFragmentNeverPublishesAnIncompleteMediaFile() = withFixture(true) { root, url ->
        assertTrue(runCatching { WindowsProtectedMedia.prepare(root, url) }.isFailure)
        assertFalse(root.listFiles()!!.any { it.extension == "mp4" || it.name.startsWith("fragments-") || it.name.endsWith(".part") })
    }

    private fun withFixture(refuse: Boolean, body: (java.io.File, String) -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val root = Files.createTempDirectory("encrypted-playready-media").toFile()
        val header = Base64.getEncoder().encodeToString(WindowsProtectedMedia.initializationData(info))
        val playlist = """
            #EXTM3U
            #EXT-X-KEY:METHOD=SAMPLE-AES-CTR,KEYFORMAT="com.microsoft.playready",URI="data:text/plain;base64,$header"
            #EXT-X-MAP:URI="init.mp4"
            #EXTINF:1.0,
            a.m4s
            #EXTINF:1.5,
            b.m4s
            #EXT-X-ENDLIST
        """.trimIndent().toByteArray()
        val data = mapOf("/playlist.m3u8" to playlist, "/init.mp4" to byteArrayOf(1, 2, 3),
            "/a.m4s" to byteArrayOf(8, 7, 6), "/b.m4s" to byteArrayOf(9, 5))
        server.createContext("/") { exchange ->
            val bytes = data.getValue(exchange.requestURI.path)
            exchange.sendResponseHeaders(if (refuse && exchange.requestURI.path == "/b.m4s") 403 else 200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()
        try { body(root, "http://127.0.0.1:${server.address.port}/playlist.m3u8") }
        finally { server.stop(0); root.deleteRecursively() }
    }
}
