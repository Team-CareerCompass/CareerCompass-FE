package com.careercompass.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.IOException

/** 파일 없이 동작하는 테스트용 [DataStore]. [failOnWrite] 가 켜지면 모든 쓰기가 [IOException] 으로 끝난다. */
internal class InMemoryPreferencesDataStore(
    initial: Preferences = emptyPreferences(),
) : DataStore<Preferences> {
    private val state = MutableStateFlow(initial)
    var failOnWrite: Boolean = false

    override val data: Flow<Preferences> get() = state

    override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
        if (failOnWrite) throw IOException("write failed")
        val updated = transform(state.value.toMutablePreferences()).toMutablePreferences()
        state.value = updated
        return updated
    }
}
