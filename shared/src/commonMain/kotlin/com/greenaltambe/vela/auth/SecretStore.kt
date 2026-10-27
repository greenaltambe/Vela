package com.greenaltambe.vela.auth

/**
 * Where credentials live. Never the main database, never a log, never a backup: Android keeps them AES-GCM encrypted under
 * a Keystore key (which does not leave the device and is excluded from backup), Linux in libsecret when `secret-tool`
 * exists, else in an AES-GCM encrypted file whose key is a 0600 file next to it (Diagnostics warns about that fallback).
 */
interface SecretStore {
    /** For Diagnostics: where secrets are kept and how strongly ("libsecret", "encrypted file (key on disk)"...). */
    val description: String

    fun get(key: String): String?
    fun put(key: String, value: String)
    fun delete(key: String)

    /** Every key currently stored (names only), for "sign out everywhere". */
    fun keys(): Set<String>
}

class InMemorySecretStore : SecretStore {
    private val map = LinkedHashMap<String, String>()
    override val description = "memory (tests)"
    @Synchronized override fun get(key: String) = map[key]
    @Synchronized override fun put(key: String, value: String) { map[key] = value }
    @Synchronized override fun delete(key: String) { map.remove(key) }
    @Synchronized override fun keys(): Set<String> = map.keys.toSet()
}

/** Stores one account's cookies as a Netscape file under `cookies.<accountId>`. */
class CredentialStore(private val secrets: SecretStore) {
    val description: String get() = secrets.description
    fun cookies(accountId: String): CookieJar? = secrets.get(key(accountId))?.let { CookieJar.parseNetscape(it) }?.takeIf { !it.isEmpty() }
    fun saveCookies(accountId: String, jar: CookieJar) = secrets.put(key(accountId), jar.toNetscape())
    fun delete(accountId: String) = secrets.delete(key(accountId))

    /** Deletes every credential this app holds ("sign out everywhere"). */
    fun deleteAll() = secrets.keys().forEach { secrets.delete(it) }

    private fun key(accountId: String) = "cookies.$accountId"
}
