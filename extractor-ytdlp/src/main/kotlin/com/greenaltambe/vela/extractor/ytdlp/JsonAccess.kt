package com.greenaltambe.vela.extractor.ytdlp

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

/** Lenient accessors: yt-dlp omits or nulls fields freely, and a missing optional field must never be an error. */
internal fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is JsonNull && it.isString }?.content?.takeIf { it.isNotEmpty() }

internal fun JsonObject.long(key: String): Long? = (this[key] as? JsonPrimitive)?.let { it.longOrNull ?: it.doubleOrNull?.toLong() }

internal fun JsonObject.double(key: String): Double? = (this[key] as? JsonPrimitive)?.doubleOrNull

internal fun JsonObject.bool(key: String): Boolean? = (this[key] as? JsonPrimitive)?.booleanOrNull

internal fun JsonObject.objects(key: String): List<JsonObject> =
    (this[key] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }

internal fun JsonObject.stringMap(key: String): Map<String, String> =
    (this[key] as? JsonObject)?.mapNotNull { (k, v) -> (v as? JsonPrimitive)?.takeIf { it !is JsonNull }?.content?.let { k to it } }
        ?.toMap().orEmpty()

private fun JsonArray?.orEmpty(): List<JsonElement> = this ?: emptyList()
