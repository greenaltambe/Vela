@file:OptIn(androidx.compose.ui.test.ExperimentalTestApi::class)

package com.greenaltambe.vela.ui

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.runDesktopComposeUiTest
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlCursor
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlPreparedStatement
import com.greenaltambe.vela.data.FeedRepository
import com.greenaltambe.vela.extractor.VideoSummary
import com.greenaltambe.vela.feed.FeedEntry
import com.greenaltambe.vela.ui.nav.Route
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes

/** Counts the queries a screen makes. */
private class CountingDriver(private val inner: SqlDriver) : SqlDriver by inner {
    val queries = AtomicInteger()
    override fun <R> executeQuery(identifier: Int?, sql: String, mapper: (SqlCursor) -> QueryResult<R>, parameters: Int, binders: (SqlPreparedStatement.() -> Unit)?): QueryResult<R> {
        queries.incrementAndGet(); return inner.executeQuery(identifier, sql, mapper, parameters, binders)
    }
}

/**
 * Regression guard for the Subscriptions lag (task 23): with 200 cached videos the screen must make a CONSTANT number of database
 * queries, not one (or several) per row. Before the fix every filter step and badge asked the Setting table once per video.
 */
class ListQueryBudgetTest {
    private val now = Clock.System.now()

    @Test fun subscriptionsMakesNoQueriesPerRowWhenOpenedOrScrolledToTheEnd() = runDesktopComposeUiTest(800, 1000) {
        lateinit var counting: CountingDriver
        val f = buildFixture(withDownloads = false, wrapDriver = { CountingDriver(it).also { c -> counting = c } })
        val entries = (0 until 200).map { i -> FeedEntry(VideoSummary("vid$i", "Title $i", "UC${i % 20}", "Channel ${i % 20}", durationSeconds = 300L + i), now - i.minutes, now) }
        FeedRepository(f.services.database).replaceAll(entries, keep = 500)
        f.services.feed.reload()
        f.navigator.selectTab(Route.Subscriptions)
        setContent { AppUnderTest(f) }; waitForIdle()
        val opened = counting.queries.get()
        // The list shows 60 rows and appends the next 60 when its end is reached: scroll to the end four times.
        var index = 55
        repeat(4) { runCatching { onNodeWithTag("subs.feed").performScrollToIndex(index) }; waitForIdle(); index += 58 }
        val afterScroll = counting.queries.get()
        println("LIST QUERIES opened=$opened afterScrollToEnd=$afterScroll for 200 rows")
        assertTrue(opened < 120, "opening Subscriptions made $opened queries for 200 rows: that is per-row work")
        assertTrue(afterScroll - opened < 60, "scrolling through 200 rows made ${afterScroll - opened} more queries: that is per-row work")
    }
}
