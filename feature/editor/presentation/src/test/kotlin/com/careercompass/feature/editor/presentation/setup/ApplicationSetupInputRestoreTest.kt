package com.careercompass.feature.editor.presentation.setup

import android.os.Parcel
import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.model.posting.PostingFormQuestion
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import com.careercompass.feature.editor.domain.usecase.CreateApplicationDraftUseCase
import com.careercompass.feature.editor.domain.usecase.GetApplicationSetupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** 회전으로 살아남은 ViewModel 이 아니라, Bundle/Parcel 을 건너 새로 만든 ViewModel 을 검증한다. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ApplicationSetupInputRestoreTest {
    private val dispatcher = StandardTestDispatcher()
    private val postings = FakePostingRepository()
    private val applications = FakeApplicationRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        postings.onGetPostingDetail = { Result.success(postingDetail()) }
        applications.onCreateDraft = { _, _, _ -> Result.success(createdDraft()) }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `손본 문항과 문체를 복원하고 새 인식 원본과 구분해서 생성 요청에 싣는다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.editFirst("직접 고친 질문", "700")
            before.screen(ApplicationSetupEvent.ToneSelected(ApplicationTone.Casual))
            val expected = before.uiState.value.items
            val fresh = listOf(PostingFormQuestion(1, "서버가 다시 인식한 질문", 300))
            postings.onGetPostingDetail = { Result.success(postingDetail(questions = fresh)) }

            val after = viewModel(handle.acrossProcessDeath())

            assertEquals(expected, after.uiState.value.items)
            assertEquals(ApplicationTone.Casual, after.uiState.value.tone)
            assertEquals(
                listOf("서버가 다시 인식한 질문"),
                after.uiState.value.recognizedItems
                    .map { it.question },
            )
            after.screen(ApplicationSetupEvent.StartClicked)
            assertEquals(expected, applications.createDraftCalls.single().items)
            assertEquals(ApplicationTone.Casual, applications.createDraftCalls.single().tone)
        }

    @Test
    fun `인식 결과가 없는 공고에 추가한 문항도 복원한다`() =
        runTest(dispatcher) {
            postings.onGetPostingDetail = { Result.success(postingDetail(questions = emptyList())) }
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.screen(ApplicationSetupEvent.ItemAddClicked)
            before.editor(ApplicationItemEditorEvent.QuestionChanged("첫 직접 입력"))
            before.editor(ApplicationItemEditorEvent.Submitted)

            val after = viewModel(handle.acrossProcessDeath())

            assertEquals(
                "첫 직접 입력",
                after.uiState.value.items
                    .single()
                    .question,
            )
            assertNull(
                after.uiState.value.items
                    .single()
                    .maxChars,
            )
            assertTrue(
                after.uiState.value.recognizedItems
                    .isEmpty(),
            )
            after.screen(ApplicationSetupEvent.StartClicked)
            assertEquals(after.uiState.value.items, applications.createDraftCalls.single().items)
        }

    @Test
    fun `인식 목록 그대로라면 아무것도 저장하지 않고 최신 서버 값을 쓴다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.editFirst("지원 동기를 작성해 주세요", "500")
            assertTrue(handle.keys().isEmpty())
            postings.onGetPostingDetail = { Result.success(postingDetail(questions = listOf(PostingFormQuestion(1, "새 질문", null)))) }

            val after = viewModel(handle.acrossProcessDeath())
            after.screen(ApplicationSetupEvent.StartClicked)

            assertEquals(
                "새 질문",
                after.uiState.value.items
                    .single()
                    .question,
            )
            assertNull(applications.createDraftCalls.single().items)
        }

    @Test
    fun `삭제와 문체 변경을 복원하지만 진행과 편집 시트와 단발 신호는 복원하지 않는다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.screen(ApplicationSetupEvent.ItemDeleteClicked(1))
            before.screen(ApplicationSetupEvent.ToneSelected(ApplicationTone.Casual))
            before.screen(ApplicationSetupEvent.ItemEditClicked(1))
            before.editor(ApplicationItemEditorEvent.QuestionChanged("아직 확정하지 않은 입력"))
            applications.onCreateDraft = { _, _, _ -> awaitCancellation() }
            before.screen(ApplicationSetupEvent.StartClicked)
            assertTrue(before.uiState.value.isCreating)

            val after = viewModel(handle.acrossProcessDeath()).uiState.value

            assertEquals(listOf(1), after.items.map { it.order })
            assertEquals("본인의 강점과 약점을 서술해 주세요", after.items.single().question)
            assertEquals(ApplicationTone.Casual, after.tone)
            assertFalse(after.isCreating)
            assertNull(after.itemEditor)
            assertNull(after.message)
            assertNull(after.draftStarted)
            assertNull(after.sessionEnd)
            assertEquals(1, applications.createDraftCalls.size)
        }

    @Test
    fun `생성 실패 뒤 입력은 복원하고 실패 안내는 되살리지 않는다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.editFirst("실패해도 지킬 질문", "")
            applications.onCreateDraft = { _, _, _ -> Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }
            before.screen(ApplicationSetupEvent.StartClicked)

            val after = viewModel(handle.acrossProcessDeath()).uiState.value

            assertEquals("실패해도 지킬 질문", after.items.first().question)
            assertNull(after.message)
            assertFalse(after.isCreating)
        }

    @Test
    fun `성공한 생성은 입력 초안을 지운다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.editFirst("전송한 질문", "")
            before.screen(ApplicationSetupEvent.ToneSelected(ApplicationTone.Casual))
            before.screen(ApplicationSetupEvent.StartClicked)

            assertTrue(handle.keys().isEmpty())
            val after = viewModel(handle.acrossProcessDeath()).uiState.value
            assertEquals(after.recognizedItems, after.items)
            assertEquals(ApplicationTone.Formal, after.tone)
            assertNull(after.draftStarted)
        }

    @Test
    fun `생성 또는 재조회 중 세션 만료는 저장 입력을 지운다`() =
        runTest(dispatcher) {
            for (duringCreate in listOf(true, false)) {
                postings.onGetPostingDetail = { Result.success(postingDetail()) }
                val handle = SavedStateHandle()
                val before = viewModel(handle)
                before.editFirst("이전 사용자의 질문", "500")
                val failure = CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException())
                if (duringCreate) {
                    applications.onCreateDraft = { _, _, _ -> Result.failure(failure) }
                    before.screen(ApplicationSetupEvent.StartClicked)
                } else {
                    postings.onGetPostingDetail = { Result.failure(failure) }
                    before.screen(ApplicationSetupEvent.RetryClicked)
                }
                assertTrue(handle.keys().isEmpty())
            }
        }

    @Test
    fun `서버 재조회 실패 뒤 다시 시도해도 복원할 입력을 잃지 않는다`() =
        runTest(dispatcher) {
            val handle = SavedStateHandle()
            viewModel(handle).editFirst("재조회 중에도 지킬 질문", "500")
            postings.onGetPostingDetail = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }
            val after = viewModel(handle.acrossProcessDeath())
            assertTrue(after.uiState.value.isFailureVisible)
            postings.onGetPostingDetail = { Result.success(postingDetail()) }

            after.screen(ApplicationSetupEvent.RetryClicked)

            assertEquals(
                "재조회 중에도 지킬 질문",
                after.uiState.value.items
                    .first()
                    .question,
            )
        }

    @Test
    fun `인식 문항이 10개를 넘어 줄이는 중이어도 편집한 목록 전체를 복원한다`() =
        runTest(dispatcher) {
            postings.onGetPostingDetail =
                { Result.success(postingDetail(questions = (1..12).map { PostingFormQuestion(it, "질문 $it", null) })) }
            val handle = SavedStateHandle()
            val before = viewModel(handle)
            before.editFirst("고친 질문", "200")
            before.screen(ApplicationSetupEvent.ItemDeleteClicked(12))

            val after = viewModel(handle.acrossProcessDeath()).uiState.value

            assertEquals(11, after.items.size)
            assertEquals(before.uiState.value.items, after.items)
        }

    private fun viewModel(handle: SavedStateHandle): ApplicationSetupViewModel {
        val viewModel =
            ApplicationSetupViewModel(
                postingId = TEST_POSTING_ID,
                getApplicationSetup = GetApplicationSetupUseCase(postings),
                createApplicationDraft = CreateApplicationDraftUseCase(applications),
                errorReporter =
                    object : ErrorReporter {
                        override fun writeFailure(
                            throwable: Throwable,
                            attributes: Map<String, String>,
                        ) = Unit
                    },
                savedStateHandle = handle,
            )
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun ApplicationSetupViewModel.screen(event: ApplicationSetupEvent) {
        onIntent(ApplicationSetupIntent.Screen(event))
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun ApplicationSetupViewModel.editor(event: ApplicationItemEditorEvent) {
        onIntent(ApplicationSetupIntent.ItemEditor(event))
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun ApplicationSetupViewModel.editFirst(
        question: String,
        limit: String,
    ) {
        screen(ApplicationSetupEvent.ItemEditClicked(1))
        editor(ApplicationItemEditorEvent.QuestionChanged(question))
        editor(ApplicationItemEditorEvent.MaxCharsChanged(limit))
        editor(ApplicationItemEditorEvent.Submitted)
    }

    private fun SavedStateHandle.acrossProcessDeath(): SavedStateHandle {
        val parcel = Parcel.obtain()
        return try {
            parcel.writeBundle(savedStateProvider().saveState())
            parcel.setDataPosition(0)
            SavedStateHandle.createHandle(parcel.readBundle(javaClass.classLoader), null)
        } finally {
            parcel.recycle()
        }
    }
}
