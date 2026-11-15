package com.careercompass.feature.foryou.presentation.roadmap

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.RoadmapMetric
import com.careercompass.feature.foryou.domain.testing.FakeForYouRepository
import com.careercompass.feature.foryou.domain.usecase.GetRoadmapComparisonUseCase
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException
import kotlin.coroutines.Continuation
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

@OptIn(ExperimentalCoroutinesApi::class)
class RoadmapViewModelTest {
    private val repository = FakeForYouRepository(onGetRoadmapComparison = { Result.success(comparison(it)) })
    private val reported = mutableListOf<Throwable>()
    private val reporter =
        object : ErrorReporter {
            override fun writeFailure(
                throwable: Throwable,
                attributes: Map<String, String>,
            ) {
                reported += throwable
            }
        }

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(handle: SavedStateHandle = SavedStateHandle()) =
        RoadmapViewModel(GetRoadmapComparisonUseCase(repository), reporter, handle)

    @Test fun `첫 resume는 중복 조회하지 않고 프로필 화면에서 돌아오면 다시 읽는다`() {
        val vm = viewModel()
        vm.onIntent(RoadmapIntent.Resumed)
        assertEquals(1, repository.roadmapCalls.size)
        vm.onIntent(RoadmapIntent.Resumed)
        assertEquals(2, repository.roadmapCalls.size)
    }

    @Test fun `세 코호트를 요청하고 마지막 선택만 새 프로세스에 복원한다`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        vm.onIntent(RoadmapIntent.SelectCohort(RoadmapCohort.Senior))
        vm.onIntent(RoadmapIntent.SelectCohort(RoadmapCohort.MeOnly))
        assertEquals(RoadmapCohort.entries, repository.roadmapCalls.toList())
        val restored = viewModel(SavedStateHandle(mapOf("roadmap.cohort" to handle.get<String>("roadmap.cohort"))))
        assertEquals(RoadmapCohort.MeOnly, restored.uiState.value.cohort)
        assertEquals(RoadmapCohort.MeOnly, repository.roadmapCalls.last())
    }

    @Test fun `취소에 협조하지 않는 이전 코호트 응답도 현재 비교를 덮지 못한다`() {
        lateinit var delayed: Continuation<Result<RoadmapComparison>>
        repository.onGetRoadmapComparison = { cohort ->
            if (cohort == RoadmapCohort.Peer) suspendCoroutine { delayed = it } else Result.success(comparison(cohort))
        }
        val vm = viewModel()
        vm.onIntent(RoadmapIntent.SelectCohort(RoadmapCohort.Senior))
        delayed.resume(Result.success(comparison(RoadmapCohort.Peer)))
        assertEquals(
            RoadmapCohort.Senior,
            vm.uiState.value.comparison
                ?.cohort,
        )
        assertFalse(vm.uiState.value.isLoading)
    }

    @Test fun `조회 실패 재시도는 로딩을 보이고 중복 요청을 만들지 않는다`() {
        repository.onGetRoadmapComparison = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException())) }
        val vm = viewModel()
        assertEquals(FailureKind.NoConnection, vm.uiState.value.failure)
        val response = CompletableDeferred<Result<RoadmapComparison>>()
        repository.onGetRoadmapComparison = { response.await() }
        vm.onIntent(RoadmapIntent.Refresh)
        vm.onIntent(RoadmapIntent.Refresh)
        assertTrue(vm.uiState.value.isLoading)
        assertNull(vm.uiState.value.failure)
        assertEquals(2, repository.roadmapCalls.size)
        response.complete(Result.success(comparison(RoadmapCohort.Peer)))
        assertFalse(vm.uiState.value.isLoading)
        assertEquals(
            86,
            vm.uiState.value.comparison
                ?.sampleSize,
        )
    }

    @Test fun `프로필 부족과 표본 없는 성공은 다른 상태다`() {
        repository.onGetRoadmapComparison = { Result.failure(CoreDataFailure.ProfileIncomplete("PROFILE_INCOMPLETE", Exception())) }
        val vm = viewModel()
        assertEquals(FailureKind.ProfileIncomplete, vm.uiState.value.failure)
        repository.onGetRoadmapComparison = { Result.success(comparison(it).copy(sampleSize = 0, metrics = emptyList())) }
        vm.onIntent(RoadmapIntent.Refresh)
        assertNull(vm.uiState.value.failure)
        assertEquals(
            0,
            vm.uiState.value.comparison
                ?.sampleSize,
        )
    }

    @Test fun `401은 비교와 복원 선택을 지우고 세션 종료 신호를 소비한다`() {
        val handle = SavedStateHandle()
        val vm = viewModel(handle)
        repository.onGetRoadmapComparison = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", Exception())) }
        vm.onIntent(RoadmapIntent.Refresh)
        assertNull(vm.uiState.value.comparison)
        assertNull(handle.get<String>("roadmap.cohort"))
        assertTrue(vm.uiState.value.sessionEnded)
        vm.onIntent(RoadmapIntent.ConsumeSessionEnded)
        assertFalse(vm.uiState.value.sessionEnded)
    }

    @Test fun `계획된 503은 공통 문구로 분류하고 오류 보고를 생략한다`() {
        repository.onGetRoadmapComparison = { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", Exception())) }
        assertEquals(FailureKind.ServiceUnavailable, viewModel().uiState.value.failure)
        assertTrue(reported.isEmpty())
    }

    private fun comparison(cohort: RoadmapCohort) = RoadmapComparison(cohort, 86, listOf(RoadmapMetric("프로젝트 수", 4.0, 2.0)), emptyList())
}
