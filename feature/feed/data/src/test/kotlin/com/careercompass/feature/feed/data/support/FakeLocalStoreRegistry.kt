package com.careercompass.feature.feed.data.support

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.careercompass.core.datastore.LocalStoreRegistry
import com.careercompass.core.datastore.StoreScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 메모리 저장소로 동작하는 [LocalStoreRegistry]. 어떤 이름이 어느 scope 로 등록됐는지 [registrations] 로 검증한다. */
internal class FakeLocalStoreRegistry : LocalStoreRegistry {
    private val stores = mutableMapOf<String, Pair<StoreScope, DataStore<Preferences>>>()
    private val generation = MutableStateFlow(0L)
    val registrations = mutableListOf<Pair<String, StoreScope>>()
    val clearedScopes = mutableListOf<StoreScope>()

    override val sessionGeneration: StateFlow<Long> = generation.asStateFlow()

    override fun store(
        name: String,
        scope: StoreScope,
    ): DataStore<Preferences> {
        registrations += name to scope
        return stores.getOrPut(name) { scope to InMemoryPreferencesDataStore() }.second
    }

    override suspend fun clearScope(scope: StoreScope) {
        clearedScopes += scope
        if (scope == StoreScope.SESSION) generation.update { it + 1 }
        stores.values.filter { it.first == scope }.forEach { (_, store) -> store.edit { it.clear() } }
    }
}
