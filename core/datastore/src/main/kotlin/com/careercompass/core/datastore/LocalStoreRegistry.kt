package com.careercompass.core.datastore

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import kotlinx.coroutines.flow.StateFlow

/**
 * 앱의 모든 Preferences DataStore 를 만들어 나눠주는 단일 창구.
 *
 * 각 모듈이 `preferencesDataStore(name = ...)` 델리게이트를 독립 선언하면 (1) 로그아웃 정리 대상 등록을
 * 빼먹어도 어디서도 안 걸리고, (2) 파일명 충돌을 아무도 못 막는다. 레지스트리 경유로 바꾸면 획득 시점에
 * 수명([StoreScope])이 함께 등록되고, 같은 name 은 항상 같은 인스턴스를 돌려받는다(DataStore 는 파일당
 * 인스턴스 1개가 강제 조건).
 *
 * 반환 타입은 [DataStore] 그대로다 — 타입드 접근자(스키마)는 각 DataSource 가 유지한다.
 */
public interface LocalStoreRegistry {
    /**
     * 세션 세대 — [clearScope] 가 [StoreScope.SESSION] 을 비울 때마다 오른다(로그아웃·세션 정리·새 로그인).
     *
     * 세션 경계를 넘긴 쓰기를 가려내는 기준이다. 서버 응답을 기다리는 동안 세션이 끝나면 그 응답은 다음 계정의
     * 저장소에 들어가서는 안 되므로, SESSION 스코프 쓰기는 시작 시점의 이 값을 들고 갔다가 커밋 직전에
     * 대조한다([editWithinSession]). 구독하면 지금 세대를 먼저 한 번 내고, 이후 경계마다 낸다.
     */
    public val sessionGeneration: StateFlow<Long>

    /**
     * `files/datastore/<name>.preferences_pb` 를 쓰는 DataStore 를 만들어 돌려주고, [scope] 를 수명으로
     * 등록한다. 같은 [name] 재요청은 같은 인스턴스를 돌려주며, 같은 [name] 을 다른 [scope] 로 재요청하면
     * 즉시 실패한다.
     *
     * [name] 은 저장 파일명 계약이다 — 바꾸면 기존 사용자의 데이터가 통째로 끊긴다.
     */
    public fun store(
        name: String,
        scope: StoreScope,
    ): DataStore<Preferences>

    /**
     * [scope] 로 등록된 모든 저장소의 키를 비운다(파일은 유지).
     *
     * 등록 이력은 디스크에도 남으므로, 이번 프로세스에서 아직 획득된 적 없는 저장소도 함께 지워진다 —
     * Hilt provider 는 최초 주입 때에야 실행되므로 in-memory 등록만으로는 "이전 프로세스에서 쓰고 이번
     * 프로세스에서 안 연" 저장소가 새는 구멍이 있다.
     */
    public suspend fun clearScope(scope: StoreScope)

    public companion object {
        /**
         * 세션 정리에서 가장 먼저 비우는 저장소 이름.
         *
         * 정리가 중간에 끊겨 한 저장소만 비우게 되더라도 그 하나는 토큰이어야 한다. 토큰이 남으면 화면은
         * 로그아웃됐는데 시작 판정이 남은 토큰으로 세션을 다시 세운다(#367).
         */
        public const val TOKEN_STORE_NAME: String = "Token"
    }
}
