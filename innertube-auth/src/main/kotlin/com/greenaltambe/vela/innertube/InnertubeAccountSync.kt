package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import com.greenaltambe.vela.data.SubscriptionRepository
import com.greenaltambe.vela.data.db.VelaDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class InnertubeAccountSync(
    private val accounts: AccountManager,
    private val db: VelaDatabase,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
) : AccountSync {
    override suspend fun pullSubscriptions(): SyncResult {
        val account = accounts.active.value
        if (!account.isSignedIn) return SyncResult(0, 0, "No signed-in account.")
        val jar = accounts.cookies(account.id) ?: return SyncResult(0, 0, "Credentials are missing; sign in again.")
        val remote = try {
            val session = AuthSession(jar, transport, clock, onCookiesChanged = { accounts.saveCookies(account.id, it) })
            withContext(Dispatchers.IO) { InnertubeClient(session, web, base).subscriptions() }
        } catch (e: AuthFailure.LoggedOut) {
            accounts.setAuthState(account.id, AuthState.NEEDS_RELOGIN); return SyncResult(0, 0, "Signed out; sign in again.")
        } catch (e: Exception) {
            return SyncResult(0, 0, Redaction.redact(e.message ?: e.javaClass.simpleName))
        }
        if (remote.isEmpty()) return SyncResult(0, 0, "The account returned no subscriptions; local data was kept.")
        val repo = SubscriptionRepository(db, account.id)
        val now = clock.now()
        var added = 0
        db.transaction {
            for (c in remote) if (!repo.isSubscribed(c.channelId)) { repo.subscribe(c.channelId, c.title, c.avatarUrl, now); added++ }
        }
        return SyncResult(added, remote.size - added)
    }
}
