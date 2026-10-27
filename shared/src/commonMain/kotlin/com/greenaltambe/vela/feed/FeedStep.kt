package com.greenaltambe.vela.feed

import com.greenaltambe.vela.core.settings.SettingsResolver
import com.greenaltambe.vela.extractor.VideoSummary

class FeedContext(val settings: SettingsResolver)

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
