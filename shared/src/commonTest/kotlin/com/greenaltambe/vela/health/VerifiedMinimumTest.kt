package com.greenaltambe.vela.health

import com.greenaltambe.vela.core.settings.YtDlpAutoUpdate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VerifiedMinimumTest {
    private fun updater(wants: Boolean) = object : ManagedUpdater {
        override val status = MutableStateFlow("")
        override suspend fun install(tag: String) = true
        override val installsVerifiedMinimum = wants
    }

    @Test fun anOldCopyOnAnUpdaterThatAsksGetsTheVerifiedTag() = assertEquals("2026.10.26", verifiedMinimumToInstall("2025.11.12", updater(true), YtDlpAutoUpdate.ASK, "2026.10.26"))
    @Test fun autoModeAlsoInstalls() = assertEquals("2026.10.26", verifiedMinimumToInstall("2025.11.12", updater(true), YtDlpAutoUpdate.AUTO, "2026.10.26"))
    @Test fun offNeverInstalls() = assertNull(verifiedMinimumToInstall("2025.11.12", updater(true), YtDlpAutoUpdate.OFF, "2026.10.26"))
    @Test fun aCurrentOrNewerCopyIsLeftAlone() { assertNull(verifiedMinimumToInstall("2026.10.26", updater(true), YtDlpAutoUpdate.ASK, "2026.10.26")); assertNull(verifiedMinimumToInstall("2027.01.02", updater(true), YtDlpAutoUpdate.ASK, "2026.10.26")) }
    @Test fun desktopUpdatersAndUnknownVersionsAreNotTouched() { assertNull(verifiedMinimumToInstall("2025.11.12", updater(false), YtDlpAutoUpdate.ASK, "2026.10.26")); assertNull(verifiedMinimumToInstall(null, updater(true), YtDlpAutoUpdate.ASK, "2026.10.26")) }
}
