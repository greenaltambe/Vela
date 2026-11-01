package com.greenaltambe.vela.innertube

import com.greenaltambe.vela.extractor.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.*
import java.util.TimeZone
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

const val NATIVE_TOKEN_PREFIX = "n1:"
internal fun wrapNativeToken(token: String?): String? = token?.let { if (it.startsWith(NATIVE_TOKEN_PREFIX)) it else "$NATIVE_TOKEN_PREFIX$it" }
internal fun unwrapNativeToken(token: String): String? {
    if (token.startsWith(NATIVE_TOKEN_PREFIX)) return token.removePrefix(NATIVE_TOKEN_PREFIX)
    return null
}

/**
 * Native YouTube channel browse (server-side sort, all tabs, Posts, Courses, About).
 * Purely anonymous: no cookies or PO tokens are ever sent.
 * Parsers are tolerant (walk by key, never fixed paths), count and log unknown renderers.
 */
class ChannelBrowse(
    private val transport: AuthTransport = OkHttpAuthTransport(),
    private val clientVersion: () -> String = { WebClient.bundled().clientVersion },
    private val visitorData: () -> String? = { null },
    private val clock: Clock = Clock.System,
    private val base: String = "https://www.youtube.com/youtubei/v1",
    private val onUnknownRenderer: (String) -> Unit = {},
    private val contentRegion: () -> String = { "US" },
) {
    // Cache per (channelId, tabKind, sortOrFilter) with 15-minute TTL.
    private val tabCache = LinkedHashMap<String, Pair<Instant, ChannelTabPage>>()
    private val landingCache = LinkedHashMap<String, Pair<Instant, ChannelLanding>>()
    private val aboutCache = LinkedHashMap<String, Pair<Instant, ChannelAbout>>()
    private val unknownRendererTimes = HashMap<String, Instant>()

    private val ttl = 15.minutes

    internal fun reportUnknownRenderer(name: String) {
        val now = clock.now()
        synchronized(unknownRendererTimes) {
            val last = unknownRendererTimes[name]
            if (last == null || now - last > 1.hours) {
                unknownRendererTimes[name] = now
                onUnknownRenderer(name)
            }
        }
    }

    fun clearCache(channelId: String? = null) {
        if (channelId == null) {
            synchronized(landingCache) { landingCache.clear() }
            synchronized(tabCache) { tabCache.clear() }
            synchronized(aboutCache) { aboutCache.clear() }
        } else {
            synchronized(landingCache) { landingCache.remove(channelId) }
            synchronized(tabCache) {
                val toRemove = tabCache.keys.filter { it.startsWith("$channelId:") }
                toRemove.forEach { tabCache.remove(it) }
            }
            synchronized(aboutCache) {
                val toRemove = aboutCache.keys.filter { it.startsWith("$channelId:") }
                toRemove.forEach { aboutCache.remove(it) }
            }
        }
    }

    suspend fun landing(channelId: String, refresh: Boolean = false): ExtractorResult<ChannelLanding> {
        val now = clock.now()
        if (refresh) synchronized(landingCache) { landingCache.remove(channelId) }
        else synchronized(landingCache) {
            landingCache[channelId]?.let { (at, data) ->
                if (now - at < ttl) return ExtractorResult.Success(data)
            }
        }
        val res = executeBrowse(buildJsonObject { put("browseId", channelId) })
        val json = when (res) {
            is ExtractorResult.Success -> res.value
            is ExtractorResult.Failure -> return res
        }
        val landing = parseLanding(channelId, json)
        synchronized(landingCache) {
            landingCache[channelId] = now to landing
            while (landingCache.size > 32) landingCache.remove(landingCache.keys.first())
        }
        return ExtractorResult.Success(landing)
    }

    suspend fun tab(channelId: String, tabDescriptor: ChannelTab, filterOption: ChannelFilterOption? = null, continuation: String? = null, refresh: Boolean = false): ExtractorResult<ChannelTabPage> {
        val sortKey = filterOption?.params ?: filterOption?.token ?: filterOption?.label ?: "default"
        val cacheKey = "$channelId:${tabDescriptor.kind.name}:$sortKey"
        val now = clock.now()

        if (continuation == null) {
            if (refresh) synchronized(tabCache) { tabCache.remove(cacheKey) }
            else synchronized(tabCache) {
                tabCache[cacheKey]?.let { (at, page) ->
                    if (now - at < ttl) return ExtractorResult.Success(page)
                }
            }
        }

        val unwrappedContinuation = if (continuation != null) {
            unwrapNativeToken(continuation) ?: return ExtractorResult.Failure(ExtractorError.TokenNotMine("Token is not from native tier"))
        } else null

        val body = buildJsonObject {
            if (unwrappedContinuation != null) {
                put("continuation", unwrappedContinuation)
            } else {
                val filterToken = filterOption?.token
                if (filterToken != null) {
                    val u = unwrapNativeToken(filterToken) ?: filterToken
                    put("continuation", u)
                } else {
                    put("browseId", channelId)
                    val p = filterOption?.params ?: tabDescriptor.params ?: defaultParamsFor(tabDescriptor.kind)
                    if (p != null) put("params", p)
                }
            }
        }

        val res = executeBrowse(body)
        val json = when (res) {
            is ExtractorResult.Success -> res.value
            is ExtractorResult.Failure -> return res
        }

        val page = parseTabPage(json)
        // Task 48 Section 4: Empty parse on first page without recognized empty-state is ParseFailure
        if (continuation == null && page.items.isEmpty() && page.shelves.isEmpty() && page.filters.isEmpty()) {
            if (!hasRecognisedEmptyState(json)) {
                val keys = extractTopLevelRendererKeys(json)
                return ExtractorResult.Failure(ExtractorError.ParseFailure("Empty tab parse without recognised empty-state message. Renderers: ${keys.joinToString(", ")}"))
            }
        }

        if (continuation == null) {
            synchronized(tabCache) {
                tabCache[cacheKey] = now to page
                while (tabCache.size > 64) tabCache.remove(tabCache.keys.first())
            }
        }
        return ExtractorResult.Success(page)
    }

    suspend fun continuation(token: String): ExtractorResult<ChannelTabPage> {
        val unwrapped = unwrapNativeToken(token) ?: return ExtractorResult.Failure(ExtractorError.TokenNotMine("Token is not from native tier"))
        val res = executeBrowse(buildJsonObject { put("continuation", unwrapped) })
        val json = when (res) {
            is ExtractorResult.Success -> res.value
            is ExtractorResult.Failure -> return res
        }
        return ExtractorResult.Success(parseTabPage(json))
    }

    suspend fun about(channelId: String, token: String? = null, refresh: Boolean = false): ExtractorResult<ChannelAbout> {
        val cacheKey = "$channelId:${token ?: "header"}"
        val now = clock.now()
        if (refresh) synchronized(aboutCache) { aboutCache.remove(cacheKey) }
        else synchronized(aboutCache) {
            aboutCache[cacheKey]?.let { (at, a) ->
                if (now - at < ttl) return ExtractorResult.Success(a)
            }
        }

        val body = buildJsonObject {
            if (token != null) put("continuation", token)
            else put("browseId", channelId)
        }
        val res = executeBrowse(body)
        val json = when (res) {
            is ExtractorResult.Success -> res.value
            is ExtractorResult.Failure -> return res
        }

        val about = parseAbout(json, channelId)
        synchronized(aboutCache) {
            aboutCache[cacheKey] = now to about
            while (aboutCache.size > 32) aboutCache.remove(aboutCache.keys.first())
        }
        return ExtractorResult.Success(about)
    }

    suspend fun search(channelId: String, query: String, continuation: String? = null): ExtractorResult<ChannelTabPage> {
        val unwrappedContinuation = if (continuation != null) {
            unwrapNativeToken(continuation) ?: return ExtractorResult.Failure(ExtractorError.TokenNotMine("Token is not from native tier"))
        } else null
        val body = buildJsonObject {
            if (unwrappedContinuation != null) {
                put("continuation", unwrappedContinuation)
            } else {
                put("browseId", channelId)
                put("params", SEARCH_PARAMS)
                put("query", query)
            }
        }
        val res = executeBrowse(body)
        val json = when (res) {
            is ExtractorResult.Success -> res.value
            is ExtractorResult.Failure -> return res
        }
        return ExtractorResult.Success(parseTabPage(json))
    }

    suspend fun postDetail(browseParams: String): ExtractorResult<Pair<ChannelPost, List<Comment>>> {
        val res = executeBrowse(buildJsonObject {
            put("browseId", "FEpost_detail")
            put("params", browseParams)
        })
        val json = when (res) {
            is ExtractorResult.Success -> res.value
            is ExtractorResult.Failure -> return res
        }
        val (post, commentsToken) = parsePostDetail(json) ?: return ExtractorResult.Failure(ExtractorError.ParseFailure("No post in post detail"))
        val comments = if (commentsToken != null) {
            val unwrapped = unwrapNativeToken(commentsToken) ?: commentsToken
            val cRes = executeBrowse(buildJsonObject { put("continuation", unwrapped) })
            if (cRes is ExtractorResult.Success) parsePostComments(cRes.value) else emptyList()
        } else emptyList()

        return ExtractorResult.Success(post to comments)
    }

    internal suspend fun executeBrowse(body: JsonObject): ExtractorResult<JsonElement> {
        val nowMs = clock.now().toEpochMilliseconds()
        val offsetMinutes = (TimeZone.getDefault().getOffset(nowMs) / 60000)
        val reg = contentRegion().ifBlank { "US" }
        val context = buildJsonObject {
            putJsonObject("client") {
                put("clientName", "WEB")
                put("clientVersion", clientVersion())
                put("hl", "en")
                put("gl", reg)
                put("utcOffsetMinutes", offsetMinutes)
                visitorData()?.let { put("visitorData", it) }
            }
        }
        val payload = buildJsonObject {
            put("context", context)
            body.forEach { (k, v) -> put(k, v) }
        }

        val raw = try {
            withContext(Dispatchers.IO) {
                transport.execute("POST", "$base/browse?prettyPrint=false", mapOf(
                    "Content-Type" to "application/json",
                    "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64; rv:140.0) Gecko/20100101 Firefox/140.0",
                    "Origin" to "https://www.youtube.com",
                    "Referer" to "https://www.youtube.com/",
                ), payload.toString())
            }
        } catch (e: Exception) {
            return ExtractorResult.Failure(ExtractorError.Network(e.message ?: "Network error", e))
        }

        if (raw.code == 429) return ExtractorResult.Failure(ExtractorError.RateLimited("HTTP 429"))
        if (raw.code == 403) return ExtractorResult.Failure(ExtractorError.Blocked("HTTP 403"))
        if (raw.code == 400 || raw.code == 404) {
            val isContinuation = body.containsKey("continuation")
            return if (isContinuation) ExtractorResult.Failure(ExtractorError.TokenExpired("HTTP ${raw.code}"))
            else ExtractorResult.Failure(ExtractorError.Unplayable("HTTP ${raw.code}"))
        }
        if (raw.code in 500..599) return ExtractorResult.Failure(ExtractorError.TierUnavailable("HTTP ${raw.code}"))
        if (raw.code !in 200..299) return ExtractorResult.Failure(ExtractorError.Unplayable("HTTP ${raw.code}"))

        val json = try {
            lenientJson.parseToJsonElement(raw.body)
        } catch (e: Exception) {
            return ExtractorResult.Failure(ExtractorError.ParseFailure("Not JSON response", e))
        }

        // Check alerts (e.g. "This channel does not exist.")
        val alerts = json["alerts"].array
        for (a in alerts) {
            val ar = a["alertRenderer"]
            if (ar["type"].string == "ERROR") {
                val text = ar["text"].text() ?: "Channel unavailable"
                return ExtractorResult.Failure(ExtractorError.Unplayable(text))
            }
        }

        return ExtractorResult.Success(json)
    }

    // ----------------- Parsers -----------------

    internal fun parseLanding(channelId: String, json: JsonElement): ChannelLanding {
        val header = parseHeader(channelId, json)
        val tabs = parseTabs(json)
        val aboutToken = findAboutToken(json)
        val initialPage = parseTabPage(json)
        return ChannelLanding(header, tabs, initialPage.takeIf { it.items.isNotEmpty() || it.shelves.isNotEmpty() }, aboutToken)
    }

    internal fun parseHeader(channelId: String, json: JsonElement): ChannelPage {
        val h = json["header"]
        val phv = h.findAll("pageHeaderViewModel").firstOrNull()
        val c4 = h.findAll("c4TabbedHeaderRenderer").firstOrNull()

        val title = phv.at("title", "dynamicTextViewModel", "text", "content").string
            ?: phv.at("title", "dynamicTextViewModel", "text").text()
            ?: c4.at("title").string ?: c4.at("title").text() ?: ""

        val avatar = phv.at("image", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image", "sources").array.lastOrNull().at("url").string
            ?: c4.at("avatar", "thumbnails").array.lastOrNull().at("url").string
            ?.let { if (it.startsWith("//")) "https:$it" else it }

        val banner = phv.at("banner", "imageBannerViewModel", "image", "sources").array.lastOrNull().at("url").string
            ?: c4.at("banner", "thumbnails").array.lastOrNull().at("url").string
            ?.let { if (it.startsWith("//")) "https:$it" else it }

        val metaRows = phv.findAll("metadataParts")
        var subText: String? = null
        for (mp in metaRows) {
            for (part in mp.array) {
                val text = part.at("text", "content").string ?: part.text()
                if (text != null && text.contains("subscriber", true)) subText = text
            }
        }
        if (subText == null) subText = c4.at("subscriberCountText").text()
        val subs = parseCount(subText)

        val desc = phv.at("description", "descriptionPreviewViewModel", "description", "content").string
            ?: c4.at("description").text() ?: ""

        return ChannelPage(
            id = channelId,
            name = title,
            description = desc,
            avatarUrl = avatar,
            subscriberCount = subs,
            bannerUrl = banner,
        )
    }

    internal fun parseTabs(json: JsonElement): List<ChannelTab> {
        val out = ArrayList<ChannelTab>()
        val tabsArray = json.findAll("twoColumnBrowseResultsRenderer").firstOrNull().at("tabs").array
        for (t in tabsArray) {
            val tr = t["tabRenderer"] ?: t["expandableTabRenderer"] ?: continue
            val endpoint = tr["endpoint"].at("browseEndpoint")
            val cmdMeta = tr.at("endpoint", "commandMetadata", "webCommandMetadata")
            val url = cmdMeta["url"].string ?: endpoint["canonicalBaseUrl"].string ?: ""
            val cleanUrl = url.substringBefore('?').substringBefore('#').trimEnd('/')
            val lastSeg = cleanUrl.substringAfterLast('/')
            val rawTitle = tr["title"].string ?: tr["title"].text() ?: ""
            val params = endpoint["params"].string

            var kind = ChannelTabKind.fromUrlSegment(lastSeg)
            if (kind == ChannelTabKind.UNKNOWN) {
                kind = when {
                    params == PLAYLISTS_PARAMS || params?.startsWith("EglwbGF5bGlzd") == true -> ChannelTabKind.PLAYLISTS
                    params == COURSES_PARAMS || params?.startsWith("Egdjb3Vyc2") == true -> ChannelTabKind.COURSES
                    params == VIDEOS_PARAMS || params?.startsWith("EgZ2aWRlb") == true -> ChannelTabKind.VIDEOS
                    params == SHORTS_PARAMS || params?.startsWith("EgZzaG9yd") == true -> ChannelTabKind.SHORTS
                    params == STREAMS_PARAMS || params?.startsWith("EgdzdHJlYW") == true -> ChannelTabKind.STREAMS
                    params == POSTS_PARAMS || params?.startsWith("EgVwb3N0") == true -> ChannelTabKind.POSTS
                    params == SEARCH_PARAMS || params?.startsWith("EgZzZWFyY") == true -> ChannelTabKind.SEARCH
                    else -> when (rawTitle.trim().lowercase()) {
                        "playlists", "playlist" -> ChannelTabKind.PLAYLISTS
                        "courses", "course" -> ChannelTabKind.COURSES
                        "videos", "video" -> ChannelTabKind.VIDEOS
                        "shorts", "short" -> ChannelTabKind.SHORTS
                        "live", "streams", "stream" -> ChannelTabKind.STREAMS
                        "community", "posts", "post" -> ChannelTabKind.POSTS
                        "shows", "show" -> ChannelTabKind.SHOWS
                        "podcasts", "podcast" -> ChannelTabKind.PODCASTS
                        "releases", "release" -> ChannelTabKind.RELEASES
                        "store" -> ChannelTabKind.STORE
                        "search" -> ChannelTabKind.SEARCH
                        "about" -> ChannelTabKind.ABOUT
                        "home", "featured" -> ChannelTabKind.FEATURED
                        else -> ChannelTabKind.UNKNOWN
                    }
                }
            }
            val title = rawTitle.ifBlank { kind.name.lowercase().replaceFirstChar(Char::uppercase) }
            val selected = (tr["selected"] as? JsonPrimitive)?.booleanOrNull == true
            out += ChannelTab(kind, title, params, selected, url)
        }
        return out
    }

    internal fun findAboutToken(json: JsonElement): String? {
        val header = json["header"]
        for (cmd in header.findAll("continuationCommand")) {
            val req = cmd["request"].string
            if (req == "CONTINUATION_REQUEST_TYPE_BROWSE") {
                val tok = cmd["token"].string
                if (tok != null) return tok
            }
        }
        return null
    }

    internal fun parseTabPage(json: JsonElement): ChannelTabPage {
        val filters = parseFilters(json)
        val (items, shelves, nextToken) = parseContent(json)
        val wrappedNextToken = wrapNativeToken(nextToken)
        return ChannelTabPage(items, shelves, filters, wrappedNextToken)
    }

    internal fun hasRecognisedEmptyState(json: JsonElement): Boolean {
        val text = json.toString().lowercase()
        if (text.contains("this channel has no") ||
            text.contains("no videos") ||
            text.contains("no content") ||
            text.contains("no uploads") ||
            text.contains("no live streams") ||
            text.contains("no playlists") ||
            text.contains("no posts") ||
            text.contains("haven't posted") ||
            text.contains("nothing here") ||
            text.contains("no items")
        ) return true
        if (json.findAll("messageRenderer").isNotEmpty() || json.findAll("backgroundPromoRenderer").isNotEmpty()) {
            return true
        }
        return false
    }

    internal fun extractTopLevelRendererKeys(json: JsonElement): List<String> {
        val keys = LinkedHashSet<String>()
        fun checkKeys(elem: JsonElement) {
            if (elem is JsonObject) {
                for ((k, v) in elem) {
                    if (k.endsWith("Renderer") || k.endsWith("ViewModel")) keys.add(k)
                    if (keys.size < 10) checkKeys(v)
                }
            } else if (elem is JsonArray) {
                for (item in elem) {
                    if (keys.size < 10) checkKeys(item)
                }
            }
        }
        checkKeys(json)
        return keys.take(10).toList().ifEmpty { listOf("unknown") }
    }

    internal fun parseFilters(json: JsonElement): List<ChannelFilterGroup> {
        val out = ArrayList<ChannelFilterGroup>()

        // 1. chipBarViewModel
        for (cb in json.findAll("chipBarViewModel")) {
            val chips = cb["chips"].array.mapNotNull { c ->
                val cvm = c["chipViewModel"] ?: return@mapNotNull null
                val text = cvm["text"].string ?: cvm["text"].text() ?: return@mapNotNull null
                val selected = (cvm["selected"] as? JsonPrimitive)?.booleanOrNull
                    ?: (cvm["isSelected"] as? JsonPrimitive)?.booleanOrNull == true
                val token = cvm.findAll("continuationCommand").firstNotNullOfOrNull { it["token"].string }
                ChannelFilterOption(text, selected, token = token)
            }
            if (chips.isNotEmpty()) out += ChannelFilterGroup(isDropdown = false, options = chips)
        }

        // 2. sortFilterSubMenuRenderer (e.g. Playlists tab)
        for (sm in json.findAll("sortFilterSubMenuRenderer")) {
            val title = sm["title"].string ?: sm["title"].text() ?: "Sort by"
            val options = sm["subMenuItems"].array.mapNotNull { item ->
                val label = item["title"].string ?: item["title"].text() ?: return@mapNotNull null
                val selected = (item["selected"] as? JsonPrimitive)?.booleanOrNull == true
                val params = item.findAll("browseEndpoint").firstNotNullOfOrNull { it["params"].string }
                ChannelFilterOption(label, selected, params = params)
            }
            if (options.isNotEmpty()) out += ChannelFilterGroup(isDropdown = true, title = title, options = options)
        }

        // 3. showSheetCommand sort menus
        for (sheet in json.findAll("sheetViewModel")) {
            val list = sheet.findAll("listItemViewModel")
            val options = list.mapNotNull { li ->
                val label = li["title"].string ?: li["title"].text() ?: return@mapNotNull null
                val selected = (li["isSelected"] as? JsonPrimitive)?.booleanOrNull == true
                val token = li.findAll("continuationCommand").firstNotNullOfOrNull { it["token"].string }
                ChannelFilterOption(label, selected, token = token)
            }
            if (options.isNotEmpty()) out += ChannelFilterGroup(isDropdown = true, options = options)
        }

        return out
    }

    private data class ContentResult(val items: List<ChannelItem>, val shelves: List<ChannelShelf>, val nextToken: String?)

    private fun parseContent(json: JsonElement): ContentResult {
        val items = ArrayList<ChannelItem>()
        val shelves = ArrayList<ChannelShelf>()
        var nextToken: String? = null

        fun inspectItem(it: JsonElement) {
            val rir = it["richItemRenderer"]?.at("content")
                ?: it["richSectionRenderer"]?.at("content")
                ?: it

            // Video / Lockup
            val lockup = rir["lockupViewModel"]
            if (lockup != null) {
                val cType = lockup["contentType"].string ?: ""
                when {
                    cType.contains("VIDEO") -> parseLockupVideo(lockup)?.let { items += ChannelItem.Video(it) }
                    cType.contains("SHORT") || cType.contains("REEL") -> parseLockupVideo(lockup)?.copy(isShort = true)?.let { items += ChannelItem.Video(it) }
                    cType.contains("PLAYLIST") -> parseLockupPlaylist(lockup)?.let { items += ChannelItem.Playlist(it) }
                    else -> reportUnknownRenderer("lockupViewModel:$cType")
                }
                return
            }

            // Classic videoRenderer / gridVideoRenderer
            val vr = rir["videoRenderer"] ?: rir["gridVideoRenderer"]
            if (vr != null) {
                parseVideo(vr)?.let { items += ChannelItem.Video(it) }
                return
            }

            // Shorts lockup / reelItemRenderer
            val reel = rir["reelItemRenderer"] ?: rir["shortsLockupViewModel"]
            if (reel != null) {
                parseShort(reel)?.let { items += ChannelItem.Video(it) }
                return
            }

            // Playlist / course
            val pr = rir["playlistRenderer"] ?: rir["gridPlaylistRenderer"]
            if (pr != null) {
                parsePlaylist(pr)?.let { items += ChannelItem.Playlist(it) }
                return
            }

            // Post
            val post = rir["backstagePostThreadRenderer"]?.at("post", "backstagePostRenderer")
                ?: rir["backstagePostRenderer"] ?: rir["postRenderer"]
            if (post != null) {
                parsePost(post)?.let { items += ChannelItem.Post(it) }
                return
            }

            // Shelf inside list (standard shelf or shorts reel shelf)
            val shelf = rir["shelfRenderer"] ?: rir["reelShelfRenderer"]
            if (shelf != null) {
                val title = shelf["title"].text() ?: "Shorts"
                val shelfItems = ArrayList<ChannelItem>()
                val rawItems = shelf["items"].array.ifEmpty {
                    shelf.at("content", "horizontalListRenderer", "items").array.ifEmpty {
                        shelf.at("content", "gridRenderer", "items").array
                    }
                }
                for (item in rawItems) {
                    val sLockup = item["shortsLockupViewModel"] ?: item["reelItemRenderer"]
                    if (sLockup != null) {
                        parseShort(sLockup)?.let { shelfItems += ChannelItem.Video(it) }
                        continue
                    }
                    val lockup = item["lockupViewModel"]
                    if (lockup != null) {
                        val cType = lockup["contentType"].string ?: ""
                        when {
                            cType.contains("VIDEO") -> parseLockupVideo(lockup)?.let { shelfItems += ChannelItem.Video(it) }
                            cType.contains("SHORT") || cType.contains("REEL") -> parseLockupVideo(lockup)?.copy(isShort = true)?.let { shelfItems += ChannelItem.Video(it) }
                            cType.contains("PLAYLIST") -> parseLockupPlaylist(lockup)?.let { shelfItems += ChannelItem.Playlist(it) }
                            else -> {}
                        }
                        continue
                    }
                    val vr = item["videoRenderer"] ?: item["gridVideoRenderer"]
                    if (vr != null) {
                        parseVideo(vr)?.let { shelfItems += ChannelItem.Video(it) }
                        continue
                    }
                    val pr = item["playlistRenderer"] ?: item["gridPlaylistRenderer"]
                    if (pr != null) {
                        parsePlaylist(pr)?.let { shelfItems += ChannelItem.Playlist(it) }
                        continue
                    }
                    val post = item["postRenderer"] ?: item["backstagePostRenderer"]
                    if (post != null) {
                        parsePost(post)?.let { shelfItems += ChannelItem.Post(it) }
                        continue
                    }
                }
                if (shelfItems.isNotEmpty()) shelves += ChannelShelf(title, shelfItems)
                return
            }

            // Continuation INSIDE list
            val cir = it["continuationItemRenderer"] ?: rir["continuationItemRenderer"]
            if (cir != null) {
                val tok = cir.at("continuationEndpoint", "continuationCommand", "token").string
                if (tok != null) nextToken = tok
                return
            }

            // Unknown renderer
            val target = if (rir is JsonObject && rir.isNotEmpty() && rir != it) rir else it
            val typeName = (target as? JsonObject)?.keys?.firstOrNull() ?: "unknown"
            if (!typeName.contains("header", true)) reportUnknownRenderer(typeName)
        }

        // Paging can be in reloadContinuationItemsCommand or appendContinuationItemsAction
        for (action in json.findAll("reloadContinuationItemsCommand") + json.findAll("appendContinuationItemsAction")) {
            val list = action["continuationItems"].array
            for (it in list) inspectItem(it)
        }

        // Or in richGridRenderer / richGridContinuation / gridRenderer
        val grid = json.findAll("richGridRenderer").firstOrNull()
            ?: json.findAll("richGridContinuation").firstOrNull()
            ?: json.findAll("gridRenderer").firstOrNull()
        if (grid != null) {
            val contents = (grid["contents"] ?: grid["items"]).array
            for (it in contents) inspectItem(it)
        }

        // Or sectionListRenderer
        for (sec in json.findAll("sectionListRenderer")) {
            for (c in sec["contents"].array) {
                val isr = c["itemSectionRenderer"]
                if (isr != null) {
                    for (item in isr["contents"].array) inspectItem(item)
                }
            }
        }

        return ContentResult(items, shelves, nextToken)
    }

    private fun parseVideo(vr: JsonElement): VideoSummary? {
        val id = vr["videoId"].string ?: return null
        val title = vr["title"].text() ?: ""
        val owner = vr["ownerText"] ?: vr["shortBylineText"] ?: vr["longBylineText"]
        val channelId = owner.findAll("browseEndpoint").firstNotNullOfOrNull { it["browseId"].string } ?: ""
        val channelName = owner.text() ?: ""
        val thumb = vr.at("thumbnail", "thumbnails").array.lastOrNull().at("url").string
        val duration = parseDuration(vr["lengthText"].text())
        val views = parseCount(vr["viewCountText"].text() ?: vr["shortViewCountText"].text())
        val published = vr["publishedTimeText"].text()
        val isLive = vr.toString().contains("LIVE")
        val avatar = vr.findAll("channelThumbnailWithLinkRenderer").firstOrNull().at("thumbnail", "thumbnails").array.firstOrNull().at("url").string
        return VideoSummary(id, title, channelId, channelName, thumb, duration, views, publishedText = published, isLive = isLive, channelAvatarUrl = avatar)
    }

    private fun parseLockupVideo(l: JsonElement): VideoSummary? {
        val id = l["contentId"].string ?: return null
        val meta = l.at("metadata", "lockupMetadataViewModel") ?: return null
        val title = meta.at("title", "content").string ?: meta["title"].text() ?: ""
        val thumb = l.at("contentImage", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
        val avatar = meta.at("image", "decoratedAvatarViewModel", "avatar", "avatarViewModel", "image", "sources").array.firstOrNull().at("url").string

        val durationBadge = l.findAll("thumbnailBadgeViewModel").firstOrNull().at("text").string
        val duration = parseDuration(durationBadge)

        val rows = meta.at("metadata", "contentMetadataViewModel", "metadataRows").array
        var channelName = ""
        var views: Long? = null
        var published: String? = null
        for (r in rows) {
            val parts = r.at("metadataParts").array
            for (p in parts) {
                val text = p.at("text", "content").string ?: p.at("text").text() ?: continue
                val lower = text.lowercase()
                if (lower.contains("view")) {
                    views = parseCount(text)
                } else if (lower.contains("ago") || lower.contains("stream") || lower.contains("premier") || lower.contains("hour") || lower.contains("day") || lower.contains("month") || lower.contains("year") || lower.contains("minute") || lower.contains("second") || lower.contains("yesterday")) {
                    published = text
                } else if (channelName.isBlank()) {
                    channelName = text
                }
            }
        }
        return VideoSummary(id, title, "", channelName, thumb, duration, views, publishedText = published, channelAvatarUrl = avatar)
    }

    internal fun parseShort(reel: JsonElement): VideoSummary? {
        val id = reel["videoId"].string
            ?: reel["contentId"].string
            ?: reel.at("onTap", "innertubeCommand", "reelWatchEndpoint", "videoId").string
            ?: reel.at("onTap", "innertubeCommand", "commandMetadata", "webCommandMetadata", "url").string
                ?.substringAfter("/shorts/")?.substringBefore("?")?.substringBefore("&")
            ?: reel["entityId"].string?.let {
                if (it.startsWith("shorts-shelf-item-")) it.removePrefix("shorts-shelf-item-")
                else if (it.startsWith("shorts-lockup-item-")) it.removePrefix("shorts-lockup-item-")
                else it
            }
            ?: reel.findAll("videoId").firstOrNull()?.let { (it as? JsonPrimitive)?.contentOrNull }
            ?: return null

        val title = reel.at("overlayMetadata", "primaryText", "content").string
            ?: reel.at("headline", "simpleText").string
            ?: reel["title"].text()
            ?: reel.at("metadata", "lockupMetadataViewModel", "title", "content").string
            ?: reel["accessibilityText"].string?.substringBefore(",")
            ?: ""

        val thumb = reel.at("thumbnailViewModel", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
            ?: reel.at("contentImage", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
            ?: reel.findAll("thumbnails").firstOrNull().array.lastOrNull().at("url").string
            ?: reel.findAll("sources").firstOrNull().array.lastOrNull().at("url").string

        val viewText = reel.at("overlayMetadata", "secondaryText", "content").string
            ?: reel["viewCountText"].text()
            ?: reel["accessibilityText"].string?.let { acc ->
                acc.split(",").getOrNull(1)?.substringBefore("-")?.trim()
            }
        val views = parseCount(viewText)

        return VideoSummary(
            id = id,
            title = title,
            channelId = "",
            channelName = "",
            thumbnailUrl = thumb,
            durationSeconds = null,
            viewCount = views,
            publishedText = viewText,
            isShort = true
        )
    }

    private fun parsePlaylist(pr: JsonElement): PlaylistRef? {
        val id = pr["playlistId"].string ?: return null
        val title = pr["title"].text() ?: pr.at("title", "simpleText").string ?: ""
        val thumb = pr.at("thumbnails", 0, "thumbnails").array.lastOrNull().at("url").string
            ?: pr.at("thumbnail", "thumbnails").array.lastOrNull().at("url").string
        val count = pr["videoCount"].string?.filter { it.isDigit() }?.toLongOrNull() ?: pr["videoCountText"].text()?.filter { it.isDigit() }?.toLongOrNull()

        // Course check: thumbnailOverlayBottomPanelRenderer.icon.iconType == "COURSE"
        val overlays = pr.findAll("thumbnailOverlayBottomPanelRenderer")
        var isCourse = false
        var lessonCount: String? = null
        for (o in overlays) {
            if (o.at("icon", "iconType").string == "COURSE") {
                isCourse = true
                lessonCount = o.at("text", "simpleText").string ?: o.at("text").text()
            }
        }
        return PlaylistRef(id, title, thumb, count, isCourse = isCourse, lessonCountText = lessonCount)
    }

    private fun parseLockupPlaylist(l: JsonElement): PlaylistRef? {
        val id = l["contentId"].string ?: return null
        val meta = l.at("metadata", "lockupMetadataViewModel") ?: return null
        val title = meta.at("title", "content").string ?: meta["title"].text() ?: ""
        val thumb = l.at("contentImage", "thumbnailViewModel", "image", "sources").array.lastOrNull().at("url").string
        val overlays = l.findAll("thumbnailOverlayBottomPanelRenderer")
        var isCourse = false
        var lessonCount: String? = null
        for (o in overlays) {
            if (o.at("icon", "iconType").string == "COURSE") {
                isCourse = true
                lessonCount = o.at("text", "simpleText").string ?: o.at("text").text()
            }
        }
        return PlaylistRef(id, title, thumb, null, isCourse = isCourse, lessonCountText = lessonCount)
    }

    internal fun parsePost(post: JsonElement): ChannelPost? {
        val id = post["postId"].string ?: return null
        val author = post["authorText"].text() ?: ""
        val avatar = post.at("authorThumbnail", "thumbnails").array.lastOrNull().at("url").string
            ?.let { if (it.startsWith("//")) "https:$it" else it }
        val text = post["contentText"].text() ?: ""
        val published = post["publishedTimeText"].text()
        val voteText = post.at("voteCount", "simpleText").string ?: post["voteCount"].text()
        val likes = parseCount(voteText)

        // Detail browse params
        val detailParams = post.findAll("browseEndpoint").firstOrNull { it["browseId"].string == "FEpost_detail" }?.get("params").string

        // Attachment
        var attachment: PostAttachment? = null
        val att = post["backstageAttachment"]
        if (att != null) {
            val vr = att["videoRenderer"]
            val pr = att["playlistRenderer"]
            val img = att["backstageImageRenderer"]
            val multi = att["postMultiImageRenderer"]
            val poll = att["pollRenderer"]
            when {
                vr != null -> parseVideo(vr)?.let { attachment = PostAttachment.Video(it) }
                pr != null -> parsePlaylist(pr)?.let { attachment = PostAttachment.Playlist(it) }
                img != null -> {
                    val url = img.at("image", "thumbnails").array.lastOrNull().at("url").string
                    if (url != null) attachment = PostAttachment.Image(url)
                }
                multi != null -> {
                    val imgs = multi.findAll("backstageImageRenderer").mapNotNull {
                        it.at("image", "thumbnails").array.lastOrNull().at("url").string?.let { u -> PostAttachment.Image(u) }
                    }
                    if (imgs.isNotEmpty()) attachment = PostAttachment.MultiImage(imgs)
                }
                poll != null -> {
                    val choices = poll["choices"].array.mapNotNull { c ->
                        val t = c["text"].text() ?: return@mapNotNull null
                        val ratio = (c["voteRatioIfVoted"] as? JsonPrimitive)?.doubleOrNull
                        val pct = c["votePercentageIfVoted"].text()
                        PollChoice(t, ratio, pct)
                    }
                    val totalVotes = poll["totalVotes"].text() ?: poll.at("totalVotes", "simpleText").string
                    attachment = PostAttachment.Poll(choices, totalVotes)
                }
            }
        }

        return ChannelPost(id, author, avatar, text, published, voteText, likes, detailParams, attachment)
    }

    internal fun parsePostDetail(json: JsonElement): Pair<ChannelPost, String?>? {
        val postElem = json.findAll("backstagePostRenderer").firstOrNull() ?: return null
        val post = parsePost(postElem) ?: return null
        // Find comments continuation token
        val cir = json.findAll("continuationItemRenderer").firstOrNull()
        val token = cir.at("continuationEndpoint", "continuationCommand", "token").string
        return post to token
    }

    internal fun parsePostComments(json: JsonElement): List<Comment> =
        json.findAll("commentRenderer").mapNotNull { c ->
            val id = c["commentId"].string ?: return@mapNotNull null
            val author = c["authorText"].text() ?: ""
            val text = c["contentText"].text() ?: ""
            val published = c["publishedTimeText"].text() ?: ""
            val likes = parseCount(c["voteCount"].text()) ?: 0L
            Comment(id, author, text, likeCount = likes, publishedText = published)
        }

    internal fun parseAbout(json: JsonElement, channelId: String): ChannelAbout {
        val vm = json.findAll("aboutChannelViewModel").firstOrNull()
        if (vm == null) {
            // Fallback to landing header description
            val desc = json.findAll("pageHeaderViewModel").firstOrNull().at("description", "descriptionPreviewViewModel", "description", "content").string ?: ""
            return ChannelAbout(description = desc)
        }

        val desc = vm["description"].string ?: vm["description"].text() ?: ""
        val country = vm["country"].string
        val joined = vm.at("joinedDateText", "content").string ?: vm["joinedDateText"].text()
        val views = vm["viewCountText"].string ?: vm["viewCountText"].text()
        val subs = vm["subscriberCountText"].string ?: vm["subscriberCountText"].text()
        val videos = vm["videoCountText"].string ?: vm["videoCountText"].text()
        val customUrl = vm["canonicalChannelUrl"].string

        val links = vm["links"].array.mapNotNull { l ->
            val linkVm = l["channelExternalLinkViewModel"] ?: return@mapNotNull null
            val title = linkVm.at("title", "content").string ?: linkVm["title"].text() ?: ""
            val displayUrl = linkVm.at("link", "content").string ?: linkVm["link"].text() ?: ""
            val rawUrl = linkVm.findAll("urlEndpoint").firstNotNullOfOrNull { it["url"].string } ?: displayUrl
            // Extract clean redirect target if present
            val cleanUrl = extractRedirectUrl(rawUrl)
            val icon = linkVm.at("favicon", "sources").array.lastOrNull().at("url").string
            ChannelAboutLink(title, cleanUrl, displayUrl, icon)
        }

        return ChannelAbout(desc, country, joined, views, subs, videos, customUrl, links)
    }

    private fun extractRedirectUrl(url: String): String {
        if (url.contains("youtube.com/redirect") && url.contains("q=")) {
            val q = Regex("""[?&]q=([^&]+)""").find(url)?.groupValues?.get(1)
            if (q != null) return runCatching { java.net.URLDecoder.decode(q, "UTF-8") }.getOrDefault(url)
        }
        return url
    }

    private fun parseCount(text: String?): Long? {
        if (text == null) return null
        val m = Regex("""([\d.,]+)\s*([KMBkmb])?""").find(text) ?: return null
        val num = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val factor = when (m.groupValues[2].uppercase()) {
            "K" -> 1_000.0
            "M" -> 1_000_000.0
            "B" -> 1_000_000_000.0
            else -> 1.0
        }
        return (num * factor).toLong()
    }

    private fun parseDuration(text: String?): Long? {
        if (text == null) return null
        val parts = text.split(':').mapNotNull { it.trim().toLongOrNull() }
        if (parts.size !in 2..3) return null
        return parts.fold(0L) { acc, n -> acc * 60 + n }
    }

    companion object {
        const val VIDEOS_PARAMS = "EgZ2aWRlb3PyBgQKAjoA"
        const val SHORTS_PARAMS = "EgZzaG9ydHPyBgUKA5oBAA=="
        const val STREAMS_PARAMS = "EgdzdHJlYW1z8gYECgJ6AA=="
        const val PLAYLISTS_PARAMS = "EglwbGF5bGlzdHPyBgoKCEIGCgIQaCIA"
        const val COURSES_PARAMS = "Egdjb3Vyc2Vz8gYFCgPCAQA="
        const val POSTS_PARAMS = "EgVwb3N0c_IGBAoCSgA="
        const val SEARCH_PARAMS = "EgZzZWFyY2jyBgQKAloA"

        fun defaultParamsFor(kind: ChannelTabKind): String? = when (kind) {
            ChannelTabKind.VIDEOS -> VIDEOS_PARAMS
            ChannelTabKind.SHORTS -> SHORTS_PARAMS
            ChannelTabKind.STREAMS -> STREAMS_PARAMS
            ChannelTabKind.PLAYLISTS -> PLAYLISTS_PARAMS
            ChannelTabKind.COURSES -> COURSES_PARAMS
            ChannelTabKind.POSTS -> POSTS_PARAMS
            ChannelTabKind.SEARCH -> SEARCH_PARAMS
            else -> null
        }
    }
}
