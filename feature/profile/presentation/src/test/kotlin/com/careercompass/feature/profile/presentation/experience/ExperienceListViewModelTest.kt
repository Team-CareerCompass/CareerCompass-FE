package com.careercompass.feature.profile.presentation.experience

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.component.ExperienceQuickAddEvent
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.profile.domain.usecase.CreateExperienceUseCase
import com.careercompass.feature.profile.domain.usecase.DeleteExperienceUseCase
import com.careercompass.feature.profile.domain.usecase.GetExperiencesUseCase
import com.careercompass.feature.profile.domain.usecase.UpdateExperienceUseCase
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
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

    private fun viewModel() =
        ExperienceListViewModel(
            getExperiences = GetExperiencesUseCase(experienceRepository),
            createExperience = CreateExperienceUseCase(experienceRepository),
            updateExperience = UpdateExperienceUseCase(experienceRepository),
            deleteExperience = DeleteExperienceUseCase(experienceRepository),
            errorReporter = reporter,
        )

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
        assertNull(viewModel.state.value.editor)
    }

    @Test
    fun `상한 아래면 빈 시트를 연다`() {
        experienceRepository.experiences += card(1L)
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.AddClicked)

        assertEquals(
            "",
            viewModel.state.value.editor
                ?.title,
        )
        assertNull(
            viewModel.state.value.editor
                ?.experienceId,
        )
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
        experienceRepository.onGetExperiences = { type, _, _ ->
            Result.success(CursorPage(items = List(if (type == null) 29 else 2) { card(it + 1L) }, nextCursor = null))
        }
        val viewModel = viewModel()
        assertEquals(29, viewModel.state.value.totalCount)

        viewModel.onEvent(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Intern)))

        assertNull(viewModel.state.value.totalCount)
        assertFalse(viewModel.state.value.isLimitReached)
        viewModel.onEvent(ExperienceListEvent.AddClicked)
        assertTrue(viewModel.state.value.editor != null)
    }

    @Test
    fun `29장 중 첫 20장만 읽었으면 그 수를 총계로 표시하지 않는다`() {
        experienceRepository.onGetExperiences = { _, cursor, _ ->
            Result.success(
                if (cursor == null) {
                    CursorPage(items = List(20) { card(it + 1L) }, nextCursor = "c1")
                } else {
                    CursorPage(items = List(9) { card(it + 21L) }, nextCursor = null)
                },
            )
        }
        val viewModel = viewModel()
        assertEquals(20, viewModel.state.value.cards.size)
        assertNull(viewModel.state.value.countLabelValue)

        viewModel.onEvent(ExperienceListEvent.LoadMore)

        assertEquals(29, viewModel.state.value.totalCount)
    }

    @Test
    fun `전체 30장 조회 뒤 필터 안에서 삭제하면 상한 잠금이 남지 않는다`() {
        experienceRepository.experiences += List(30) { card(it + 1L) }
        experienceRepository.onGetExperiences = { type, _, _ ->
            Result.success(
                CursorPage(
                    items =
                        if (type ==
                            null
                        ) {
                            experienceRepository.experiences.toList()
                        } else {
                            experienceRepository.experiences.take(2)
                        },
                    nextCursor = null,
                ),
            )
        }
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLimitReached)
        viewModel.onEvent(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Project)))
        viewModel.onEvent(ExperienceListEvent.DeleteClicked(1L))
        viewModel.onIntent(ExperienceListIntent.ConfirmDelete)

        assertEquals(29, experienceRepository.experiences.size)
        assertNull(viewModel.state.value.totalCount)
        viewModel.onEvent(ExperienceListEvent.AddClicked)
        assertTrue(viewModel.state.value.editor != null)
    }

    @Test
    fun `24장을 읽은 뒤 저장과 삭제로 갱신해도 목록을 비우거나 첫 페이지로 줄이지 않는다`() {
        experienceRepository.experiences += List(24) { card(it + 1L) }
        var refreshGate: CompletableDeferred<Unit>? = null
        experienceRepository.onGetExperiences = { _, cursor, _ ->
            refreshGate?.await()
            val all = experienceRepository.experiences.toList()
            Result.success(
                if (cursor == null) {
                    CursorPage(items = all.take(20), nextCursor = "c1")
                } else {
                    CursorPage(items = all.drop(20), nextCursor = null)
                },
            )
        }
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.LoadMore)
        val states = mutableListOf<ExperienceListUiState>()
        val scope = CoroutineScope(UnconfinedTestDispatcher())
        val collection = scope.launch { viewModel.uiState.collect { states += it } }
        try {
            refreshGate = CompletableDeferred()
            viewModel.onEvent(ExperienceListEvent.CardClicked(22L))
            viewModel.onEditor(ExperienceQuickAddEvent.TitleChanged("수정한 22번째 카드"))
            viewModel.onEditor(ExperienceQuickAddEvent.Submitted)
            assertTrue(viewModel.state.value.isLoading)
            assertEquals(24, viewModel.state.value.cards.size)
            refreshGate.complete(Unit)
            assertEquals(24, viewModel.state.value.totalCount)
            assertEquals(
                "수정한 22번째 카드",
                viewModel.state.value.cards
                    .single { it.id == 22L }
                    .title,
            )

            refreshGate = CompletableDeferred()
            viewModel.onEvent(ExperienceListEvent.DeleteClicked(23L))
            viewModel.onIntent(ExperienceListIntent.ConfirmDelete)
            assertEquals(24, viewModel.state.value.cards.size)
            refreshGate.complete(Unit)
            assertEquals(23, viewModel.state.value.cards.size)
            assertEquals(23, viewModel.state.value.totalCount)
            assertTrue(states.none { it.isInitialLoading || it.cards.isEmpty() })
            assertTrue(states.none { it.totalCount == 20 })
        } finally {
            collection.cancel()
            scope.cancel()
        }
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

    @Test
    fun `이어 읽기 대기 중 새로고침하면 취소 뒤 다시 2페이지를 읽을 수 있다`() {
        assertPaginationRecoversAfter(ExperienceListIntent.Refresh)
    }

    @Test
    fun `이어 읽기 대기 중 필터를 바꾸면 새 필터의 2페이지를 읽을 수 있다`() {
        assertPaginationRecoversAfter(
            ExperienceListIntent.Screen(ExperienceListEvent.FilterSelected(ExperienceTypeFilter(ExperienceType.Award))),
        )
    }

    private fun assertPaginationRecoversAfter(intent: ExperienceListIntent) {
        var moreCalls = 0
        var cancelled = false
        val queries = mutableListOf<Pair<ExperienceType?, String?>>()
        experienceRepository.onGetExperiences = { type, cursor, _ ->
            queries += type to cursor
            if (cursor != null && ++moreCalls == 1) {
                try {
                    awaitCancellation()
                } finally {
                    cancelled = true
                }
            }
            Result.success(
                if (cursor == null) {
                    CursorPage(items = List(20) { card(it + 1L) }, nextCursor = "c1")
                } else {
                    CursorPage(items = listOf(card(21L)), nextCursor = null)
                },
            )
        }
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.LoadMore)
        assertTrue(viewModel.state.value.isLoadingMore)

        viewModel.onIntent(intent)

        assertTrue(cancelled)
        assertFalse(viewModel.state.value.isLoadingMore)
        assertTrue(viewModel.state.value.canLoadMore)
        assertNull(viewModel.state.value.message)
        viewModel.onEvent(ExperienceListEvent.LoadMore)
        assertEquals(2, moreCalls)
        assertEquals(21, viewModel.state.value.cards.size)
        assertEquals(viewModel.state.value.filter.type to "c1", queries.last())
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

    // ── 등록·수정·삭제 (#179) ──────────────────────────────────────────────────

    @Test
    fun `카드를 누르면 그 값으로 시트를 연다`() {
        experienceRepository.experiences += card(7L)
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.CardClicked(7L))

        assertEquals(
            7L,
            viewModel.state.value.editor
                ?.experienceId,
        )
        assertEquals(
            "카드 7",
            viewModel.state.value.editor
                ?.title,
        )
    }

    @Test
    fun `필수 값이 비면 저장하지 않고 시트에 오류를 돌려준다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.AddClicked)

        viewModel.onEditor(ExperienceQuickAddEvent.Submitted)

        assertTrue(experienceRepository.createdDrafts.isEmpty())
        assertEquals(
            ProfileFieldViolation.Required,
            viewModel.state.value.editor
                ?.titleError,
        )
    }

    @Test
    fun `신규 카드를 등록하고 목록을 다시 읽는다`() {
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.AddClicked)
        viewModel.onEditor(ExperienceQuickAddEvent.TitleChanged("졸업 프로젝트"))
        viewModel.onEditor(ExperienceQuickAddEvent.StartDateChanged("2025.03"))

        viewModel.onEditor(ExperienceQuickAddEvent.Submitted)

        assertEquals("졸업 프로젝트", experienceRepository.createdDrafts.single().title)
        assertNull(viewModel.state.value.editor)
        assertEquals(ExperienceListMessage.Saved, viewModel.state.value.message)
        assertEquals(1, viewModel.state.value.cards.size)
    }

    /** 저장 실패는 시트를 닫지 않는다 — 친 값을 버리면 다시 쳐야 한다. */
    @Test
    fun `저장이 실패해도 시트는 열린 채 남는다`() {
        experienceRepository.onCreateExperience = { Result.failure(IOException("offline")) }
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.AddClicked)
        viewModel.onEditor(ExperienceQuickAddEvent.TitleChanged("졸업 프로젝트"))
        viewModel.onEditor(ExperienceQuickAddEvent.StartDateChanged("2025.03"))

        viewModel.onEditor(ExperienceQuickAddEvent.Submitted)

        assertEquals(
            "졸업 프로젝트",
            viewModel.state.value.editor
                ?.title,
        )
        assertEquals(ExperienceListMessage.SaveFailed, viewModel.state.value.message)
        assertEquals("experience_save", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `서버가 상한으로 거부하면 상한 문구로 알린다`() {
        experienceRepository.onCreateExperience = {
            Result.failure(CoreDataFailure.LimitExceeded(code = "LIMIT_EXCEEDED", cause = IllegalStateException()))
        }
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.AddClicked)
        viewModel.onEditor(ExperienceQuickAddEvent.TitleChanged("졸업 프로젝트"))
        viewModel.onEditor(ExperienceQuickAddEvent.StartDateChanged("2025.03"))

        viewModel.onEditor(ExperienceQuickAddEvent.Submitted)

        assertEquals(ExperienceListMessage.SaveLimitExceeded, viewModel.state.value.message)
    }

    @Test
    fun `삭제는 확인을 받고 지운 뒤 목록을 다시 읽는다`() {
        experienceRepository.experiences += card(3L)
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.DeleteClicked(3L))
        assertEquals(
            "카드 3",
            viewModel.state.value.pendingDeletion
                ?.title,
        )

        viewModel.onIntent(ExperienceListIntent.ConfirmDelete)

        assertTrue(experienceRepository.experiences.none { it.id == 3L })
        assertNull(viewModel.state.value.pendingDeletion)
        assertEquals(ExperienceListMessage.Deleted, viewModel.state.value.message)
    }

    @Test
    fun `삭제를 취소하면 아무것도 지우지 않는다`() {
        experienceRepository.experiences += card(3L)
        val viewModel = viewModel()

        viewModel.onEvent(ExperienceListEvent.DeleteClicked(3L))
        viewModel.onIntent(ExperienceListIntent.DismissDelete)

        assertEquals(1, experienceRepository.experiences.size)
        assertNull(viewModel.state.value.pendingDeletion)
    }

    @Test
    fun `삭제 중 닫기와 재확인은 기존 대상을 유지하고 요청을 겹치지 않는다`() {
        experienceRepository.experiences += listOf(card(3L), card(4L))
        val server = CompletableDeferred<Unit>()
        var calls = 0
        experienceRepository.onDeleteExperience = {
            calls++
            server.await()
            Result.failure(IOException("offline"))
        }
        val viewModel = viewModel()
        viewModel.onEvent(ExperienceListEvent.DeleteClicked(3L))
        viewModel.onIntent(ExperienceListIntent.ConfirmDelete)
        assertTrue(viewModel.state.value.isDeleting)

        viewModel.onIntent(ExperienceListIntent.DismissDelete)
        viewModel.onEvent(ExperienceListEvent.DeleteClicked(4L))
        viewModel.onIntent(ExperienceListIntent.ConfirmDelete)
        assertEquals(
            3L,
            viewModel.state.value.pendingDeletion
                ?.experienceId,
        )
        assertEquals(1, calls)

        server.complete(Unit)
        assertFalse(viewModel.state.value.isDeleting)
        assertNull(viewModel.state.value.pendingDeletion)
        assertEquals(ExperienceListMessage.DeleteFailed, viewModel.state.value.message)
        viewModel.onEvent(ExperienceListEvent.DeleteClicked(3L))
        viewModel.onIntent(ExperienceListIntent.ConfirmDelete)
        assertEquals(2, calls)
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

private fun ExperienceListViewModel.onEditor(event: ExperienceQuickAddEvent) = onIntent(ExperienceListIntent.Editor(event))

private val ExperienceListViewModel.state get() = uiState
