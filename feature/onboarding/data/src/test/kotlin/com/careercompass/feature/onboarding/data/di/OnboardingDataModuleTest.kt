package com.careercompass.feature.onboarding.data.di

import com.careercompass.core.datastore.StoreScope
import com.careercompass.feature.onboarding.data.OnboardingProgressRepositoryImpl
import com.careercompass.feature.onboarding.data.support.FakeLocalStoreRegistry
import com.careercompass.feature.onboarding.domain.model.OnboardingProgress
import com.careercompass.feature.onboarding.domain.model.OnboardingStep
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 진행 상태 저장소가 등록하는 이름·스코프를 고정한다.
 *
 * 이름을 바꾸면 진행 중이던 사용자의 재개 지점이 사라지고, DEVICE 로 바꾸면 로그아웃한 기기에 앞 계정의
 * 재개 지점이 남는다. 둘 다 provider 안에만 적혀 있어 값 자체를 여기서 다시 적어 대조한다.
 */
class OnboardingDataModuleTest {
    private val registry = FakeLocalStoreRegistry()

    @Test
    fun `진행 상태 저장소는 SESSION 스코프로 등록한다`() {
        OnboardingProgressStoreModule.provideOnboardingProgressDataStore(registry)

        assertEquals(listOf("OnboardingProgress" to StoreScope.SESSION), registry.registrations)
    }

    @Test
    fun `같은 이름을 다시 요청하면 같은 저장소를 돌려받는다`() {
        val first = OnboardingProgressStoreModule.provideOnboardingProgressDataStore(registry)
        val second = OnboardingProgressStoreModule.provideOnboardingProgressDataStore(registry)

        assertSame(first, second)
    }

    @Test
    fun `로그아웃으로 SESSION 스코프가 비워지면 재개 지점도 사라진다`() =
        runTest {
            val repository =
                OnboardingProgressRepositoryImpl(
                    dataStore = OnboardingProgressStoreModule.provideOnboardingProgressDataStore(registry),
                    localStoreRegistry = registry,
                )
            assertTrue(repository.save(OnboardingStep.Experience).isSuccess)
            assertEquals(OnboardingProgress.InProgress(OnboardingStep.Experience), repository.progress.first())

            registry.clearScope(StoreScope.SESSION)

            assertEquals(OnboardingProgress.NotStarted, repository.progress.first())
        }
}
