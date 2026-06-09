package com.phoebe.app.ui

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopImageDecodeTest {
    @Test
    fun decodeImageBitmapPreservesRedChannel() {
        val source = BufferedImage(8, 8, BufferedImage.TYPE_INT_RGB)
        val graphics = source.createGraphics()
        try {
            graphics.color = Color.RED
            graphics.fillRect(0, 0, source.width, source.height)
        } finally {
            graphics.dispose()
        }
        val bytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(source, "png", output)
            output.toByteArray()
        }

        val decoded = assertNotNull(decodeImageBitmap(bytes, maxDimension = 8))
        val pixel = IntArray(1)
        decoded.readPixels(pixel, startX = 4, startY = 4, width = 1, height = 1, bufferOffset = 0, stride = 1)
        val argb = pixel[0]
        val red = argb shr 16 and 0xFF
        val blue = argb and 0xFF

        assertTrue(red > 200, "expected red channel to dominate, got red=$red blue=$blue")
        assertTrue(blue < 80, "expected low blue channel, got red=$red blue=$blue")
    }

    @Test
    fun decodeImageBitmapHonorsMaxDimension() {
        val source = BufferedImage(800, 400, BufferedImage.TYPE_INT_ARGB)
        val graphics = source.createGraphics()
        try {
            graphics.color = Color.MAGENTA
            graphics.fillRect(0, 0, source.width, source.height)
        } finally {
            graphics.dispose()
        }
        val bytes = ByteArrayOutputStream().use { output ->
            ImageIO.write(source, "png", output)
            output.toByteArray()
        }

        val decoded = assertNotNull(decodeImageBitmap(bytes, maxDimension = 200))

        assertTrue(maxOf(decoded.width, decoded.height) <= 200)
        assertEquals(200, decoded.width)
        assertEquals(100, decoded.height)
    }
}
