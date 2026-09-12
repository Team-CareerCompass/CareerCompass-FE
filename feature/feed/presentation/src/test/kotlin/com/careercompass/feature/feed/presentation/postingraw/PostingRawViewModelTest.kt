package com.careercompass.feature.feed.presentation.postingraw

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.feature.feed.domain.usecase.OpenPostingDetailUseCase
import com.careercompass.feature.feed.presentation.FIXED_CLOCK
import com.careercompass.feature.feed.presentation.MainDispatcherRule
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import com.careercompass.feature.feed.presentation.navigation.FeedRoute
import com.careercompass.feature.feed.presentation.postingDetail
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostingRawViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reporter = RecordingErrorReporter()

    private fun viewModel(repository: FakePostingRepository): PostingRawViewModel =
        PostingRawViewModel(
            route = FeedRoute.PostingRaw(POSTING_ID),
            openPostingDetail = OpenPostingDetailUseCase(repository),
            errorReporter = reporter,
            clock = FIXED_CLOCK,
        )

    @Test
    fun `원문을 받아 오고 원본 링크·뒤로가기는 단발 신호로 올라간다`() {
        val viewModel = viewModel(FakePostingRepository(details = listOf(postingDetail(id = POSTING_ID, isRead = true))))

        assertTrue(viewModel.state.value.loadState is PostingRawLoadState.Loaded)

        viewModel.onEvent(PostingRawEvent.OpenOriginalClicked)
        assertEquals("https://example.com/postings/$POSTING_ID", viewModel.state.value.openUrl)
        viewModel.onOpenUrlConsumed()
        assertNull(viewModel.state.value.openUrl)

        viewModel.onEvent(PostingRawEvent.BackClicked)
        assertTrue(viewModel.state.value.isBackRequested)
        viewModel.onBackConsumed()
        assertTrue(!viewModel.state.value.isBackRequested)
    }

    /**
     * 원본 주소는 사용자가 등록한 게시판에서 크롤러가 긁어 온 값이다. 그대로 `ACTION_VIEW` 로 넘기면 서버(또는 그
     * 게시판)가 고른 다른 앱의 딥링크가 열린다. 웹 주소가 아닌 것은 열지 않고, 계약 위반이니 결함으로 남긴다.
     */
    @Test
    fun `웹 주소가 아닌 원본 링크는 열지 않고 결함으로 남긴다`() {
        val viewModel =
            viewModel(
                FakePostingRepository(
                    details = listOf(postingDetail(id = POSTING_ID, url = "intent://scan/#Intent;scheme=zxing;end")),
                ),
            )

        viewModel.onEvent(PostingRawEvent.OpenOriginalClicked)

        assertNull(viewModel.state.value.openUrl)
        assertTrue(viewModel.state.value.openUrlRejected)
        assertEquals(listOf("posting_raw"), reporter.stages)

        viewModel.onOpenUrlRejectedConsumed()
        assertTrue(!viewModel.state.value.openUrlRejected)
    }

    @Test
    fun `실패는 네트워크 여부를 구분하고 다시 시도로 복구한다`() {
        val repository = FakePostingRepository(details = listOf(postingDetail(id = POSTING_ID)))
        repository.onGetPostingDetail = { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }
        val viewModel = viewModel(repository)

        assertEquals(PostingRawLoadState.Failed(FeedFailureReason.NetworkUnavailable), viewModel.state.value.loadState)
        // 화면이 사유를 그대로 안내하는 실패다 — 세션 표본 한 건만 남기고 재시도는 접는다.
        assertEquals(listOf("posting_raw"), reporter.stages)

        repository.onGetPostingDetail = null
        viewModel.retry()

        assertTrue(viewModel.state.value.loadState is PostingRawLoadState.Loaded)
    }

    /**
     * 503 이 일반 실패로 접히면, 점검 중에 공고 상세에서 「원문 보기」를 누른 사용자가 바로 앞 화면과 다른
     * 말을 듣는다(#212). 상세가 쓰는 것과 **같은 사유 값**까지 와야 같은 안내가 그려진다.
     */
    @Test
    fun `서버 점검은 일반 실패로 접히지 않고 점검 사유로 남는다`() {
        val repository =
            FakePostingRepository.strict().apply {
                onGetPostingDetail = { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException())) }
            }

        val loadState = viewModel(repository).state.value.loadState
        assertEquals(PostingRawLoadState.Failed(FeedFailureReason.Maintenance), loadState)
    }

    @Test
    fun `사유를 특정할 수 없는 실패만 일반 실패로 남는다`() {
        val repository =
            FakePostingRepository.strict().apply {
                onGetPostingDetail = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }
            }

        assertEquals(
            PostingRawLoadState.Failed(FeedFailureReason.Generic(FailureKind.Unexpected)),
            viewModel(repository).state.value.loadState,
        )
    }

    @Test
    fun `401 은 세션 종료 신호를 올린다`() {
        val repository =
            FakePostingRepository.strict().apply {
                onGetPostingDetail = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException())) }
            }

        assertTrue(viewModel(repository).state.value.sessionEnded)
    }

    private companion object {
        const val POSTING_ID = 7L
    }
}
