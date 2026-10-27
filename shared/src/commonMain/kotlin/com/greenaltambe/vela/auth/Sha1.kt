package com.greenaltambe.vela.auth

/** SHA-1 for SAPISIDHASH (Google's own scheme; not used for anything that needs collision resistance). */
object Sha1 {
    fun hex(input: String): String = hash(input.encodeToByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    fun hash(input: ByteArray): ByteArray {
        var h0 = 0x67452301; var h1 = -0x10325477; var h2 = -0x67452302; var h3 = 0x10325476; var h4 = -0x3c2d1e10
        val bitLength = input.size.toLong() * 8
        val padded = ByteArray(((input.size + 9 + 63) / 64) * 64)
        input.copyInto(padded)
        padded[input.size] = 0x80.toByte()
        for (i in 0 until 8) padded[padded.size - 1 - i] = (bitLength ushr (8 * i)).toByte()
        val w = IntArray(80)
        for (chunk in padded.indices step 64) {
            for (i in 0 until 16) w[i] = ((padded[chunk + i * 4].toInt() and 0xff) shl 24) or ((padded[chunk + i * 4 + 1].toInt() and 0xff) shl 16) or ((padded[chunk + i * 4 + 2].toInt() and 0xff) shl 8) or (padded[chunk + i * 4 + 3].toInt() and 0xff)
            for (i in 16 until 80) w[i] = (w[i - 3] xor w[i - 8] xor w[i - 14] xor w[i - 16]).rotl(1)
            var a = h0; var b = h1; var c = h2; var d = h3; var e = h4
            for (i in 0 until 80) {
                val (f, k) = when {
                    i < 20 -> ((b and c) or (b.inv() and d)) to 0x5a827999
                    i < 40 -> (b xor c xor d) to 0x6ed9eba1
                    i < 60 -> ((b and c) or (b and d) or (c and d)) to -0x70e44324
                    else -> (b xor c xor d) to -0x359d3e2a
                }
                val t = a.rotl(5) + f + e + k + w[i]
                e = d; d = c; c = b.rotl(30); b = a; a = t
            }
            h0 += a; h1 += b; h2 += c; h3 += d; h4 += e
        }
        val out = ByteArray(20)
        intArrayOf(h0, h1, h2, h3, h4).forEachIndexed { i, v -> for (j in 0 until 4) out[i * 4 + j] = (v ushr (24 - 8 * j)).toByte() }
        return out
    }

    private fun Int.rotl(n: Int) = (this shl n) or (this ushr (32 - n))
}
