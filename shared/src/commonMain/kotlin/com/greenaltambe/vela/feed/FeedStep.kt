package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.VideoSummary

class FeedContext(val settings: SettingsResolver) {
    private val memo = HashMap<Triple<String, String?, Boolean>, Any?>()

    /**
     * [SettingsResolver.resolve] remembered for this run of the pipeline: a feed has a few hundred videos but only as many
     * channels, and a setting does not change while one run is in progress. Looking it up once per channel instead of once per
     * video took the Subscriptions filters from ~0.3 s to a few ms of main-thread time on 500 cached videos.
     */
    @Suppress("UNCHECKED_CAST")
    fun <T> resolve(setting: com.greenaltambe.vela.core.settings.Setting<T>, channelId: String? = null): T =
        memo.getOrPut(Triple(setting.key, channelId, false)) { settings.resolve(setting, channelId = channelId) } as T
}

/** One filter/transform stage; a new feed rule means a new step. */
fun interface FeedStep {
    fun apply(items: List<VideoSummary>, context: FeedContext): List<VideoSummary>
}

class FeedPipeline(private val steps: List<FeedStep>) {
    /** This pipeline followed by [step]. */
    fun with(step: FeedStep) = FeedPipeline(steps + step)

    fun run(items: List<VideoSummary>, context: FeedContext): List<VideoSummary> =
        steps.fold(items) { acc, step -> step.apply(acc, context) }
}
