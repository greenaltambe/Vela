package com.greenaltambe.vela.sync

import kotlin.test.*

class WebDavAddressTest {
    @Test fun koofrNeedsNoServer() = assertEquals("https://app.koofr.net/dav/Koofr/Vela%20sync", WebDavAddress.build(WebDavService.KOOFR, "", "me@x.org", ""))

    @Test fun nextcloudIsBuiltFromJustTheServerAndUser() {
        assertEquals("https://cloud.example.com/remote.php/dav/files/me/Vela%20sync", WebDavAddress.build(WebDavService.NEXTCLOUD, "cloud.example.com/", "me", "Vela sync"))
        assertNull(WebDavAddress.build(WebDavService.NEXTCLOUD, "cloud.example.com", "", ""))
        assertEquals("https://c.org/remote.php/dav/files/me/Apps/Vela", WebDavAddress.build(WebDavService.NEXTCLOUD, "https://c.org/remote.php/dav/files/me", "me", "Apps/Vela"))
    }

    @Test fun otherUsesTheGivenAddressAndFixesCommonSlips() {
        assertEquals("https://nas.local:5006/home/Vela%20sync", WebDavAddress.build(WebDavService.OTHER, " nas.local:5006/home/ ", "u", "  "))
        assertNull(WebDavAddress.build(WebDavService.OTHER, "  ", "u", "x"))
        assertEquals("http://nas/dav/M%C3%BCsik", WebDavAddress.build(WebDavService.OTHER, "http://nas/dav", "u", "Müsik"))
    }
}
