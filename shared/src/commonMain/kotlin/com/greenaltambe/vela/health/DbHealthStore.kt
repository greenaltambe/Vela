package com.greenaltambe.vela.health

import com.greenaltambe.vela.core.settings.SettingLayer
import com.greenaltambe.vela.data.SqlDelightSettingsStore
import com.greenaltambe.vela.data.db.VelaDatabase

class DbHealthStore(db: VelaDatabase) : HealthStore {
    private val store = SqlDelightSettingsStore(db)
    override fun load() = store.get(KEY, SettingLayer.Global)?.let(HealthCodec::decode).orEmpty()
    override fun save(results: List<CanaryResult>) = store.put(KEY, SettingLayer.Global, HealthCodec.encode(results))
    override fun loadStreak() = store.get(STREAK, SettingLayer.Global)?.toIntOrNull() ?: 0
    override fun saveStreak(streak: Int) = store.put(STREAK, SettingLayer.Global, streak.toString())
    private companion object { const val KEY = "health.results"; const val STREAK = "health.failStreak" }
}
