package com.greenaltambe.vela.library

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** One channel from an import file. */
data class ImportedChannel(val channelId: String, val name: String)

class ImportResult(val channels: List<ImportedChannel>, val skipped: Int)

private val channelIdRegex = Regex("""UC[\w-]{22}""")

/** "https://www.youtube.com/channel/UCxxxx" -> UCxxxx. Handle (/@name) and /user/ URLs carry no id and give null. */
fun channelIdFromUrl(url: String): String? =
    Regex("""youtube\.com/channel/(UC[\w-]{22})""").find(url)?.groupValues?.get(1)

/**
 * YouTube Takeout's `subscriptions.csv`: a header row `Channel Id,Channel Url,Channel Title`, then one row per channel.
 * Quoted fields (titles with commas) are handled. Rows without a usable channel id are counted as skipped.
 */
fun parseTakeoutCsv(csv: String): ImportResult {
    val rows = parseCsv(csv.removePrefix("﻿"))
    if (rows.isEmpty()) return ImportResult(emptyList(), 0)
    val header = rows.first().map { it.trim().lowercase() }
    val idCol = header.indexOf("channel id").takeIf { it >= 0 } ?: 0
    val urlCol = header.indexOf("channel url")
    val titleCol = header.indexOf("channel title").takeIf { it >= 0 } ?: 2
    val hasHeader = header.contains("channel id") || header.contains("channel title")
    var skipped = 0
    val channels = rows.drop(if (hasHeader) 1 else 0).mapNotNull { row ->
        if (row.all { it.isBlank() }) return@mapNotNull null
        val id = row.getOrNull(idCol)?.trim()?.takeIf { channelIdRegex.matches(it) }
            ?: row.getOrNull(urlCol)?.let(::channelIdFromUrl)
        val title = row.getOrNull(titleCol)?.trim().orEmpty()
        if (id == null) { skipped++; null } else ImportedChannel(id, title.ifEmpty { id })
    }.distinctBy { it.channelId }
    return ImportResult(channels, skipped)
}

/** Minimal RFC 4180 reader: quotes, doubled quotes, commas and newlines inside quotes. */
internal fun parseCsv(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var inQuotes = false
    var i = 0
    fun endField() { row.add(field.toString()); field.clear() }
    fun endRow() { endField(); rows.add(row); row = mutableListOf() }
    while (i < text.length) {
        val c = text[i]
        when {
            inQuotes && c == '"' && text.getOrNull(i + 1) == '"' -> { field.append('"'); i++ }
            c == '"' -> inQuotes = !inQuotes
            !inQuotes && c == ',' -> endField()
            !inQuotes && c == '\r' -> {}
            !inQuotes && c == '\n' -> endRow()
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) endRow()
    return rows
}

/**
 * NewPipe's subscription export: `{"subscriptions":[{"service_id":0,"url":"https://www.youtube.com/channel/UC...","name":"..."}]}`.
 * Only the YouTube service (id 0) is imported.
 */
fun parseNewPipeJson(json: String): ImportResult {
    val root = runCatching { Json.parseToJsonElement(json) }.getOrNull() as? JsonObject ?: return ImportResult(emptyList(), 0)
    val subs = root["subscriptions"] as? JsonArray ?: return ImportResult(emptyList(), 0)
    var skipped = 0
    val channels = subs.mapNotNull { element ->
        val o = (element as? JsonObject) ?: run { skipped++; return@mapNotNull null }
        val service = o["service_id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull() ?: 0
        val url = o["url"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val id = channelIdFromUrl(url)
        if (service != 0 || id == null) { skipped++; null }
        else ImportedChannel(id, o["name"]?.jsonPrimitive?.contentOrNull?.ifBlank { null } ?: id)
    }.distinctBy { it.channelId }
    return ImportResult(channels, skipped)
}

/** Picks the parser by content: JSON starts with `{`, anything else is read as CSV. */
fun parseSubscriptionFile(text: String): ImportResult =
    if (text.trimStart('﻿', ' ', '\n', '\r', '\t').startsWith("{")) parseNewPipeJson(text) else parseTakeoutCsv(text)
