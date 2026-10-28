package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlin.time.Instant

/** Breaker state in the Setting table (state only: no credentials), so a restart keeps a cooldown. */
class DbBreakerStore(db: VelaDatabase) : BreakerStore {
    private val store = SqlDelightSettingsStore(db)
    private fun k(id: String, f: String) = "governor.$id.$f"

    override fun load(accountId: String): BreakerStatus {
        val state = store.get(k(accountId, "state"), SettingLayer.Global)?.let { runCatching { BreakerState.valueOf(it) }.getOrNull() } ?: return BreakerStatus.Closed
        return BreakerStatus(
            state,
            store.get(k(accountId, "until"), SettingLayer.Global)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) },
            store.get(k(accountId, "level"), SettingLayer.Global)?.toIntOrNull() ?: 0,
            store.get(k(accountId, "signal"), SettingLayer.Global)?.let { runCatching { BlockSignal.valueOf(it) }.getOrNull() },
        )
    }

    override fun save(accountId: String, status: BreakerStatus) {
        store.put(k(accountId, "state"), SettingLayer.Global, status.state.name)
        store.put(k(accountId, "until"), SettingLayer.Global, status.openUntil?.toEpochMilliseconds()?.toString() ?: "")
        store.put(k(accountId, "level"), SettingLayer.Global, status.level.toString())
        store.put(k(accountId, "signal"), SettingLayer.Global, status.lastSignal?.name ?: "")
    }
}

/** The audit log in the database, capped, with the newest first for Diagnostics. */
class DbAuditLog(private val db: VelaDatabase, private val cap: Long = 1000, private val sink: com.greenaltambe.vela.extractor.chain.LogLineSink? = null) : AuditSink {
    private val q get() = db.accountAuditQueries
    override fun record(entry: AuditEntry) {
        q.insert(entry.accountId, entry.at.toEpochMilliseconds(), entry.kind, entry.host, entry.result, entry.decision)
        q.trim(cap)
        sink?.let { runCatching { it.write(Redaction.redact("${entry.at} governor account=${entry.accountId} ${entry.kind} host=${entry.host} result=${entry.result} decision=${entry.decision}")) } }
    }
    fun recent(accountId: String, limit: Long = 200): List<AuditEntry> =
        q.recent(accountId, limit).executeAsList().map { AuditEntry(it.accountId, Instant.fromEpochMilliseconds(it.at), it.kind, it.host, it.result, it.decision) }
    fun purge(accountId: String? = null) { if (accountId == null) q.clearAll() else q.clearForAccount(accountId) }
}

/** The governor's limits as the user set them. */
fun governorLimits(settings: SettingsResolver): GovernorLimits = GovernorLimits(
    playbackPerHour = settings.resolve(Settings.accountPlaybackPerHour).toInt(),
    browsePerMinute = settings.resolve(Settings.accountBrowsePerMinute).toInt(),
    writesPerHour = settings.resolve(Settings.accountWritesPerHour).toInt(),
    killSwitch = settings.resolve(Settings.accountKillSwitch),
)
