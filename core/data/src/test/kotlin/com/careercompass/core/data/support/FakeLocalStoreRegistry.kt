package com.careercompass.core.data.support

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import com.careercompass.core.datastore.LocalStoreRegistry
import com.careercompass.core.datastore.StoreScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** 메모리 저장소로 동작하는 [LocalStoreRegistry]. clearScope 호출을 기록하고 실제 구현처럼 세대를 올린다. */
internal class FakeLocalStoreRegistry : LocalStoreRegistry {
    private val stores = mutableMapOf<String, Pair<StoreScope, InMemoryPreferencesDataStore>>()
    private val generation = MutableStateFlow(0L)
    var clearedScopes = mutableListOf<StoreScope>()

    override val sessionGeneration: StateFlow<Long> = generation.asStateFlow()

    override fun store(
        name: String,
        scope: StoreScope,
    ): DataStore<Preferences> = stores.getOrPut(name) { scope to InMemoryPreferencesDataStore() }.second

    /** 이미 획득된 [name] 저장소의 쓰기를 실패시킨다 — 저장 실패 갈래를 타게 하는 스위치. */
    fun failWrites(name: String) {
        stores.getValue(name).second.failOnWrite = true
    }

    override suspend fun clearScope(scope: StoreScope) {
        clearedScopes += scope
        if (scope == StoreScope.SESSION) generation.update { it + 1 }
        stores.values.filter { it.first == scope }.forEach { (_, store) -> store.edit { it.clear() } }
    }
}
