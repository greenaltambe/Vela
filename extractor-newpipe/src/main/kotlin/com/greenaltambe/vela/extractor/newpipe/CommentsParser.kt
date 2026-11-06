package com.greenaltambe.vela.extractor.newpipe

import com.grack.nanojson.JsonArray
import com.grack.nanojson.JsonObject
import com.greenaltambe.vela.extractor.Comment

/**
 * Reads YouTube's comment responses (the "view model" format: the list names each comment by a key, and the comments themselves
 * come in `frameworkUpdates.entityBatchUpdate`). NewPipe's own comment parser drops the commenter's picture for this format, so
 * the app reads the response itself; everything NewPipe gave is kept (text, likes, time, pinned, reply count and reply paging).
 */
internal object CommentsParser {
    class Parsed(
        val comments: List<Comment>,
        /** The token of the next page of this list (more comments, or more replies), if any. */
        val next: String?,
        /** The video's whole comment count, from the header (first page only). */
        val total: Long?,
        /** The continuation tokens of the sort menu, in menu order (Top comments, Newest first). */
        val sortTokens: List<String>,
    )

    fun parse(response: JsonObject): Parsed {
        val entities = HashMap<String, JsonObject>()
        // The toolbar state (liked / hearted by the creator) is its own entity, found by the comment's toolbarStateKey. (The toolbar's own
        // "heartActiveTooltip" is on every comment, hearted or not, so it says nothing.)
        val hearted = HashSet<String>()
        response.getObject("frameworkUpdates").getObject("entityBatchUpdate").getArray("mutations").filterIsInstance<JsonObject>().forEach { m ->
            val payload = m.getObject("payload")
            payload.getObject("commentEntityPayload").takeIf { it.isNotEmpty() }?.let { p -> p.getString("key")?.let { entities[it] = p } }
            payload.getObject("engagementToolbarStateEntityPayload").takeIf { it.isNotEmpty() }?.let { t ->
                if (t.getString("heartState", "") == "TOOLBAR_HEART_STATE_HEARTED") t.getString("key")?.let(hearted::add)
            }
        }
        val items = ArrayList<JsonObject>()
        response.getArray("onResponseReceivedEndpoints").filterIsInstance<JsonObject>().forEach { e ->
            (e.getObject("reloadContinuationItemsCommand").takeIf { it.has("continuationItems") } ?: e.getObject("appendContinuationItemsAction").takeIf { it.has("continuationItems") })
                ?.getArray("continuationItems")?.filterIsInstance<JsonObject>()?.let(items::addAll)
        }
        val comments = ArrayList<Comment>()
        var next: String? = null
        items.forEach { item ->
            val renderer = item.getObject("continuationItemRenderer")
            if (renderer.isNotEmpty()) {
                // More comments: the item is the trigger itself; more replies: a "Show more replies" button holds the command.
                next = (renderer.getObject("continuationEndpoint").getObject("continuationCommand").getString("token")
                    ?: renderer.getObject("button").getObject("buttonRenderer").getObject("command").getObject("continuationCommand").getString("token"))?.takeIf { it.isNotEmpty() }
                return@forEach
            }
            val thread = item.getObject("commentThreadRenderer")
            // A thread wraps the view model twice; a reply (a plain list item of a replies response) once.
            val view = (thread.takeIf { it.isNotEmpty() } ?: item).getObject("commentViewModel").let { vm -> if (vm.has("commentKey")) vm else vm.getObject("commentViewModel") }
            val key = view.getString("commentKey") ?: return@forEach
            val entity = entities[key] ?: return@forEach
            val replies = thread.getObject("replies").getObject("commentRepliesRenderer").getArray("contents").filterIsInstance<JsonObject>()
                .firstNotNullOfOrNull { it.getObject("continuationItemRenderer").getObject("continuationEndpoint").getObject("continuationCommand").getString("token")?.takeIf { t -> t.isNotEmpty() } }
            comments += toComment(entity, pinned = view.has("pinnedText"), repliesToken = replies, hearted = entity.getObject("properties").getString("toolbarStateKey") in hearted)
        }
        val header = findFirst(response) { it.has("commentsHeaderRenderer") }?.getObject("commentsHeaderRenderer")
        val total = header?.getObject("countText")?.getArray("runs")?.filterIsInstance<JsonObject>()?.joinToString("") { it.getString("text", "") }?.filter { it.isDigit() }?.toLongOrNull()
        val sort = findFirst(response) { it.has("subMenuItems") }?.getArray("subMenuItems")?.filterIsInstance<JsonObject>().orEmpty()
            .mapNotNull { it.getObject("serviceEndpoint").getObject("continuationCommand").getString("token")?.takeIf { t -> t.isNotEmpty() } }
        return Parsed(comments, next, total, sort)
    }

    private fun toComment(e: JsonObject, pinned: Boolean, repliesToken: String?, hearted: Boolean): Comment {
        val props = e.getObject("properties"); val author = e.getObject("author"); val toolbar = e.getObject("toolbar")
        val published = props.getString("publishedTime", "")
        return Comment(
            id = props.getString("commentId", ""),
            author = author.getString("displayName", ""),
            text = props.getObject("content").getString("content", ""),
            likeCount = parseCount(toolbar.getString("likeCountNotliked", "")),
            publishedText = published.removeSuffix(" (edited)").trim(),
            replyCount = toolbar.getString("replyCount", "").filter { it.isDigit() }.toIntOrNull() ?: 0,
            isPinned = pinned,
            repliesContinuation = repliesToken?.let { org.schabi.newpipe.extractor.Page("https://www.youtube.com/youtubei/v1/next?prettyPrint=false", it).toTokenOrNull() },
            authorAvatarUrl = author.getString("avatarThumbnailUrl")?.takeIf { it.isNotBlank() },
            isByCreator = author.getBoolean("isCreator", false),
            isHearted = hearted,
            isVerified = author.getBoolean("isVerified", false),
            isEdited = published.contains("(edited)"),
        )
    }

    /** "221K" -> 221000, "1.2M" -> 1200000, "1,234" -> 1234, "" -> 0. */
    internal fun parseCount(text: String): Long {
        val t = text.trim().replace(",", "")
        if (t.isEmpty()) return 0
        val unit = when (t.last().uppercaseChar()) { 'K' -> 1_000.0; 'M' -> 1_000_000.0; 'B' -> 1_000_000_000.0; else -> 1.0 }
        val number = (if (unit == 1.0) t else t.dropLast(1)).toDoubleOrNull() ?: return 0
        return (number * unit).toLong()
    }

    private fun findFirst(root: Any?, test: (JsonObject) -> Boolean): JsonObject? {
        when (root) {
            is JsonObject -> { if (test(root)) return root; root.values.forEach { findFirst(it, test)?.let { f -> return f } } }
            is JsonArray -> root.forEach { findFirst(it, test)?.let { f -> return f } }
        }
        return null
    }
}
