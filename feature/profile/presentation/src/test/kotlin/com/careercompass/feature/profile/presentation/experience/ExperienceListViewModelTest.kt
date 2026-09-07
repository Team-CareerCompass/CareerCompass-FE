package com.careercompass.feature.profile.presentation.experience

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.profile.domain.usecase.GetExperiencesUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
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

@OptIn(ExperimentalCoroutinesApi::class)
class ExperienceListViewModelTest {
    private class RecordingReporter : ErrorReporter {
        val recorded = mutableListOf<Map<String, String>>()

        override fun writeFailure(
            throwable: Throwable,
            attributes: Map<String, String>,
        ) {
            recorded += attributes
        }
    }

    private val reporter = RecordingReporter()
    private val experienceRepository = FakeExperienceRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ExperienceListViewModel(GetExperiencesUseCase(experienceRepository), reporter)

    @Test
    fun `첫 조회로 목록과 전체 개수를 받는다`() {
        experienceRepository.experiences += List(3) { card(it + 1L) }

        val state = viewModel().state.value

        assertEquals(3, state.cards.size)
        assertEquals(3, state.totalCount)
        assertFalse(state.isLimitReached)
        assertNull(state.emptyReason)
    }

    /** 서버가 422 를 줄 때까지 기다렸다 알리면 사용자는 폼을 다 채운 뒤에야 막힌다. */
    @Test
    fun `상한에 닿으면 추가를 막고 이유를 말한다`() {
        experienceRepository.onGetExperiences = { _, _, _ ->
            Result.success(CursorPage(items = List(30) { card(it + 1L) }, nextCursor = null))
        }
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLimitReached)

        viewModel.onEvent(ExperienceListEvent.AddClicked)

        assertEquals(ExperienceListMessage.LimitReached, viewModel.state.value.message)
        assertFalse(viewModel.state.value.isAddRequested)
    }

    @Test
    fun `상한 아래면 추가 요청을 올린다`() {
        experienceRepository.experiences += card(1L)
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.AddClicked)

        assertTrue(viewModel.state.value.isAddRequested)
    }

    @Test
    fun `필터를 바꾸면 커서와 목록을 처음으로 되돌린다`() {
        val queries = mutableListOf<Pair<ExperienceType?, String?>>()
        experienceRepository.onGetExperiences = { type, cursor, _ ->
            queries += type to cursor
            Result.success(CursorPage(items = listOf(card(1L)), nextCursor = "c1"))
        }
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Award)))

        assertEquals(listOf(null to null, ExperienceType.Award to null), queries)
        assertEquals(1, viewModel.state.value.cards.size)
    }

    /** 필터된 목록의 길이를 전체인 양 적으면 상한 안내가 거짓이 된다. */
    @Test
    fun `필터가 걸려 있으면 전체 개수를 모른다고 둔다`() {
        experienceRepository.onGetExperiences = { _, _, _ ->
            Result.success(CursorPage(items = List(2) { card(it + 1L) }, nextCursor = null))
        }
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Intern)))

        assertEquals(2, viewModel.state.value.totalCount)
        assertFalse(viewModel.state.value.isLimitReached)
    }

    @Test
    fun `이어 읽기는 앞 페이지에 붙이고 개수를 늘린다`() {
        val pages =
            mapOf<String?, CursorPage<Experience>>(
                null to CursorPage(items = List(20) { card(it + 1L) }, nextCursor = "c1"),
                "c1" to CursorPage(items = List(4) { card(it + 21L) }, nextCursor = null),
            )
        experienceRepository.onGetExperiences = { _, cursor, _ -> Result.success(pages.getValue(cursor)) }
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.LoadMore)

        assertEquals(24, viewModel.state.value.cards.size)
        assertEquals(24, viewModel.state.value.totalCount)
        assertNull(viewModel.state.value.nextCursor)
    }

    /** 이어 읽기 실패는 이미 보이는 목록을 덮지 않는다. */
    @Test
    fun `이어 읽기가 실패해도 목록은 남는다`() {
        var calls = 0
        experienceRepository.onGetExperiences = { _, _, _ ->
            calls++
            if (calls == 1) {
                Result.success(CursorPage(items = List(20) { card(it + 1L) }, nextCursor = "c1"))
            } else {
                Result.failure(IOException("offline"))
            }
        }
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.LoadMore)

        assertEquals(20, viewModel.state.value.cards.size)
        assertEquals(ExperienceListMessage.LoadMoreFailed, viewModel.state.value.message)
        assertFalse(viewModel.state.value.isFailureVisible)
    }

    @Test
    fun `읽은 카드가 없을 때만 실패가 화면을 덮는다`() {
        experienceRepository.onGetExperiences = { _, _, _ ->
            Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline")))
        }

        val state = viewModel().state.value

        assertTrue(state.isFailureVisible)
        assertEquals(FailureKind.NoConnection, state.loadFailure)
        assertEquals("experience_list", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `401 은 세션 종료로 올린다`() {
        experienceRepository.onGetExperiences = { _, _, _ ->
            Result.failure(CoreDataFailure.Unauthorized(code = "AUTH_INVALID", cause = IllegalStateException()))
        }

        assertEquals(ProfileSessionEnd.Expired, viewModel().state.value.sessionEnd)
    }

    /** 필터 때문에 빈 것과 정말 없는 것은 사용자가 할 일이 다르다. */
    @Test
    fun `빈 사유를 필터와 미등록으로 가른다`() {
        val viewModel = viewModel()
        assertEquals(ExperienceEmptyReason.NoCards, viewModel.state.value.emptyReason)

        viewModel.onEvent(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Certificate)))

        assertEquals(ExperienceEmptyReason.FilteredOut, viewModel.state.value.emptyReason)
    }

    private companion object {
        fun card(id: Long) =
            Experience(
                id = id,
                title = "카드 $id",
                startPoint = ExperiencePoint.YearMonth(2025, 3),
                endPoint = null,
                details = ExperienceDetails.Project(role = "Android", techs = emptyList(), summary = null, link = null),
                createdAt = null,
            )
    }
}

private fun ExperienceListViewModel.onEvent(event: ExperienceListEvent) = onIntent(ExperienceListIntent.Screen(event))

private val ExperienceListViewModel.state get() = uiState
