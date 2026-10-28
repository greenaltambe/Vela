package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/** Validates cookies against `account/account_menu`, then stores them as a Google account. */
class InnertubeAccountLogin(
    private val accounts: AccountManager,
    private val governors: GovernorRegistry,
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val web: WebClient = WebClient.bundled(),
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
) : AccountLogin {

    override suspend fun importCookies(netscapeText: String): LoginResult {
        val jar = try { CookieJar.parseNetscape(netscapeText) } catch (e: Exception) { return LoginResult.Failure("That does not look like a cookies.txt file.") }
        return addFromJar(jar)
    }

    override suspend fun addFromJar(jar: CookieJar): LoginResult {
        if (jar.isEmpty()) return LoginResult.Failure("The file has no cookies. Export them from a browser window where you are signed in to YouTube.")
        if (!jar.looksSignedIn) return LoginResult.Failure("No Google sign-in cookies (SID, SAPISID) were found. Export while signed in, from youtube.com.")
        val info = try {
            withContext(Dispatchers.IO) { InnertubeClient(AuthSession(jar, governors.forAccount(ONBOARDING), transport, clock), web, base).accountInfo() }
        } catch (e: AuthFailure.LoggedOut) {
            return LoginResult.Failure("Google says these cookies are signed out or expired. Export again from a fresh private window and close it right after.")
        } catch (e: AuthFailure.RateLimited) {
            return LoginResult.Failure("Google is rate limiting this connection. Try again in a few minutes.")
        } catch (e: java.io.IOException) {
            return LoginResult.Failure("Could not reach YouTube: ${Redaction.redact(e.message ?: "network error")}")
        } catch (e: GovernorDenied) {
            return LoginResult.Failure(e.message ?: "Account traffic is paused.")
        } catch (e: AuthFailure) {
            return LoginResult.Failure(Redaction.redact(e.message ?: "sign-in check failed"))
        }
        val keep = CookieJar(jar.cookies.filter { it.domain.trimStart('.').let { d -> d.endsWith("youtube.com") || d.endsWith("google.com") || d.endsWith("googleapis.com") } })
        return LoginResult.Success(accounts.addGoogle(GoogleIdentity(info.displayName, info.email, info.avatarUrl, info.channelId), keep))
    }

    override suspend fun checkSession(accountId: String): AuthState {
        val jar = accounts.cookies(accountId)
        if (jar == null) { accounts.setAuthState(accountId, AuthState.NEEDS_RELOGIN); return AuthState.NEEDS_RELOGIN }
        val state = try {
            val session = AuthSession(jar, governors.forAccount(accountId), transport, clock, onCookiesChanged = { accounts.saveCookies(accountId, it) })
            withContext(Dispatchers.IO) { InnertubeClient(session, web, base).accountInfo() }
            AuthState.OK
        } catch (e: AuthFailure.LoggedOut) { AuthState.NEEDS_RELOGIN }
        catch (e: Exception) { return accounts.get(accountId)?.authState ?: AuthState.ERROR } // a network blip must not sign anyone out
        accounts.setAuthState(accountId, state)
        return state
    }

    private companion object { const val ONBOARDING = "onboarding" }
}
