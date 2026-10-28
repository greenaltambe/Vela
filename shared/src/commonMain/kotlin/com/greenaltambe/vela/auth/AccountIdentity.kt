package com.greenaltambe.vela.auth

import kotlin.random.Random
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.days
import kotlin.time.Instant

/**
 * What makes one account's requests look like one stable client: the same device id, visitor data, client version and user
 * agent every time, instead of a new identity per request. Kept in the [SecretStore] next to the account's cookies (so it is
 * encrypted, excluded from backup, and removed with the account), and swapped when the account changes.
 */
data class AccountIdentity(
    val deviceId: String,
    val visitorData: String?,
    val clientVersion: String?,
    val userAgent: String?,
    val createdAt: Instant,
    val refreshedAt: Instant,
) {
    fun isStale(now: Instant, maxAge: Duration = 30.days): Boolean = now - refreshedAt > maxAge

    internal fun encode(): String = listOf("deviceId" to deviceId, "visitorData" to visitorData, "clientVersion" to clientVersion, "userAgent" to userAgent,
        "createdAt" to createdAt.toEpochMilliseconds().toString(), "refreshedAt" to refreshedAt.toEpochMilliseconds().toString())
        .filter { it.second != null }.joinToString("\n") { "${it.first}=${it.second!!.replace("\n", " ")}" }

    companion object {
        internal fun decode(text: String): AccountIdentity? {
            val m = text.lineSequence().mapNotNull { l -> l.indexOf('=').takeIf { it > 0 }?.let { l.substring(0, it) to l.substring(it + 1) } }.toMap()
            return AccountIdentity(m["deviceId"] ?: return null, m["visitorData"], m["clientVersion"], m["userAgent"],
                Instant.fromEpochMilliseconds(m["createdAt"]?.toLongOrNull() ?: return null), Instant.fromEpochMilliseconds(m["refreshedAt"]?.toLongOrNull() ?: return null))
        }

        fun newDeviceId(random: Random = Random.Default): String = buildString { repeat(16) { append("0123456789abcdef"[random.nextInt(16)]) } }
    }
}

/**
 * Hands out the identity of an account: created on first use, reused after, replaced only when it is stale (30 days) or the
 * caller says it failed ([refresh]). The device id survives a refresh, so an account does not look like a new device each time.
 */
class AccountIdentities(private val secrets: SecretStore, private val clock: Clock = Clock.System, private val random: Random = Random.Default) {
    private fun key(accountId: String) = "identity.$accountId"

    fun current(accountId: String): AccountIdentity? = secrets.get(key(accountId))?.let(AccountIdentity::decode)

    /** The stored identity, or a new one filled by [fill] (visitorData, clientVersion, userAgent) when there is none or it is stale. */
    fun getOrCreate(accountId: String, fill: () -> Triple<String?, String?, String?>): AccountIdentity {
        val existing = current(accountId)
        if (existing != null && !existing.isStale(clock.now())) return existing
        return refresh(accountId, fill)
    }

    /** Re-fetches visitorData/version/UA (after a failure or staleness) but keeps the device id. */
    fun refresh(accountId: String, fill: () -> Triple<String?, String?, String?>): AccountIdentity {
        val old = current(accountId)
        val (visitor, version, ua) = fill()
        val now = clock.now()
        return AccountIdentity(old?.deviceId ?: AccountIdentity.newDeviceId(random), visitor, version, ua, old?.createdAt ?: now, now).also { secrets.put(key(accountId), it.encode()) }
    }

    fun delete(accountId: String) = secrets.delete(key(accountId))
}
