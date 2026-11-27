package com.alananasss.kittytune

import com.alananasss.kittytune.ui.player.ArtworkPalette
import com.sun.net.httpserver.HttpServer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.InetSocketAddress
import javax.imageio.ImageIO
import org.junit.Assert.assertNotNull
import org.junit.Test

class ArtworkPaletteTest {

    @Test
    fun httpArtworkIsStillReadableAfterTheResponseCloses() {
        val imageBytes = ByteArrayOutputStream().use { out ->
            val image = BufferedImage(16, 16, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.color = Color(220, 80, 40)
            graphics.fillRect(0, 0, 16, 16)
            graphics.dispose()
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cover.png") { exchange ->
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, imageBytes.size.toLong())
            exchange.responseBody.use { it.write(imageBytes) }
        }
        server.start()
        try {
            val loaded = ArtworkPalette.load(
                "http://127.0.0.1:${server.address.port}/cover.png"
            )
            assertNotNull("Artwork must remain decodable after the HTTP response is closed", loaded)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun extractTrackColorsCachesAllRolesAndServesInstantHits() {
        val imageBytes = ByteArrayOutputStream().use { out ->
            val image = BufferedImage(32, 32, BufferedImage.TYPE_INT_RGB)
            val graphics = image.createGraphics()
            graphics.color = Color(20, 150, 170)
            graphics.fillRect(0, 0, 32, 32)
            graphics.dispose()
            ImageIO.write(image, "png", out)
            out.toByteArray()
        }

        var requests = 0
        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/cached-cover.png") { exchange ->
            requests++
            exchange.responseHeaders.add("Content-Type", "image/png")
            exchange.sendResponseHeaders(200, imageBytes.size.toLong())
            exchange.responseBody.use { it.write(imageBytes) }
        }
        server.start()
        try {
            val url = "http://127.0.0.1:${server.address.port}/cached-cover.png"
            org.junit.Assert.assertNull(ArtworkPalette.cachedTrackColors(url))

            val first = ArtworkPalette.extractTrackColors(url)
            assertNotNull(first)
            org.junit.Assert.assertTrue(first!!.meshColors.isNotEmpty())
            org.junit.Assert.assertEquals(1, requests)

            val cached = ArtworkPalette.cachedTrackColors(url)
            assertNotNull(cached)
            org.junit.Assert.assertEquals(first, cached)

            val second = ArtworkPalette.extractTrackColors(url)
            org.junit.Assert.assertEquals(first, second)
            org.junit.Assert.assertEquals("Cached track colors must not re-fetch over HTTP", 1, requests)
        } finally {
            server.stop(0)
        }
    }
}
