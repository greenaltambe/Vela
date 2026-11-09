package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.settings.AccountHistorySync
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.AccountWatchedEntry
import com.greenaltambe.vela.data.AccountWatchedRepository
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/** What started a sync: the user (History screen), the app starting, or the periodic worker / timer. */
enum class HistorySyncTrigger { MANUAL, START, PERIODIC }

sealed interface HistorySyncStatus {
    data object Idle : HistorySyncStatus
    data object Running : HistorySyncStatus
    /** The read failed; whatever was stored before is untouched. Shown in the History screen only. */
    data object Failed : HistorySyncStatus
    /** [added] videos were new; [read] entries were looked at. */
    data class Done(val added: Int, val read: Int) : HistorySyncStatus
}

/**
 * Reads the signed-in account's watch history (FEhistory, a READ: `history.localOnly` is about not sending and does not stop it) into the
 * `AccountWatched` table. Incremental: pages are followed until a page contains a video that is already stored, or [maxEntries] entries were read.
 * Everything goes through [AccountReads], i.e. the account's governor as BROWSE with patience. A failed read changes nothing that is stored.
 * With the setting OFF, or nobody signed in, no request is made.
 */
class AccountHistoryService(
    private val reads: AccountReads?,
    private val db: VelaDatabase,
    private val accounts: AccountManager,
    private val settings: SettingsResolver,
    private val clock: Clock = Clock.System,
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES,
    private val maxPages: Int = 20,
) {
    val status = MutableStateFlow<HistorySyncStatus>(HistorySyncStatus.Idle)
    /** Raised whenever stored rows may have changed, so a screen showing them re-reads. */
    val revision = MutableStateFlow(0)
    private val lock = Mutex()
    private var startHandled = false
    private val store get() = SqlDelightSettingsStore(db)

    fun mode(): AccountHistorySync = settings.resolve(Settings.historyAccountSync)

    /** Whether a run triggered by [trigger] should happen now for [accountId], by the setting and the time of the last successful run. */
    fun isDue(trigger: HistorySyncTrigger, accountId: String): Boolean = when (mode()) {
        AccountHistorySync.OFF -> false
        AccountHistorySync.MANUAL -> trigger == HistorySyncTrigger.MANUAL
        AccountHistorySync.ON_START -> trigger == HistorySyncTrigger.START || trigger == HistorySyncTrigger.MANUAL
        AccountHistorySync.EVERY_6H -> trigger == HistorySyncTrigger.MANUAL || (lastRun(accountId)?.let { clock.now() - it >= SIX_HOURS } ?: true)
    }

    /** Runs a sync when [isDue]; returns the new status, or null when nothing was due. */
    suspend fun syncIfDue(trigger: HistorySyncTrigger): HistorySyncStatus? {
        val id = accounts.active.value.id
        // "When the app starts" means once per process: a rotated or recreated screen does not read again.
        if (trigger == HistorySyncTrigger.START && startHandled) return null
        if (trigger == HistorySyncTrigger.START) startHandled = true
        return if (isDue(trigger, id)) sync() else null
    }

    /** Reads now (the History screen's button, or a scheduled run that was due). */
    suspend fun sync(): HistorySyncStatus {
        val account = accounts.active.value
        val source = reads
        if (mode() == AccountHistorySync.OFF || source == null || !account.isSignedIn) return status.value.takeIf { it is HistorySyncStatus.Failed } ?: HistorySyncStatus.Idle
        if (!lock.tryLock()) return status.value
        try {
            status.value = HistorySyncStatus.Running
            val repo = AccountWatchedRepository(db, account.id)
            val now = clock.now()
            var token: String? = null
            var read = 0; var added = 0; var index = 0
            repeat(maxPages) {
                val page = try { source.historyPage(token) } catch (e: kotlinx.coroutines.CancellationException) { throw e } catch (_: Exception) { null }
                if (page == null) { status.value = HistorySyncStatus.Failed; return HistorySyncStatus.Failed }
                var sawKnown = false
                db.transaction {
                    for (v in page.videos) {
                        if (read >= maxEntries) break
                        if (repo.contains(v.id)) sawKnown = true else added++
                        // YouTube lists no time per entry: the order is kept by stepping back a millisecond per entry from the time of the read.
                        repo.upsert(AccountWatchedEntry(v.id, v.title, v.channelId.ifBlank { null }, v.watchedPercent, Instant.fromEpochMilliseconds(now.toEpochMilliseconds() - index)))
                        index++; read++
                    }
                }
                token = page.continuation
                if (sawKnown || read >= maxEntries || token == null) { return finish(account.id, now, added, read) }
            }
            return finish(account.id, now, added, read)
        } finally { lock.unlock() }
    }

    private fun finish(accountId: String, now: Instant, added: Int, read: Int): HistorySyncStatus {
        store.put(lastKey(accountId), SettingLayer.Global, now.toEpochMilliseconds().toString())
        revision.value++
        return HistorySyncStatus.Done(added, read).also { status.value = it }
    }

    private fun lastKey(accountId: String) = "history.accountSync.lastAt.$accountId"
    private fun lastRun(accountId: String): Instant? = store.get(lastKey(accountId), SettingLayer.Global)?.toLongOrNull()?.let { Instant.fromEpochMilliseconds(it) }

    companion object {
        /** Entries read per run at most. */
        const val DEFAULT_MAX_ENTRIES = 200
        private val SIX_HOURS = 6.hours
    }
}
