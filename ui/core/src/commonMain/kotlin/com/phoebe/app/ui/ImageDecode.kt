package com.phoebe.app.ui

import androidx.compose.ui.graphics.ImageBitmap

expect fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int = Int.MAX_VALUE): ImageBitmap?
