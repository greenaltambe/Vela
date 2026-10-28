package com.greenaltambe.vela.auth

import com.greenaltambe.vela.core.Sha256
import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.data.LOCAL_ACCOUNT
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Clock
import kotlin.time.Instant

enum class AccountKind { LOCAL, GOOGLE }

/** [OK] works; [NEEDS_RELOGIN] the cookies were rejected (or deleted); [ERROR] something else is wrong with the account's calls. */
enum class AuthState { OK, NEEDS_RELOGIN, ERROR }

data class Account(
    val id: String,
    val displayName: String,
    val kind: AccountKind,
    val email: String?,
    val avatarUrl: String?,
    val channelId: String?,
    val authState: AuthState,
    val createdAt: Instant,
) {
    /** What may appear in a log or in Diagnostics: the kind and the first characters of the id, never name or e-mail. */
    val label: String get() = if (kind == AccountKind.LOCAL) "local" else "google:${id.takeLast(6)}"
    val maskedEmail: String? get() = Redaction.maskEmail(email)
    val isSignedIn: Boolean get() = kind == AccountKind.GOOGLE && authState == AuthState.OK
}

/** Who a Google sign-in turned out to be, as the account endpoint reported it. */
data class GoogleIdentity(val displayName: String, val email: String?, val avatarUrl: String?, val channelId: String?)

/**
 * The accounts on this device and which one is active. A LOCAL account always exists. Switching changes [active]; the
 * data of every account (subscriptions, history, playlists, search history, feed cache) stays in its own rows, keyed by
 * the account id, and the UI re-reads everything under the new id. Credentials are in the [CredentialStore], never here.
 */
class AccountManager(
    private val db: VelaDatabase,
    private val credentials: CredentialStore,
    private val clock: Clock = Clock.System,
) {
    private val settings = SqlDelightSettingsStore(db)
    private val q get() = db.accountQueries

    private val _all = MutableStateFlow(emptyList<Account>())
    val all: StateFlow<List<Account>> = _all

    private val _active = MutableStateFlow(load(LOCAL_ACCOUNT))
    val active: StateFlow<Account> = _active

    init {
        ensureLocal()
        reload()
        val remembered = settings.get(ACTIVE_KEY, SettingLayer.Global)
        _active.value = byId(remembered ?: LOCAL_ACCOUNT) ?: byId(LOCAL_ACCOUNT)!!
    }

    private fun ensureLocal() {
        if (q.selectById(LOCAL_ACCOUNT).executeAsOneOrNull() == null) q.upsert(LOCAL_ACCOUNT, "This device", clock.now().toEpochMilliseconds(), AccountKind.LOCAL.name, null, null, null, AuthState.OK.name)
    }

    private fun load(id: String): Account = byId(id) ?: Account(id, "This device", AccountKind.LOCAL, null, null, null, AuthState.OK, clock.now())

    private fun byId(id: String): Account? = q.selectById(id).executeAsOneOrNull()?.toAccount()

    private fun com.greenaltambe.vela.data.db.Account.toAccount() = Account(
        id, displayName, runCatching { AccountKind.valueOf(kind) }.getOrDefault(AccountKind.LOCAL), email, avatarUrl, channelId,
        runCatching { AuthState.valueOf(authState) }.getOrDefault(AuthState.ERROR), Instant.fromEpochMilliseconds(createdAt),
    )

    private fun reload() {
        _all.value = q.selectAll().executeAsList().map { it.toAccount() }
        _active.value.id.let { id -> byId(id)?.let { _active.value = it } }
    }

    fun get(id: String): Account? = byId(id)

    fun switchTo(id: String) {
        val target = byId(id) ?: return
        settings.put(ACTIVE_KEY, SettingLayer.Global, id)
        _active.value = target
    }

    /** The id a Google account gets: stable for the same channel / e-mail, so signing in again finds the same rows. */
    fun idFor(identity: GoogleIdentity): String = "google-" + Sha256.hex(identity.channelId ?: identity.email ?: identity.displayName).take(16)

    /**
     * Adds (or re-signs-in) a Google account: stores its credentials, creates the row (or refreshes it, keeping its data
     * and its original creation time) and makes it the active account.
     */
    fun addGoogle(identity: GoogleIdentity, cookies: CookieJar): Account {
        val id = idFor(identity)
        credentials.saveCookies(id, cookies)
        val created = byId(id)?.createdAt ?: clock.now()
        q.upsert(id, identity.displayName, created.toEpochMilliseconds(), AccountKind.GOOGLE.name, identity.email, identity.avatarUrl, identity.channelId, AuthState.OK.name)
        reload()
        switchTo(id)
        return byId(id)!!
    }

    fun setAuthState(id: String, state: AuthState) {
        q.setAuthState(state.name, id)
        reload()
    }

    /** Where credentials are kept, for the Accounts screen and Diagnostics. */
    val credentialStoreDescription: String get() = credentials.description

    fun cookies(id: String): CookieJar? = credentials.cookies(id)

    /** Replaces the stored cookies (after a rotation or a Set-Cookie update). */
    fun saveCookies(id: String, jar: CookieJar) = credentials.saveCookies(id, jar)

    /** Signs out: the credentials are deleted and the account needs a new login; its local data stays. */
    fun signOut(id: String) {
        if (byId(id)?.kind != AccountKind.GOOGLE) return
        credentials.delete(id)
        setAuthState(id, AuthState.NEEDS_RELOGIN)
    }

    /** Deletes every credential this app holds, for every account; accounts remain, all needing a new login. Switches to local. */
    fun signOutEverywhere() {
        credentials.deleteAll()
        for (a in q.selectAll().executeAsList()) if (a.kind == AccountKind.GOOGLE.name) q.setAuthState(AuthState.NEEDS_RELOGIN.name, a.id)
        reload()
        switchTo(LOCAL_ACCOUNT)
    }

    /** Removes a Google account and everything it owns here (credentials, subscriptions, history, playlists, search history, feed cache). */
    fun remove(id: String) {
        if (id == LOCAL_ACCOUNT || byId(id)?.kind != AccountKind.GOOGLE) return
        credentials.delete(id)
        db.transaction {
            q.deleteSubscriptions(id); q.deletePlaylistItems(id); q.deletePlaylists(id)
            db.watchHistoryQueries.clearForAccount(id)
            db.searchHistoryQueries.clearForAccount(id)
            db.feedItemQueries.clearForAccount(id)
            db.accountAuditQueries.clearForAccount(id)
            q.delete(id)
        }
        if (_active.value.id == id) switchTo(LOCAL_ACCOUNT)
        reload()
    }

    private companion object { const val ACTIVE_KEY = "accounts.active" }
}

