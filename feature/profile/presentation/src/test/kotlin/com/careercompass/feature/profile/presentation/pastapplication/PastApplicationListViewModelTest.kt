package com.careercompass.feature.profile.presentation.pastapplication

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.profile.domain.usecase.DeletePastApplicationUseCase
import com.careercompass.feature.profile.domain.usecase.GetPastApplicationsUseCase
import com.careercompass.feature.profile.domain.usecase.UpdatePastApplicationItemCategoryUseCase
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
class PastApplicationListViewModelTest {
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
    private val repository = FakePastApplicationRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() =
        PastApplicationListViewModel(
            getPastApplications = GetPastApplicationsUseCase(repository),
            updateItemCategory = UpdatePastApplicationItemCategoryUseCase(repository),
            deletePastApplication = DeletePastApplicationUseCase(repository),
            errorReporter = reporter,
        )

    @Test
    fun `목록과 개수를 받는다`() {
        repository.applications += application(1L)

        val state = viewModel().state.value

        assertEquals(1, state.applications.size)
        assertEquals(1, state.count)
        assertFalse(state.isLimitReached)
        assertFalse(state.isEmpty)
    }

    @Test
    fun `상한에 닿으면 추가를 막고 이유를 말한다`() {
        repository.applications += List(10) { application(it + 1L) }
        val viewModel = viewModel()
        assertTrue(viewModel.state.value.isLimitReached)

        viewModel.onEvent(PastApplicationListEvent.AddClicked)

        assertEquals(PastApplicationListMessage.LimitReached, viewModel.state.value.message)
        assertFalse(viewModel.state.value.isAddRequested)
    }

    /** 한 번에 하나만 펼친다 — 같은 것을 다시 누르면 접힌다. */
    @Test
    fun `펼침은 하나만 유지한다`() {
        repository.applications += application(1L)
        repository.applications += application(2L)
        val viewModel = viewModel()

        viewModel.onEvent(PastApplicationListEvent.ApplicationToggled(1L))
        assertEquals(1L, viewModel.state.value.expandedId)

        viewModel.onEvent(PastApplicationListEvent.ApplicationToggled(2L))
        assertEquals(2L, viewModel.state.value.expandedId)

        viewModel.onEvent(PastApplicationListEvent.ApplicationToggled(2L))
        assertNull(viewModel.state.value.expandedId)
    }

    @Test
    fun `항목을 누르면 그 항목의 분류로 시트를 연다`() {
        repository.applications += application(1L)
        val viewModel = viewModel()

        viewModel.onEvent(PastApplicationListEvent.ItemCategoryClicked(1L, 11L))

        val editor = viewModel.state.value.categoryEditor
        assertEquals("1", editor?.documentId)
        assertEquals(11L, editor?.itemId)
        assertEquals(PastApplicationCategory.Other, editor?.selected)
    }

    /** 서버가 고친 항목을 돌려주므로 목록을 다시 읽지 않는다 — 펼침과 스크롤이 흔들리지 않게. */
    @Test
    fun `분류를 바꾸면 그 항목만 갈아 낀다`() {
        repository.applications += application(1L)
        val viewModel = viewModel()
        viewModel.onEvent(PastApplicationListEvent.ItemCategoryClicked(1L, 11L))

        viewModel.onIntent(PastApplicationListIntent.CategorySelected(PastApplicationCategory.Motivation))

        val item =
            viewModel.state.value.applications
                .single()
                .items
                .single { it.id == 11L }
        assertEquals(PastApplicationCategory.Motivation, item.category)
        assertTrue(item.confident)
        assertNull(viewModel.state.value.categoryEditor)
    }

    @Test
    fun `같은 분류를 다시 고르면 왕복하지 않고 시트만 닫는다`() {
        repository.applications += application(1L)
        var calls = 0
        repository.onUpdateItemCategory = { _, _, _ ->
            calls++
            Result.failure(IllegalStateException("불려서는 안 된다"))
        }
        val viewModel = viewModel()
        viewModel.onEvent(PastApplicationListEvent.ItemCategoryClicked(1L, 11L))

        viewModel.onIntent(PastApplicationListIntent.CategorySelected(PastApplicationCategory.Other))

        assertEquals(0, calls)
        assertNull(viewModel.state.value.categoryEditor)
    }

    @Test
    fun `분류 변경이 실패하면 목록을 그대로 두고 알린다`() {
        repository.applications += application(1L)
        repository.onUpdateItemCategory = { _, _, _ -> Result.failure(IOException("offline")) }
        val viewModel = viewModel()
        viewModel.onEvent(PastApplicationListEvent.ItemCategoryClicked(1L, 11L))

        viewModel.onIntent(PastApplicationListIntent.CategorySelected(PastApplicationCategory.Growth))

        assertEquals(
            PastApplicationCategory.Other,
            viewModel.state.value.applications
                .single()
                .items
                .single { it.id == 11L }
                .category,
        )
        assertEquals(PastApplicationListMessage.CategoryUpdateFailed, viewModel.state.value.message)
        assertEquals("past_application_category", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `삭제는 확인을 받고 지운 뒤 목록을 다시 읽는다`() {
        repository.applications += application(3L)
        val viewModel = viewModel()

        viewModel.onEvent(PastApplicationListEvent.DeleteClicked(3L))
        assertEquals(
            "지원서 3",
            viewModel.state.value.pendingDeletion
                ?.label,
        )

        viewModel.onIntent(PastApplicationListIntent.ConfirmDelete)

        assertTrue(repository.applications.isEmpty())
        assertNull(viewModel.state.value.pendingDeletion)
        assertEquals(PastApplicationListMessage.Deleted, viewModel.state.value.message)
        assertTrue(viewModel.state.value.isEmpty)
    }

    @Test
    fun `삭제를 취소하면 아무것도 지우지 않는다`() {
        repository.applications += application(3L)
        val viewModel = viewModel()

        viewModel.onEvent(PastApplicationListEvent.DeleteClicked(3L))
        viewModel.onIntent(PastApplicationListIntent.DismissDelete)

        assertEquals(1, repository.applications.size)
        assertNull(viewModel.state.value.pendingDeletion)
    }

    @Test
    fun `읽은 지원서가 없는 실패만 화면을 덮는다`() {
        repository.onGetPastApplications = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) }

        val state = viewModel().state.value

        assertTrue(state.isFailureVisible)
        assertEquals(FailureKind.NoConnection, state.loadFailure)
        assertEquals("past_application_list", reporter.recorded.single()["profile_stage"])
    }

    @Test
    fun `401 은 세션 종료로 올린다`() {
        repository.onGetPastApplications = {
            Result.failure(CoreDataFailure.Unauthorized(code = "AUTH_INVALID", cause = IllegalStateException()))
        }

        assertEquals(ProfileSessionEnd.Expired, viewModel().state.value.sessionEnd)
    }

    private companion object {
        fun application(id: Long) =
            PastApplication(
                id = id,
                label = "지원서 $id",
                items =
                    listOf(
                        PastApplicationItem(id = 11L, category = PastApplicationCategory.Other, content = "분류가 애매한 문단", confident = false),
                        PastApplicationItem(
                            id = 12L,
                            category = PastApplicationCategory.Motivation,
                            content = "지원 동기 문단",
                            confident = true,
                        ),
                    ),
                createdAt = null,
            )
    }
}

private fun PastApplicationListViewModel.onEvent(event: PastApplicationListEvent) = onIntent(PastApplicationListIntent.Screen(event))

private val PastApplicationListViewModel.state get() = uiState
