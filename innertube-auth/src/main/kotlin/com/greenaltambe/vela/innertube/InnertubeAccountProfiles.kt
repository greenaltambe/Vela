package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

class InnertubeAccountProfiles(
    private val accounts: AccountManager,
    private val governors: GovernorRegistry,
    private val store: ProfileStore,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
) : AccountProfiles {
    override fun current() = store.chosen(accounts.active.value.id)

    private suspend fun <T> read(pageId: String?, block: InnertubeClient.() -> T): Pair<ActionResult, T?> {
        val account = accounts.active.value
        if (!account.isSignedIn) return ActionResult.LocalOnly to null
        val jar = accounts.cookies(account.id) ?: return ActionResult.Failed("Credentials are missing; sign in again.") to null
        return try {
            val session = AuthSession(jar, governors.forAccount(account.id), transport, clock, onCookiesChanged = { accounts.saveCookies(account.id, it) }, pageId = { pageId })
            ActionResult.Ok to withContext(Dispatchers.IO) { InnertubeClient(session, web, base).block() }
        } catch (e: AuthFailure.LoggedOut) { accounts.setAuthState(account.id, AuthState.NEEDS_RELOGIN); ActionResult.Failed("Signed out; sign in again.") to null
        } catch (e: GovernorDenied) { ActionResult.Failed(e.message ?: "Account traffic is paused.") to null
        } catch (e: kotlinx.coroutines.CancellationException) { throw e
        } catch (e: Exception) { ActionResult.Failed(Redaction.redact(e.message ?: e.javaClass.simpleName)) to null }
    }

    /** Listed WITHOUT any page id, so the answer is the account's own list. */
    override suspend fun list(): List<AccountProfile>? = read(null) { profiles() }.second?.map { AccountProfile(it.name, it.pageId) }

    override suspend fun select(profile: AccountProfile?): ActionResult {
        val id = accounts.active.value.id
        if (profile == null || profile.pageId == null) { store.clear(id); return ActionResult.Ok }
        // The check: a read made as that profile must come back as that profile. Nothing is stored (and nothing else can use it) before it passes.
        val (r, seen) = read(profile.pageId) { profiles() }
        if (r !is ActionResult.Ok) return r
        val selected = seen.orEmpty().firstOrNull { it.selected }
        if (selected == null || selected.name != profile.name) return ActionResult.Failed("YouTube answered as \"${selected?.name ?: "nobody"}\" instead of \"${profile.name}\"; the profile was not selected.")
        store.save(id, ChosenProfile(profile, verified = true))
        return ActionResult.Ok
    }
}
