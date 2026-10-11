package com.snapkode.nivolauncher.pagelet

import com.snapkode.nivolauncher.data.LayoutStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pagelet configs, kept in step with the layout. A Pagelet's *placement* is a normal workspace item owned by the view
 * model; this only holds what is particular to Pagelets (their config), persisted through [LayoutStore].
 */
class PageletRepository(private val store: LayoutStore) {
    private val _configs = MutableStateFlow(store.loadPageletConfigs())
    val configs: StateFlow<Map<String, PageletConfig>> = _configs.asStateFlow()

    fun configOf(id: String): PageletConfig =
        _configs.value[id] ?: PageletConfig.defaultsFor(pageletTypeOf(id) ?: PageletType.SKY)

    fun put(id: String, config: PageletConfig) {
        store.savePageletConfig(id, config)
        _configs.value = _configs.value + (id to config)
    }

    fun remove(id: String) {
        store.savePageletConfig(id, null)
        _configs.value = _configs.value - id
    }

    /** Re-read everything after a backup was restored. */
    fun reload() {
        _configs.value = store.loadPageletConfigs()
    }

    /** Forget configs of Pagelets that no longer exist on the workspace (e.g. after a restore of a trimmed layout). */
    fun retainOnly(existingIds: Set<String>) {
        val stale = _configs.value.keys.filter { it !in existingIds }
        stale.forEach(::remove)
    }
}
