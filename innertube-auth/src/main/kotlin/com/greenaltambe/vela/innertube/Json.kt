package com.greenaltambe.vela.innertube

import kotlinx.serialization.json.*

/**
 * Lenient path navigation over YouTube's shifting JSON: nothing here throws on a missing or differently-typed node,
 * it returns null / empty, so one renamed field loses one value rather than the whole response.
 */
internal val lenientJson = Json { ignoreUnknownKeys = true; isLenient = true }

internal operator fun JsonElement?.get(key: String): JsonElement? = (this as? JsonObject)?.get(key)
internal operator fun JsonElement?.get(index: Int): JsonElement? = (this as? JsonArray)?.getOrNull(index)

/** `node.at("a", "b", 0, "c")`: strings step into objects, ints into arrays. */
internal fun JsonElement?.at(vararg path: Any): JsonElement? {
    var cur: JsonElement? = this
    for (p in path) cur = when (p) { is String -> cur[p]; is Int -> cur[p]; else -> null } ?: return null
    return cur
}

internal val JsonElement?.string: String? get() = (this as? JsonPrimitive)?.takeIf { it.isString }?.content
internal val JsonElement?.array: List<JsonElement> get() = (this as? JsonArray) ?: emptyList()
internal val JsonElement?.obj: JsonObject? get() = this as? JsonObject

/** Text of a `{simpleText}` or `{runs:[{text}]}` node. */
internal fun JsonElement?.text(): String? =
    this["simpleText"].string ?: this["runs"].array.mapNotNull { it["text"].string }.takeIf { it.isNotEmpty() }?.joinToString("")

/** Every object anywhere below this node that has [key], in document order: how renderers are found without knowing their nesting. */
internal fun JsonElement?.findAll(key: String): List<JsonElement> {
    val out = ArrayList<JsonElement>()
    fun walk(e: JsonElement?) {
        when (e) {
            is JsonObject -> e.forEach { (k, v) -> if (k == key) out += v else walk(v) }
            is JsonArray -> e.forEach(::walk)
            else -> {}
        }
    }
    walk(this)
    return out
}
