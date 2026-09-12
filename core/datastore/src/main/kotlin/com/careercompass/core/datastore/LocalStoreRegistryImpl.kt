package com.careercompass.core.datastore

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStoreFile
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.io.File
import java.io.IOException
import javax.inject.Inject

/**
 * [LocalStoreRegistry] 구현.
 *
 * (name → scope) 등록은 in-memory 캐시와 별도로 자체 매니페스트 파일에도 영속한다. Hilt `@Provides` 는
 * 최초 주입 때에야 실행되므로, in-memory 만으로는 "이전 프로세스에서 기록되고 이번 프로세스에서 아직 안
 * 열린" 저장소를 [clearScope] 가 놓친다 — 매니페스트가 그 구멍을 막는다. 매니페스트 기록이 유실된 이름은
 * 다음 획득 때 다시 기록되므로 자가 복구된다.
 */
internal class LocalStoreRegistryImpl(
    private val produceFile: (name: String) -> File,
    private val registryScope: CoroutineScope,
) : LocalStoreRegistry {
    @Inject
    constructor(
        @ApplicationContext context: Context,
    ) : this(
        produceFile = { name -> context.preferencesDataStoreFile(name) },
        registryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    )

    /** [registeredByCode] false = 매니페스트 기록만으로 열림([clearScope] 경유) — 코드의 scope 선언이 오면 그쪽이 정본. */
    private class Entry(
        var scope: StoreScope,
        val dataStore: DataStore<Preferences>,
        var registeredByCode: Boolean,
    )

    private val lock = Any()
    private val entries = mutableMapOf<String, Entry>()
    private val pendingManifestJobs = mutableListOf<Job>()
    private val generation = MutableStateFlow(0L)

    override val sessionGeneration: StateFlow<Long> = generation.asStateFlow()

    private val manifest: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = newStoreScope()) { produceFile(MANIFEST_NAME) }
    }

    override fun store(
        name: String,
        scope: StoreScope,
    ): DataStore<Preferences> {
        require(name != MANIFEST_NAME) { "'$MANIFEST_NAME' 은 레지스트리 예약 이름입니다." }
        val (dataStore, needsRecord) =
            synchronized(lock) {
                val existing = entries[name]
                when {
                    existing == null -> {
                        val created = createStore(name)
                        entries[name] = Entry(scope, created, registeredByCode = true)
                        created to true
                    }

                    existing.registeredByCode -> {
                        check(existing.scope == scope) {
                            "저장소 '$name' 의 scope 충돌: ${existing.scope} 로 이미 등록됐는데 $scope 로 재요청됨"
                        }
                        existing.dataStore to false
                    }

                    else -> {
                        val scopeChanged = existing.scope != scope
                        existing.scope = scope
                        existing.registeredByCode = true
                        existing.dataStore to scopeChanged
                    }
                }
            }
        if (needsRecord) scheduleManifestRecord(name, scope)
        return dataStore
    }

    /**
     * 세대는 비우기 전에 올린다 — 그래야 이 시점 이후에 커밋되는 앞 세션의 쓰기가
     * [editWithinSession] 의 대조에서 전부 걸린다. 뒤에 올리면 비운 직후부터 세대를 올리기 전까지가
     * 그대로 구멍이 된다.
     *
     * 비우기는 저장소마다 따로 시도한다. 한 저장소가 [IOException] 으로 실패했다고 나머지를 건너뛰면 앞
     * 계정의 프로필·공고 스냅샷이 그대로 남아 다음 계정에 넘어가고, 호출부의 뒷정리도 실행되지 않는다.
     * 실패는 모아 두었다가 마지막에 첫 예외로 던진다(나머지는 suppressed).
     */
    override suspend fun clearScope(scope: StoreScope) {
        if (scope == StoreScope.SESSION) generation.update { it + 1 }
        awaitPendingRegistrations()
        val manifestNames = readManifestNames(scope)
        val targets =
            synchronized(lock) {
                val inMemoryNames = entries.filterValues { it.scope == scope }.keys
                (manifestNames + inMemoryNames).mapNotNull { name ->
                    val entry = entries[name]
                    when {
                        entry == null -> {
                            // 이번 프로세스에서 아직 획득되지 않은 저장소 — 매니페스트 기록으로 연다.
                            val created = createStore(name)
                            entries[name] = Entry(scope, created, registeredByCode = false)
                            name to created
                        }

                        entry.scope == scope -> {
                            name to entry.dataStore
                        }

                        // 코드에서 scope 가 바뀐 저장소 — 매니페스트가 낡았다. 지우지 않는다.
                        else -> {
                            null
                        }
                    }
                }
            }
        clearAll(targets)
    }

    /** 토큰을 먼저 비우고([LocalStoreRegistry.TOKEN_STORE_NAME]) 나머지를 잇는다 — 중간에 끊겨도 토큰만은 비어 있게. */
    private suspend fun clearAll(targets: List<Pair<String, DataStore<Preferences>>>) {
        var failure: IOException? = null
        targets
            .sortedBy { (name, _) -> if (name == LocalStoreRegistry.TOKEN_STORE_NAME) 0 else 1 }
            .forEach { (name, store) ->
                try {
                    store.edit { it.clear() }
                } catch (exception: IOException) {
                    Log.e(TAG, "저장소 비우기 실패($name): ${exception.javaClass.name}")
                    val first = failure
                    if (first == null) failure = exception else first.addSuppressed(exception)
                }
            }
        failure?.let { throw it }
    }

    /** 아직 디스크에 안 간 매니페스트 기록을 기다린다 — [clearScope] 선행 단계이자 재기동 테스트의 flush 지점. */
    internal suspend fun awaitPendingRegistrations() {
        synchronized(lock) { pendingManifestJobs.toList() }.joinAll()
    }

    private fun createStore(name: String): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = newStoreScope()) { produceFile(name) }

    /**
     * 저장소별 독립 [SupervisorJob] 을 [registryScope] 아래에 둔다 — 한 저장소의 실패가 다른 저장소로
     * 번지지 않으면서, 부모 취소 시 함께 닫혀 DataStore 의 "파일당 활성 인스턴스 1개" 등록이 해제된다.
     */
    private fun newStoreScope(): CoroutineScope =
        CoroutineScope(registryScope.coroutineContext + SupervisorJob(registryScope.coroutineContext.job))

    private fun scheduleManifestRecord(
        name: String,
        scope: StoreScope,
    ) {
        val job =
            registryScope.launch {
                // 실패해도 앱은 계속 가야 한다 — 이 이름은 다음 획득 때 재기록되고, 이번 프로세스의
                // clearScope 는 in-memory 등록으로 커버된다.
                runCatching { recordInManifest(name, scope) }
                    .onFailure { Log.e(TAG, "매니페스트 기록 실패($name): ${it.javaClass.name}") }
            }
        synchronized(lock) { pendingManifestJobs += job }
        job.invokeOnCompletion { synchronized(lock) { pendingManifestJobs.remove(job) } }
    }

    private suspend fun recordInManifest(
        name: String,
        scope: StoreScope,
    ) {
        manifest.edit { prefs ->
            StoreScope.entries.forEach { candidate ->
                val key = manifestKey(candidate)
                val current = prefs[key] ?: emptySet()
                // 소속 scope 에는 더하고 나머지에서는 빼서, scope 이동 시 낡은 기록이 남지 않게 한다.
                prefs[key] = if (candidate == scope) current + name else current - name
            }
        }
    }

    private suspend fun readManifestNames(scope: StoreScope): Set<String> =
        runCatching { manifest.data.first()[manifestKey(scope)] }
            .getOrElse { exception ->
                if (exception is IOException) {
                    // 매니페스트 손상 — in-memory 등록만으로 진행한다. 이름들은 다음 획득 때 재기록된다.
                    Log.e(TAG, "매니페스트 읽기 실패: ${exception.javaClass.name}")
                    null
                } else {
                    throw exception
                }
            }.orEmpty()

    private fun manifestKey(scope: StoreScope): Preferences.Key<Set<String>> =
        when (scope) {
            StoreScope.SESSION -> SESSION_NAMES
            StoreScope.DEVICE -> DEVICE_NAMES
        }

    companion object {
        private const val TAG = "LocalStoreRegistry"

        /** 레지스트리 자신의 (name → scope) 영속 기록 파일 — [store] 의 name 으로 쓸 수 없다. */
        internal const val MANIFEST_NAME = "local_store_registry"

        private val SESSION_NAMES = stringSetPreferencesKey("session_store_names")
        private val DEVICE_NAMES = stringSetPreferencesKey("device_store_names")
    }
}
