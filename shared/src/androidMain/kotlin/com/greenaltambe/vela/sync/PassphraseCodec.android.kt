package com.greenaltambe.vela.sync

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

actual fun passphraseCodec(passphrase: String): SyncCodec = AesGcmCodec(passphrase)

private class AesGcmCodec(passphrase: String) : SyncCodec {
    private val secret = normalizeSyncCode(passphrase).toCharArray()
    private val random = SecureRandom()
    private val ownSalt = ByteArray(16).also { random.nextBytes(it) }
    private val keys = HashMap<String, SecretKeySpec>()

    private fun key(salt: ByteArray): SecretKeySpec = synchronized(keys) {
        keys.getOrPut(salt.joinToString("") { "%02x".format(it) }) {
            val spec = PBEKeySpec(secret, salt, 120_000, 256)
            SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        }
    }

    override fun encode(plain: ByteArray): ByteArray {
        val iv = ByteArray(12).also { random.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key(ownSalt), GCMParameterSpec(128, iv)) }
        return MAGIC + ownSalt + iv + c.doFinal(plain)
    }

    override fun decode(stored: ByteArray): ByteArray {
        if (stored.size < 4 + 16 + 12 + 16 || !stored.copyOfRange(0, 4).contentEquals(MAGIC)) throw SyncDecodeException("Not a Vela sync file")
        val salt = stored.copyOfRange(4, 20); val iv = stored.copyOfRange(20, 32)
        return try {
            Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(salt), GCMParameterSpec(128, iv)) }.doFinal(stored, 32, stored.size - 32)
        } catch (e: Exception) { throw SyncDecodeException("Wrong sync code or damaged file", e) }
    }

    private companion object { val MAGIC = "VSY1".encodeToByteArray() }
}
