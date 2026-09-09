package com.careercompass.feature.editor.presentation.setup

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.editor.domain.model.ApplicationTone
import com.careercompass.feature.editor.domain.testing.FakeApplicationRepository
import com.careercompass.feature.editor.domain.usecase.CreateApplicationDraftUseCase
import com.careercompass.feature.editor.domain.usecase.GetApplicationSetupUseCase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.net.UnknownHostException

class ApplicationSetupViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val postingRepository = FakePostingRepository()
    private val applicationRepository = FakeApplicationRepository()
    private val recordedFailures = mutableListOf<Throwable>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `인식된 문항을 순서대로 그리고 공고 제목을 보인다`() =
        runTest(dispatcher) {
            postingRepository.onGetPostingDetail = { Result.success(postingDetail()) }

            val state = viewModel().uiState.value

            assertEquals("카카오 SW 인턴십", state.postingTitle)
            assertEquals(listOf(1, 2), state.items.map { it.order })
            assertEquals(listOf(500, null), state.items.map { it.maxChars })
            assertFalse(state.isEmptyRecognition)
        }

    /** 「항목을 못 찾았습니다」로 끝내면 그 공고는 영영 초안을 못 만든다 — 직접 쓰는 자리를 연다(F4-1). */
    @Test
    fun `인식된 문항이 없어도 실패가 아니라 직접 쓰는 자리다`() =
        runTest(dispatcher) {
            postingRepository.onGetPostingDetail = { Result.success(postingDetail(questions = emptyList())) }

            val state = viewModel().uiState.value

            assertTrue(state.isEmptyRecognition)
            assertNull(state.loadFailure)
            assertFalse(state.canStart)
        }

    /** 파싱이 아직 안 끝난 공고도 같은 길이다 — 기다리라고 막으면 사용자가 할 수 있는 일이 없다. */
    @Test
    fun `파싱 결과가 없는 공고도 직접 쓰는 자리로 연다`() =
        runTest(dispatcher) {
            postingRepository.onGetPostingDetail = { Result.success(postingDetail(parsed = false)) }

            val state = viewModel().uiState.value

            assertTrue(state.isEmptyRecognition)
            assertEquals(emptyList<Int>(), state.items.map { it.order })
        }

    @Test
    fun `공고를 못 읽으면 화면을 덮고 다시 시도로 복구한다`() =
        runTest(dispatcher) {
            postingRepository.onGetPostingDetail = {
                Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException()))
            }
            val viewModel = viewModel()

            assertEquals(FailureKind.NoConnection, viewModel.uiState.value.loadFailure)
            assertTrue(viewModel.uiState.value.isFailureVisible)

            postingRepository.onGetPostingDetail = { Result.success(postingDetail()) }
            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.RetryClicked))

            assertFalse(viewModel.uiState.value.isFailureVisible)
            assertEquals(2, viewModel.uiState.value.items.size)
        }

    @Test
    fun `문항을 추가하면 번호가 이어 붙는다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemAddClicked))
            viewModel.editor(ApplicationItemEditorEvent.QuestionChanged("직접 쓴 문항"))
            viewModel.editor(ApplicationItemEditorEvent.MaxCharsChanged("300"))
            viewModel.editor(ApplicationItemEditorEvent.Submitted)

            val state = viewModel.uiState.value
            assertEquals(listOf(1, 2, 3), state.items.map { it.order })
            assertEquals("직접 쓴 문항", state.items.last().question)
            assertEquals(300, state.items.last().maxChars)
            assertNull(state.itemEditor)
        }

    @Test
    fun `빈 질문은 시트를 닫지 않고 오류를 보인다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemAddClicked))
            viewModel.editor(ApplicationItemEditorEvent.Submitted)

            val editor = requireNotNull(viewModel.uiState.value.itemEditor)
            assertTrue(editor.hasErrors)
            assertEquals(2, viewModel.uiState.value.items.size)
        }

    /** 공고가 글자 수를 안 적는 일은 흔하다 — 빈 값을 막으면 그런 문항을 넣을 수 없다. */
    @Test
    fun `글자 수 제한을 비우면 제한 없음으로 들어간다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemAddClicked))
            viewModel.editor(ApplicationItemEditorEvent.QuestionChanged("제한 없는 문항"))
            viewModel.editor(ApplicationItemEditorEvent.Submitted)

            assertNull(
                viewModel.uiState.value.items
                    .last()
                    .maxChars,
            )
            assertTrue(viewModel.uiState.value.hasUnboundedItem)
        }

    @Test
    fun `삭제하면 번호를 다시 매긴다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemDeleteClicked(1)))

            val state = viewModel.uiState.value
            assertEquals(listOf(1), state.items.map { it.order })
            assertEquals("본인의 강점과 약점을 서술해 주세요", state.items.single().question)
        }

    /** 문항이 0개면 초안을 만들 수 없어 화면이 막다른 길이 된다. */
    @Test
    fun `마지막 문항은 지우지 않고 알린다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemDeleteClicked(1)))

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemDeleteClicked(1)))

            assertEquals(1, viewModel.uiState.value.items.size)
            assertEquals(ApplicationSetupMessage.LastItemKept, viewModel.uiState.value.message)
        }

    @Test
    fun `문항 상한에 닿으면 추가 시트를 열지 않는다`() =
        runTest(dispatcher) {
            val questions =
                (1..10).map {
                    com.careercompass.core.model.posting
                        .PostingFormQuestion(order = it, question = "문항 $it", maxChars = 300)
                }
            postingRepository.onGetPostingDetail = { Result.success(postingDetail(questions = questions)) }
            val viewModel = viewModel()

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemAddClicked))

            assertNull(viewModel.uiState.value.itemEditor)
            assertEquals(ApplicationSetupMessage.LimitReached, viewModel.uiState.value.message)
        }

    /** 계약에 없는 필드라, 보낼 이유가 없을 때 보내면 그 필드를 모르는 서버가 거절할 여지만 만든다. */
    @Test
    fun `손보지 않고 시작하면 문항을 보내지 않는다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            applicationRepository.onCreateDraft = { _, _, _ -> Result.success(createdDraft()) }

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.StartClicked))

            val call = applicationRepository.createDraftCalls.single()
            assertNull(call.items)
            assertEquals(ApplicationTone.Formal, call.tone)
        }

    @Test
    fun `손본 문항과 고른 문체를 실어 보내고 진행 화면으로 넘긴다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            applicationRepository.onCreateDraft = { _, _, _ -> Result.success(createdDraft(id = 77L)) }

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ToneSelected(ApplicationTone.Casual)))
            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.ItemDeleteClicked(2)))
            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.StartClicked))

            val call = applicationRepository.createDraftCalls.single()
            assertEquals(ApplicationTone.Casual, call.tone)
            assertEquals(listOf("지원 동기를 작성해 주세요"), call.items?.map { it.question })
            assertEquals(
                ApplicationDraftStarted(applicationId = 77L, postingId = TEST_POSTING_ID),
                viewModel.uiState.value.draftStarted,
            )
        }

    /** 생성이 실패해도 쓰던 문항은 화면에 그대로 남는다 — 다시 만들게 하면 사용자가 처음부터 쓴다. */
    @Test
    fun `생성 실패는 문항을 지우지 않고 알린다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            applicationRepository.onCreateDraft = { _, _, _ ->
                Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException()))
            }

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.StartClicked))

            assertEquals(ApplicationSetupMessage.CreateFailed, viewModel.uiState.value.message)
            assertEquals(2, viewModel.uiState.value.items.size)
            assertFalse(viewModel.uiState.value.isCreating)
            assertNull(viewModel.uiState.value.draftStarted)
            // 503 `LLM_UNAVAILABLE` 은 서버가 스스로 알린 계획된 상태라 리포팅하지 않는다
            // (`recordStagedFailure` 의 「알려진 결말」). 우리가 고칠 것이 아니다.
            assertTrue(recordedFailures.isEmpty())
        }

    /** 알려진 결말이 아닌 실패는 남긴다 — 그것이 우리가 고칠 것이다. */
    @Test
    fun `서버 오류는 리포팅에 남긴다`() =
        runTest(dispatcher) {
            val viewModel = loadedViewModel()
            applicationRepository.onCreateDraft = { _, _, _ ->
                Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException()))
            }

            viewModel.send(ApplicationSetupIntent.Screen(ApplicationSetupEvent.StartClicked))

            assertEquals(ApplicationSetupMessage.CreateFailed, viewModel.uiState.value.message)
            assertEquals(1, recordedFailures.size)
        }

    @Test
    fun `401 은 셸에 세션 종료로 넘긴다`() =
        runTest(dispatcher) {
            postingRepository.onGetPostingDetail = {
                Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException()))
            }

            val state = viewModel().uiState.value

            assertNotNull(state.sessionEnd)
            assertNull(state.loadFailure)
        }

    /** 비동기 갈래가 있는 intent 는 보낸 뒤 스케줄러를 비워야 결과가 상태에 닿는다. */
    private fun ApplicationSetupViewModel.send(intent: ApplicationSetupIntent) {
        onIntent(intent)
        dispatcher.scheduler.advanceUntilIdle()
    }

    private fun ApplicationSetupViewModel.editor(event: ApplicationItemEditorEvent) {
        send(ApplicationSetupIntent.ItemEditor(event))
    }

    private fun viewModel(): ApplicationSetupViewModel {
        val viewModel =
            ApplicationSetupViewModel(
                postingId = TEST_POSTING_ID,
                getApplicationSetup = GetApplicationSetupUseCase(postingRepository),
                createApplicationDraft = CreateApplicationDraftUseCase(applicationRepository),
                errorReporter = recordingReporter(),
            )
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }

    private fun loadedViewModel(): ApplicationSetupViewModel {
        postingRepository.onGetPostingDetail = { Result.success(postingDetail()) }
        return viewModel()
    }

    private fun recordingReporter(): ErrorReporter =
        object : ErrorReporter {
            override fun writeFailure(
                throwable: Throwable,
                attributes: Map<String, String>,
            ) {
                recordedFailures += throwable
            }
        }
}
