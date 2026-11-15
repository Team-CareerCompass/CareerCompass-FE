package com.careercompass.feature.onboarding.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.mutablePreferencesOf
import androidx.datastore.preferences.core.stringPreferencesKey
import com.careercompass.core.datastore.StoreScope
import com.careercompass.feature.onboarding.data.support.FakeLocalStoreRegistry
import com.careercompass.feature.onboarding.data.support.InMemoryPreferencesDataStore
import com.careercompass.feature.onboarding.domain.model.OnboardingProgress
import com.careercompass.feature.onboarding.domain.model.OnboardingStep
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class OnboardingProgressRepositoryImplTest {
    private val registry = FakeLocalStoreRegistry()
    private val dataStore = InMemoryPreferencesDataStore()
    private val repository = OnboardingProgressRepositoryImpl(dataStore, registry)

    @Test
    fun `저장 전에는 NotStarted 다`() =
        runTest {
            assertEquals(OnboardingProgress.NotStarted, repository.progress.first())
        }

    @Test
    fun `단계를 저장하면 같은 단계로 복원된다`() =
        runTest {
            assertTrue(repository.save(OnboardingStep.Experience).isSuccess)

            assertEquals(OnboardingProgress.InProgress(OnboardingStep.Experience), repository.progress.first())
            assertEquals(
                OnboardingProgress.InProgress(OnboardingStep.Experience),
                OnboardingProgressRepositoryImpl(dataStore, registry).progress.first(),
            )
        }

    @Test
    fun `완료 기록은 단계 값보다 우선한다`() =
        runTest {
            repository.save(OnboardingStep.PastApplication)

            assertTrue(repository.markCompleted().isSuccess)

            assertEquals(OnboardingProgress.Completed, repository.progress.first())
        }

    @Test
    fun `완료 뒤 단계를 다시 저장하면 진행 중으로 돌아간다`() =
        runTest {
            repository.markCompleted()

            repository.save(OnboardingStep.BasicInfo)

            assertEquals(OnboardingProgress.InProgress(OnboardingStep.BasicInfo), repository.progress.first())
        }

    @Test
    fun `clear 는 단계와 완료 기록을 모두 지운다`() =
        runTest {
            repository.save(OnboardingStep.JobPreference)
            repository.markCompleted()

            assertTrue(repository.clear().isSuccess)

            assertEquals(OnboardingProgress.NotStarted, repository.progress.first())
            assertTrue(dataStore.snapshot().asMap().isEmpty())
        }

    @Test
    fun `알 수 없는 단계 이름은 NotStarted 로 읽는다`() =
        runTest {
            val corrupted = InMemoryPreferencesDataStore(mutablePreferencesOf(stringPreferencesKey("step") to "Legacy"))

            assertEquals(OnboardingProgress.NotStarted, OnboardingProgressRepositoryImpl(corrupted, registry).progress.first())
        }

    /**
     * #348 — 세션이 끝난 뒤에 커밋된 기록은 다음 계정의 진입 판정을 앞 계정의 재개 지점이나 완료 상태로 연다.
     */
    @Test
    fun `세션이 끝난 뒤 커밋되는 기록은 저장소를 비운 채로 둔다`() =
        runTest {
            val store = registry.store("OnboardingProgress", StoreScope.SESSION)
            val gate = CompletableDeferred<Unit>()
            val repository = OnboardingProgressRepositoryImpl(GatedPreferencesDataStore(store, gate), registry)
            val saved = async { repository.save(OnboardingStep.Experience) }
            runCurrent()

            registry.clearScope(StoreScope.SESSION)
            gate.complete(Unit)
            saved.await().getOrThrow()

            assertEquals(OnboardingProgress.NotStarted, repository.progress.first())
        }

    @Test
    fun `쓰기 실패는 Result 실패로 돌려준다`() =
        runTest {
            dataStore.failOnWrite = true

            val result = repository.save(OnboardingStep.Experience)

            assertTrue(result.exceptionOrNull() is IOException)
            assertEquals(OnboardingProgress.NotStarted, repository.progress.first())
        }

    /** [gate] 가 열릴 때까지 쓰기를 붙잡아 둔다 — 세션이 끝난 뒤에 커밋되는 쓰기를 재현한다. */
    private class GatedPreferencesDataStore(
        private val delegate: DataStore<Preferences>,
        private val gate: CompletableDeferred<Unit>,
    ) : DataStore<Preferences> {
        override val data: Flow<Preferences> get() = delegate.data

        override suspend fun updateData(transform: suspend (t: Preferences) -> Preferences): Preferences {
            gate.await()
            return delegate.updateData(transform)
        }
    }
}
