package com.greenaltambe.vela.auth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Credentials encrypted with an AES-256-GCM key that lives in the Android Keystore (it cannot be read out of the
 * device, and is not part of any backup), the ciphertext in a private SharedPreferences file that the backup rules
 * exclude too. Chosen over androidx.security:security-crypto, which is deprecated; this is the same primitive without it.
 */
class AndroidKeystoreSecretStore(context: Context) : SecretStore {
    override val description = "Android Keystore (AES-256-GCM)"
    private val prefs = context.applicationContext.getSharedPreferences("vela_secrets", Context.MODE_PRIVATE)

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).setKeySize(256).build())
        }.generateKey()
    }

    override fun get(key: String): String? {
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val raw = Base64.decode(stored, Base64.NO_WRAP)
            val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, raw.copyOfRange(0, 12))) }
            String(c.doFinal(raw, 12, raw.size - 12))
        } catch (e: Exception) { null } // key lost (restored device, cleared lock screen): the credential is gone, the account must log in again
    }

    override fun put(key: String, value: String) {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        prefs.edit().putString(key, Base64.encodeToString(c.iv + c.doFinal(value.toByteArray()), Base64.NO_WRAP)).apply()
    }

    override fun delete(key: String) { prefs.edit().remove(key).apply() }
    override fun keys(): Set<String> = prefs.all.keys.toSet()

    private companion object { const val ALIAS = "vela_secrets_v1" }
}
