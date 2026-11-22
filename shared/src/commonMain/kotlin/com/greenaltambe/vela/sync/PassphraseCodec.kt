package com.greenaltambe.vela.sync

/**
 * AES-256-GCM with a key from [passphrase] (PBKDF2-HMAC-SHA256). File layout: `VSY1` + 16-byte salt + 12-byte IV + ciphertext. A wrong code or a
 * damaged file fails the integrity check and throws [SyncDecodeException]; nothing wrong is ever applied.
 */
expect fun passphraseCodec(passphrase: String): SyncCodec

/** A readable random sync code like `K7QM-2XPD-9TNA-HW4R` (no look-alike characters), about 80 bits. */
fun generateSyncCode(random: kotlin.random.Random = kotlin.random.Random.Default): String {
    val alphabet = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    return (0 until 4).joinToString("-") { (0 until 4).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("") }
}

/** Codes are typed by hand: ignore case, spaces and dashes so `k7qm 2xpd…` works. */
fun normalizeSyncCode(code: String): String = code.filter { it.isLetterOrDigit() }.uppercase()
