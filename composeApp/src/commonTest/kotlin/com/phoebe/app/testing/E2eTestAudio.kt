package com.phoebe.app.testing

/** Minimal ID3-tagged bytes so folder scanners treat the file as an MP3. */
fun minimalMp3Bytes(): ByteArray =
    byteArrayOf(0x49, 0x44, 0x33, 0x04, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00)
