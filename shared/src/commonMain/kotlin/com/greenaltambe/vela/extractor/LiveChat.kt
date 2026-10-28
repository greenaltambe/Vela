package com.greenaltambe.vela.extractor

enum class ChatKind { TEXT, SUPERCHAT, MEMBER }

/** One line of chat. [amount] is set for a paid message ("$5.00"). Emoji are kept as their text form (":smile:" or the character). */
data class ChatMessage(val id: String, val author: String, val text: String, val kind: ChatKind = ChatKind.TEXT, val amount: String? = null, val timestampMs: Long = 0,
    /** Replay only: when in the video this message appeared (ms from the start). */
    val offsetMs: Long? = null)

/** What one poll returned and how long the service asked us to wait before the next one. */
data class ChatBatch(val messages: List<ChatMessage>, val nextPollMs: Long, val ended: Boolean = false)

/** An open chat (live or replay). [poll] is one request; the caller waits [ChatBatch.nextPollMs] (never less than a few seconds) between polls and stops when it is done. */
interface ChatChannel {
    val isReplay: Boolean
    suspend fun poll(): ChatBatch

    /** Replay only: continue from [positionMs] of the video (after the viewer seeks). Live chat ignores it. */
    suspend fun seek(positionMs: Long) {}
}

/** Read-only, anonymous. Null from [open] means this video has no chat (or it could not be reached). */
interface LiveChatSource {
    suspend fun open(videoId: String): ChatChannel?
}
