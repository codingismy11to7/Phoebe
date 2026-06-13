package com.phoebe.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Image
import org.jetbrains.skia.SamplingMode

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    runCatching {
        val image = Image.makeFromEncoded(bytes)
        val longest = maxOf(image.width, image.height)
        val scaled = if (maxDimension == Int.MAX_VALUE || longest <= maxDimension) {
            image
        } else {
            val scale = maxDimension.toFloat() / longest
            val width = (image.width * scale).toInt().coerceAtLeast(1)
            val height = (image.height * scale).toInt().coerceAtLeast(1)
            val bitmap = Bitmap()
            bitmap.allocN32Pixels(width, height)
            image.scalePixels(bitmap.peekPixels()!!, SamplingMode.LINEAR, cache = false)
            Image.makeFromBitmap(bitmap)
        }
        scaled.toComposeImageBitmap()
    }.getOrNull()
