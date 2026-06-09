package com.phoebe.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import javax.imageio.ImageIO
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ImageInfo

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    runCatching {
        val inputStream = ByteArrayInputStream(bytes)
        val imageInputStream = ImageIO.createImageInputStream(inputStream) ?: return@runCatching null
        imageInputStream.use { stream ->
            val readers = ImageIO.getImageReaders(stream)
            if (!readers.hasNext()) return@runCatching null
            val reader = readers.next()
            try {
                reader.input = stream
                val width = reader.getWidth(0)
                val height = reader.getHeight(0)
                if (width <= 0 || height <= 0) return@runCatching null

                val targetMax = maxDimension.takeIf { it > 0 } ?: Int.MAX_VALUE
                var subsample = 1
                val longest = maxOf(width, height)
                while (longest / subsample > targetMax) {
                    subsample *= 2
                }

                val param = reader.defaultReadParam.apply {
                    setSourceSubsampling(subsample, subsample, 0, 0)
                }
                val buffered = reader.read(0, param) as BufferedImage
                bufferedToImageBitmap(buffered)
            } finally {
                reader.dispose()
            }
        }
    }.getOrNull()

private fun bufferedToImageBitmap(image: BufferedImage): ImageBitmap {
    val width = image.width
    val height = image.height
    val pixels = IntArray(width * height)
    image.getRGB(0, 0, width, height, pixels, 0, width)
    val rgbaBytes = ByteArray(width * height * 4)
    var offset = 0
    for (pixel in pixels) {
        rgbaBytes[offset++] = ((pixel shr 16) and 0xFF).toByte()
        rgbaBytes[offset++] = ((pixel shr 8) and 0xFF).toByte()
        rgbaBytes[offset++] = (pixel and 0xFF).toByte()
        rgbaBytes[offset++] = ((pixel shr 24) and 0xFF).toByte()
    }
    val bitmap = Bitmap()
    bitmap.installPixels(
        ImageInfo.makeN32(width, height, ColorAlphaType.PREMUL),
        rgbaBytes,
        width * 4,
    )
    return bitmap.asComposeImageBitmap()
}
