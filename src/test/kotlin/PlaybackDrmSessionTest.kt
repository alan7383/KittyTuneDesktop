import com.alananasss.kittytune.data.drm.*
import com.sun.net.httpserver.HttpServer
import org.junit.Assert.*
import org.junit.Test
import java.net.InetSocketAddress

class PlaybackDrmSessionTest {
    private class Cdm : WidevineCdm {
        override val name = "single-session fixture"
        private var key = 0.toByte()
        override fun fork(): WidevineCdm = Cdm()
        override fun openSession(pssh: ByteArray): WidevineLicenseSession {
            key = pssh[0]
            return object : WidevineLicenseSession {
                override fun licenseRequest() = byteArrayOf(1)
                override fun provideLicense(license: ByteArray) = emptyList<ContentKey>()
                override fun decryptSample(data: ByteArray, iv: ByteArray, subsamples: List<Pair<Int, Int>>) = byteArrayOf(key)
                override fun close() {}
            }
        }
    }

    private fun encryption(key: Byte) = HlsEncryptionInfo("SAMPLE-AES", CencDecrypter.WIDEVINE_KEY_FORMAT,
        key.toString(), null, null, byteArrayOf(key))

    private fun withLicenseServer(cdm: WidevineCdm = Cdm(), block: () -> Unit) {
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        val oldUrl = SoundCloudWidevineLicense.playbackUrl
        server.createContext("/") { exchange ->
            exchange.requestBody.readBytes()
            exchange.sendResponseHeaders(200, 1)
            exchange.responseBody.use { it.write(byteArrayOf(1)) }
        }
        server.start()
        SoundCloudWidevineLicense.playbackUrl = "http://127.0.0.1:${server.address.port}/?token="
        WidevineCdmRegistry.install(cdm)
        try { block() } finally {
            WidevineCdmRegistry.install(null)
            SoundCloudWidevineLicense.playbackUrl = oldUrl
            server.stop(0)
        }
    }

    @Test fun overlappingStreamsKeepTheirOwnKeys() = withLicenseServer {
        val first = PlaybackDrmSession()
        val second = PlaybackDrmSession()
        val a = first.session(encryption(1), "first")
        val b = second.session(encryption(2), "second")
        try {
            assertArrayEquals(byteArrayOf(1), a.decryptSample(byteArrayOf(0), byteArrayOf(0), emptyList()))
            assertArrayEquals(byteArrayOf(2), b.decryptSample(byteArrayOf(0), byteArrayOf(0), emptyList()))
            first.release(a)
            assertArrayEquals(byteArrayOf(2), b.decryptSample(byteArrayOf(0), byteArrayOf(0), emptyList()))
        } finally { first.release(a); second.release(b) }
    }

    @Test fun replacedRegistryInvalidatesAnOldStream() = withLicenseServer {
        val owner = PlaybackDrmSession()
        val a = owner.session(encryption(1), "first")
        try {
            WidevineCdmRegistry.install(Cdm())
            assertTrue(a.isExpired)
            assertThrows(DrmLicenseException::class.java) {
                a.decryptSample(byteArrayOf(0), byteArrayOf(0), emptyList())
            }
            val b = owner.session(encryption(2), "second")
            assertArrayEquals(byteArrayOf(2), b.decryptSample(byteArrayOf(0), byteArrayOf(0), emptyList()))
            owner.release(b)
        } finally { owner.release(a) }
    }

    @Test fun failedLicenceLeavesThePrimaryInstanceAvailable() {
        val cdm = object : WidevineCdm by Cdm() {
            override fun fork(): WidevineCdm? = null
        }
        withLicenseServer(cdm) {
            val owner = PlaybackDrmSession()
            assertThrows(DrmLicenseException::class.java) { owner.session(encryption(1), "") }
            val session = owner.session(encryption(2), "valid")
            try {
                assertArrayEquals(byteArrayOf(2), session.decryptSample(byteArrayOf(0), byteArrayOf(0), emptyList()))
            } finally { owner.release(session) }
        }
    }
}