sealed interface LoginResult {
    class Success(val account: Account) : LoginResult
    /** [message] is safe to show: it never contains cookie values. */
    class Failure(val message: String) : LoginResult
}

/**
 * Turns credentials into an account (validating them against the account endpoint) and checks stored ones later.
 * Implemented in `:innertube-auth`; the UI only sees this.
 */
interface AccountLogin {
    /** Cookies in Netscape format, as exported from a browser. */
    suspend fun importCookies(netscapeText: String): LoginResult

    /** Cookies captured by a web login (a Cookie header-style jar), same validation. */
    suspend fun addFromJar(jar: CookieJar): LoginResult

    /** Asks the account endpoint whether the stored session still works and records NEEDS_RELOGIN / OK. Returns the new state. */
    suspend fun checkSession(accountId: String): AuthState
}

/** What a pull from the account changed. [failure] is set when nothing was changed because the pull failed. */
data class SyncResult(val added: Int, val alreadyHad: Int, val failure: String? = null)

/** Pulls the signed-in account's data into its local rows. Implemented in `:innertube-auth`. */
interface AccountSync {
    /**
     * Subscriptions of the active Google account into its subscription table. Idempotent: running it twice adds nothing
     * the second time. Local rows are never deleted (union), because deleting needs the write path to be authoritative.
     * A failed pull leaves the existing rows untouched; a signed-out answer marks the account NEEDS_RELOGIN.
     */
    suspend fun pullSubscriptions(): SyncResult
}

/** The viewer's rating of a video. */
enum class VideoRating { LIKE, DISLIKE, NONE }

/** The outcome of a write to the account. [LocalOnly] means no Google account is signed in, so only the local copy changed. */
sealed interface ActionResult {
    data object Ok : ActionResult
    data object LocalOnly : ActionResult
    data class Failed(val message: String) : ActionResult
}

/**
 * Writes to the active Google account, each one a governor-owned call (write budget, spacing, breaker, audit). The UI updates the
 * local copy first and calls these; a [ActionResult.Failed] rolls the local change back and shows the message. Implemented in `:innertube-auth`.
 */
interface AccountActions {
    suspend fun subscribe(channelId: String): ActionResult
    suspend fun unsubscribe(channelId: String): ActionResult
    suspend fun rate(videoId: String, rating: VideoRating): ActionResult
    /** [playlistId] "WL" is Watch Later. */
    suspend fun addToPlaylist(playlistId: String, videoId: String): ActionResult
    suspend fun removeFromPlaylist(playlistId: String, videoId: String): ActionResult
    suspend fun createPlaylist(title: String): Pair<ActionResult, String?>
    suspend fun renamePlaylist(playlistId: String, title: String): ActionResult
    suspend fun deletePlaylist(playlistId: String): ActionResult
}

/** An account playlist as the library lists it. [id] "WL" is Watch Later, "LL" Liked videos. */
data class AccountPlaylist(val id: String, val title: String, val videoCount: Int?)

/** Reads from the signed-in account (governor-owned BROWSE calls). Each returns null when nobody is signed in or the call failed, so callers fall back to local data. */
interface AccountReads {
    /** The account's Home feed (FEwhat_to_watch). Empty list is a real answer (a new account has no recommendations). */
    suspend fun recommended(): List<com.greenaltambe.vela.extractor.VideoSummary>?
    suspend fun playlists(): List<AccountPlaylist>?
    suspend fun playlistVideos(playlistId: String): List<com.greenaltambe.vela.extractor.VideoSummary>?
    /** The account's watch history (newest first); entries carry no time, only the order YouTube lists them in. */
    suspend fun history(): List<com.greenaltambe.vela.extractor.VideoSummary>? = null
}
