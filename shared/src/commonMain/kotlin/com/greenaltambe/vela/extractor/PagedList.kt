package com.greenaltambe.vela.extractor

/**
 * A listing assembled from successive [Page]s, with items de-duplicated by id. Search has no server-side cursor, so
 * the yt-dlp tier re-ranks per page and a few results repeat across pages (about 4 of 20 observed); any caller that
 * accumulates pages should do it through here. Immutable: [append] returns the next state.
 */
class PagedList<T>(
    val items: List<T> = emptyList(),
    /** Token for the next page, or null when there is none. */
    val continuation: String? = null,
    private val idOf: (T) -> String,
) {
    private val seen = items.mapTo(HashSet(), idOf)

    /** How many items of the last appended page were dropped as repeats. */
    var lastDuplicates: Int = 0
        private set

    /** First occurrence wins, so an item never changes position. [continuation] always advances to the page's. */
    fun append(page: Page<T>): PagedList<T> {
        val fresh = page.items.filter { seen.add(idOf(it)) }
        return PagedList(items + fresh, page.continuation, idOf).also { it.lastDuplicates = page.items.size - fresh.size }
    }

    /** A new listing from the first page. */
    fun replace(page: Page<T>) = PagedList(emptyList(), null, idOf).append(page)
}

fun videoPagedList() = PagedList<VideoSummary>(idOf = VideoSummary::id)
