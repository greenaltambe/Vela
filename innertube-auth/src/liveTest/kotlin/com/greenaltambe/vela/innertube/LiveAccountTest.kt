package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.auth.AccountGovernor
import com.greenaltambe.vela.auth.Redaction
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class LiveAccountTest {
    private val jar = LiveCookies.jarOrSkip()
    private var rotated = 0
    private val session = AuthSession(jar, AccountGovernor("live"), onCookiesChanged = { rotated++ })
    private val client = InnertubeClient(session)

    @Test fun accountInfoComesBackForTheCookies() {
        val info = client.accountInfo()
        assertTrue(info.displayName.isNotBlank())
        println("account: name=${info.displayName.length} chars, email=${Redaction.maskEmail(info.email)}, avatar=${info.avatarUrl != null}, channelId=${info.channelId != null}, cookiesRotatedDuringCall=$rotated")
        assertNotNull(info.avatarUrl)
    }

    @Test fun subscriptionsAreListedWithoutDuplicates() {
        val subs = client.subscriptions()
        println("subscriptions: ${subs.size}")
        assertTrue(subs.isNotEmpty())
        assertEquals(subs.size, subs.map { it.channelId }.toSet().size)
        assertTrue(subs.all { it.channelId.startsWith("UC") && it.title.isNotBlank() })
    }

    @Test fun brokenCookiesAreReportedAsLoggedOut() {
        val bad = AuthSession(com.greenaltambe.vela.auth.CookieJar(jar.cookies.map { if (it.name.contains("SID") || it.name.contains("PAPISID")) it.copy(value = "invalid") else it }), AccountGovernor("live-bad"))
        val thrown = runCatching { InnertubeClient(bad).accountInfo() }.exceptionOrNull()
        println("broken cookies -> ${thrown?.javaClass?.simpleName}: ${thrown?.message}")
        assertTrue(thrown is AuthFailure.LoggedOut, "got $thrown")
    }
}
