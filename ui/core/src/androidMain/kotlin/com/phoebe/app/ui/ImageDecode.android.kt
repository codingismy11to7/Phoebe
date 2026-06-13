package com.phoebe.app.ui

import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeImageBitmap(bytes: ByteArray, maxDimension: Int): ImageBitmap? {
    if (maxDimension == Int.MAX_VALUE) {
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap()
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val sampleSize = decodeSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
    val decode = BitmapFactory.Options().apply {
        inSampleSize = sampleSize
        inPreferredConfig = if (maxDimension > ListArtworkMaxDecodeDimension) {
            android.graphics.Bitmap.Config.ARGB_8888
        } else {
            android.graphics.Bitmap.Config.RGB_565
        }
    }
    return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decode)?.asImageBitmap()
}

private fun decodeSampleSize(width: Int, height: Int, maxDimension: Int): Int {
    var sample = 1
    val longest = maxOf(width, height)
    while (longest / sample > maxDimension) {
        sample *= 2
    }
    return sample
}
