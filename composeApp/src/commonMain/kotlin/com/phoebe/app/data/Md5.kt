package com.phoebe.app.data

private val Md5Shifts = intArrayOf(
    7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22, 7, 12, 17, 22,
    5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20, 5, 9, 14, 20,
    4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23, 4, 11, 16, 23,
    6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21, 6, 10, 15, 21,
)

private val Md5Table = IntArray(64) { i ->
    (kotlin.math.abs(kotlin.math.sin((i + 1).toDouble())) * 4294967296.0).toLong().toInt()
}

internal fun md5Hex(input: String): String {
    val bytes = input.encodeToByteArray()
    val bitLength = bytes.size.toLong() * 8L
    val paddedLength = (((bytes.size + 8) / 64) + 1) * 64
    val padded = ByteArray(paddedLength)
    bytes.copyInto(padded)
    padded[bytes.size] = 0x80.toByte()
    for (i in 0 until 8) {
        padded[paddedLength - 8 + i] = ((bitLength ushr (8 * i)) and 0xff).toByte()
    }

    var a0 = 0x67452301
    var b0 = 0xefcdab89.toInt()
    var c0 = 0x98badcfe.toInt()
    var d0 = 0x10325476

    val m = IntArray(16)
    for (offset in padded.indices step 64) {
        for (i in 0 until 16) {
            val j = offset + i * 4
            m[i] = (padded[j].toInt() and 0xff) or
                ((padded[j + 1].toInt() and 0xff) shl 8) or
                ((padded[j + 2].toInt() and 0xff) shl 16) or
                ((padded[j + 3].toInt() and 0xff) shl 24)
        }

        var a = a0
        var b = b0
        var c = c0
        var d = d0

        for (i in 0 until 64) {
            val (f, g) = when (i) {
                in 0..15 -> ((b and c) or (b.inv() and d)) to i
                in 16..31 -> ((d and b) or (d.inv() and c)) to ((5 * i + 1) % 16)
                in 32..47 -> (b xor c xor d) to ((3 * i + 5) % 16)
                else -> (c xor (b or d.inv())) to ((7 * i) % 16)
            }
            val temp = d
            d = c
            c = b
            b += (a + f + Md5Table[i] + m[g]).rotateLeft(Md5Shifts[i])
            a = temp
        }

        a0 += a
        b0 += b
        c0 += c
        d0 += d
    }

    return intArrayOf(a0, b0, c0, d0).joinToString("") { word ->
        (0 until 4).joinToString("") { i ->
            ((word ushr (8 * i)) and 0xff).toString(16).padStart(2, '0')
        }
    }
}
