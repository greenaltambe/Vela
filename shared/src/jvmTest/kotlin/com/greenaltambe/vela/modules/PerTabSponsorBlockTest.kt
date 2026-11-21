package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.http.FakeTransport
import com.greenaltambe.vela.core.http.HttpResponse
import com.greenaltambe.vela.core.settings.InMemorySettingsStore
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.JvmDatabaseDriverFactory
import com.greenaltambe.vela.data.createDatabase
import kotlin.test.Test
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class PerTabSponsorBlockTest {
    @Test fun eachDesktopTabGetsItsOwnAddonAndTheDefaultStaysTheAppsOne() {
        val m = createModules(FakeTransport { HttpResponse(404, "") }, createDatabase(JvmDatabaseDriverFactory()), SettingsResolver(InMemorySettingsStore()))
        val a = m.newSponsorBlock(); val b = m.newSponsorBlock()
        assertNotSame(a, b, "separate state (prompt, notices, current video) per tab")
        assertNotSame(a, m.sponsorBlock)
        // A Modules built by hand (tests, Android) hands out its one addon.
        val plain = Modules(m.log, m.sponsorBlock, m.segments, m.ryd, m.dearrow, m.client)
        assertSame(plain.sponsorBlock, plain.newSponsorBlock())
    }
}
