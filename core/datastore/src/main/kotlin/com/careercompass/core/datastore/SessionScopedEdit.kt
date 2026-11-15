package com.careercompass.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit

/**
 * SESSION 스코프 저장소에 세션 세대 가드를 걸고 쓴다 — [startedAt] 세대가 아직 살아 있을 때만 값을 넣는다.
 *
 * 쓸 값을 만드는 동안(서버 응답 대기) 세션이 끝나면 그 값은 다음 계정의 저장소에 들어가서는 안 된다. 그래서
 * 값을 만들기 시작한 시점의 [LocalStoreRegistry.sessionGeneration] 을 들고 와 커밋 직전에 대조한다.
 *
 * 대조를 DataStore 갱신 트랜잭션 안에서 하는 것이 요점이다 — [LocalStoreRegistry.clearScope] 의 비우기와 같은
 * 줄에 서므로 「비운 뒤 늦게 도착한 쓰기」 가 검사와 커밋 사이를 비집고 들어갈 틈이 없다. 대조에서 걸리면
 * 저장소는 손대지 않은 채로 남는다.
 *
 * 값을 만드는 동안 기다림이 없는 쓰기는 [startedAt] 을 생략한다 — 호출 시점이 곧 시작 시점이다.
 */
public suspend fun LocalStoreRegistry.editWithinSession(
    store: DataStore<Preferences>,
    startedAt: Long = sessionGeneration.value,
    transform: (MutablePreferences) -> Unit,
) {
    store.edit { preferences ->
        if (sessionGeneration.value == startedAt) transform(preferences)
    }
}
