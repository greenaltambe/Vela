package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlin.test.*

class SponsorBlockUserIdTest {
    @Test fun eachAccountSubmitsUnderItsOwnPrivateIdAndTheLocalOneKeepsTheOriginalSetting() {
        var account = "local"
        val settings = SettingsResolver(InMemorySettingsStore())
        val sb = createModules(FakeTransport { HttpResponse(404, "") }, createDatabase(JvmDatabaseDriverFactory()), settings, accountId = { account }).sponsorBlock
        val local = sb.userId(); assertEquals(local, settings.resolve(Settings.sponsorBlockUserId), "an existing install's id stays where it was")
        account = "google-a"; val a = sb.userId()
        account = "google-b"; val b = sb.userId()
        assertEquals(3, setOf(local, a, b).size, "no two accounts share an id")
        account = "google-a"; assertEquals(a, sb.userId(), "stable per account")
        account = "local"; assertEquals(local, sb.userId())
        assertTrue(a.length >= 30 && b.length >= 30)
    }
}
