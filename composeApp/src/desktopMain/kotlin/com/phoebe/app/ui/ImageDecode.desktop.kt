package com.phoebe.app.ui

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.Image

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? =
    Image.makeFromEncoded(bytes).toComposeImageBitmap()
