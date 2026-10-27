package com.greenaltambe.vela.core.http

import com.greenaltambe.vela.data.db.VelaDatabase
import kotlin.time.Instant

class CachedResponse(val status: Int, val body: String, val fetchedAt: Instant)

/** Where [ApiClient] keeps what it fetched, so a repeat (or an offline start) does not need the network. */
interface ApiCache {
    fun get(key: String): CachedResponse?
    fun put(key: String, response: CachedResponse)
}

class InMemoryApiCache(private val capacity: Int = 512) : ApiCache {
    private val map = object : LinkedHashMap<String, CachedResponse>() {}

    @Synchronized override fun get(key: String): CachedResponse? = map.remove(key)?.also { map[key] = it } // refresh recency

    @Synchronized override fun put(key: String, response: CachedResponse) {
        map.remove(key)
        map[key] = response
        while (map.size > capacity) map.remove(map.keys.first())
    }
}

/** Memory in front of SQLite: reads hit memory first and fill it, writes go to both. */
class LayeredApiCache(private val memory: ApiCache, private val db: VelaDatabase) : ApiCache {
    override fun get(key: String): CachedResponse? = memory.get(key) ?: db.apiCacheQueries.selectEntry(key).executeAsOneOrNull()?.let {
        CachedResponse(it.status.toInt(), it.body, Instant.fromEpochMilliseconds(it.fetchedAt))
    }?.also { memory.put(key, it) }

    override fun put(key: String, response: CachedResponse) {
        memory.put(key, response)
        db.apiCacheQueries.upsertEntry(key, response.status.toLong(), response.body, response.fetchedAt.toEpochMilliseconds())
    }
}
