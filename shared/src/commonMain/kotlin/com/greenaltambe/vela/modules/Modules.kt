package com.greenaltambe.vela.modules

import com.greenaltambe.vela.core.http.ApiClient
import com.greenaltambe.vela.core.http.HttpTransport
import com.greenaltambe.vela.core.http.InMemoryApiCache
import com.greenaltambe.vela.core.http.LayeredApiCache
import com.greenaltambe.vela.core.http.ModuleLog
import com.greenaltambe.vela.core.settings.Settings
import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.data.db.VelaDatabase
import com.greenaltambe.vela.modules.dearrow.DeArrow
import com.greenaltambe.vela.modules.ryd.ReturnYouTubeDislike
import com.greenaltambe.vela.modules.sponsorblock.SegmentRepository
import com.greenaltambe.vela.modules.sponsorblock.SponsorBlockAddon
import com.greenaltambe.vela.modules.sponsorblock.SponsorBlockApi
import kotlin.time.Clock

/** The three feature modules and what they share: one HTTP client, one cache, one log. Built once per process by the platform entry point. */
class Modules(
    val log: ModuleLog,
    val sponsorBlock: SponsorBlockAddon,
    val segments: SegmentRepository,
    val ryd: ReturnYouTubeDislike,
    val dearrow: DeArrow,
    /** The shared HTTP client (cached GETs), for other readers of small public files such as subtitle tracks. */
    val client: ApiClient,
) {
    val all: List<FeatureModule> get() = listOf(sponsorBlock, ryd, dearrow)
}

fun createModules(transport: HttpTransport, database: VelaDatabase, settings: SettingsResolver, clock: Clock = Clock.System, accountId: () -> String = { com.greenaltambe.vela.data.LOCAL_ACCOUNT }): Modules {
    val log = ModuleLog()
    val client = ApiClient(transport, LayeredApiCache(InMemoryApiCache(), database), clock)
    val api = SponsorBlockApi(client, server = { settings.resolve(Settings.sponsorBlockServer) })
    val segments = SegmentRepository(api, database, clock)
    return Modules(
        log = log,
        sponsorBlock = SponsorBlockAddon(segments, api, settings, log, clock, accountId),
        segments = segments,
        ryd = ReturnYouTubeDislike(client, settings, log, clock),
        dearrow = DeArrow(client, settings, log, clock),
        client = client,
    )
}
