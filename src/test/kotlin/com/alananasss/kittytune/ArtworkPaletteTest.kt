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
}
