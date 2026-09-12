package com.careercompass.feature.onboarding.data.support

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.careercompass.core.datastore.LocalStoreRegistry
import com.careercompass.core.datastore.StoreScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 메모리 저장소로 동작하는 [LocalStoreRegistry]. SESSION 을 비우면 실제 구현처럼 세대가 오른다. */
internal class FakeLocalStoreRegistry : LocalStoreRegistry {
    private val stores = mutableMapOf<String, Pair<StoreScope, DataStore<Preferences>>>()
    private val generation = MutableStateFlow(0L)

    override val sessionGeneration: StateFlow<Long> = generation.asStateFlow()

    override fun store(
        name: String,
        scope: StoreScope,
    ): DataStore<Preferences> = stores.getOrPut(name) { scope to InMemoryPreferencesDataStore() }.second

    override suspend fun clearScope(scope: StoreScope) {
        if (scope == StoreScope.SESSION) generation.update { it + 1 }
        stores.values.filter { it.first == scope }.forEach { (_, store) -> store.edit { it.clear() } }
    }
}
