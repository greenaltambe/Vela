package com.greenaltambe.vela.sync

import com.greenaltambe.vela.auth.*
import kotlin.time.Instant

/**
 * Lets the viewer's devices share Google sign-ins. Each signed-in account is published as one record (who it is, plus its cookies), encrypted like
 * everything else. A device adopts a record only when it has no such account or the account needs a new sign-in; a device that is signed in keeps its
 * own session (two devices fighting over rotating cookies would sign both out). The record lives in the secret store as a snapshot, so what is
 * published stays stable until another device publishes a newer one.
 *
 * Removing an account on a device makes it "declined" there, so sync does not bring it back. Sign-ins are never removed from another device.
 */
class AccountsDomain(private val accounts: AccountManager, private val secrets: CredentialStore) : SyncDomain {
    override val prefix = "acct/"

    private fun snapKey(id: String) = "sync.login.$id"
    private fun declinedKey(id: String) = "sync.declined.$id"

    override fun read(): Map<String, String> {
        val local = accounts.all.value.filter { it.kind == AccountKind.GOOGLE }
        // An account that was adopted and later removed here stays removed.
        for (key in secrets.secretKeys("sync.login.")) {
            val id = key.removePrefix("sync.login.")
            if (local.none { it.id == id }) { secrets.putSecret(declinedKey(id), "1"); secrets.deleteSecret(key) }
        }
        return buildMap {
            for (a in local) {
                if (a.authState != AuthState.OK) continue
                val snap = secrets.secret(snapKey(a.id)) ?: run {
                    val jar = accounts.cookies(a.id) ?: return@run null
                    join(a.displayName, a.email, a.avatarUrl, a.channelId, a.createdAt.toEpochMilliseconds(), jar.toNetscape()).also { secrets.putSecret(snapKey(a.id), it) }
                }
                if (snap != null) put("$prefix${esc(a.id)}", snap)
            }
        }
    }

    override fun apply(changes: Map<String, String?>) {
        for ((k, v) in changes) {
            val id = unesc(k.removePrefix(prefix))
            if (v == null) { secrets.deleteSecret(snapKey(id)); continue }
            if (secrets.secret(declinedKey(id)) != null) continue
            val f = v.split('\t').map(::unesc)
            if (f.size < 6) continue
            secrets.putSecret(snapKey(id), v)
            val existing = accounts.get(id)
            if (existing == null || existing.authState != AuthState.OK) {
                val jar = CookieJar.parseNetscape(f[5]).takeIf { !it.isEmpty() } ?: continue
                accounts.restoreFromSync(id, GoogleIdentity(f[0], f[1].ifBlank { null }, f[2].ifBlank { null }, f[3].ifBlank { null }), Instant.fromEpochMilliseconds(f[4].toLongOrNull() ?: 0), jar)
            }
        }
    }
}
