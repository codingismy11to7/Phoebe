package com.phoebe.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode
import kotlin.math.roundToInt

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    runCatching {
        val source = Image.makeFromEncoded(bytes)
        try {
            val sourceWidth = source.imageInfo.width
            val sourceHeight = source.imageInfo.height
            val targetMax = maxDimension.takeIf { it > 0 } ?: Int.MAX_VALUE
            val largestDimension = maxOf(sourceWidth, sourceHeight)
            if (largestDimension <= targetMax) {
                return@runCatching source.toComposeImageBitmap()
            }

            val scale = targetMax.toFloat() / largestDimension.toFloat()
            val targetWidth = (sourceWidth * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (sourceHeight * scale).roundToInt().coerceAtLeast(1)
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(targetWidth, targetHeight)
            val pixmap = bitmap.peekPixels() ?: run {
                bitmap.close()
                return@runCatching null
            }
            if (!source.scalePixels(pixmap, SamplingMode.LINEAR, false)) {
                bitmap.close()
                return@runCatching null
            }
            bitmap.asComposeImageBitmap()
        } finally {
            source.close()
        }
    }.getOrNull()
