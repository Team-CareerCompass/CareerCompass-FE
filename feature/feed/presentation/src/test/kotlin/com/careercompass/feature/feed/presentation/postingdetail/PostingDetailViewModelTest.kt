package com.careercompass.feature.feed.presentation.postingdetail

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakePostingRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.posting.PostingDetail
import com.careercompass.core.model.posting.Suitability
import com.careercompass.core.model.posting.SuitabilityLabel
import com.careercompass.core.model.user.UserProfile
import com.careercompass.feature.feed.domain.usecase.OpenPostingDetailUseCase
import com.careercompass.feature.feed.domain.usecase.TogglePostingBookmarkUseCase
import com.careercompass.feature.feed.presentation.FIXED_CLOCK
import com.careercompass.feature.feed.presentation.MainDispatcherRule
import com.careercompass.feature.feed.presentation.RecordingErrorReporter
import com.careercompass.feature.feed.presentation.navigation.FeedRoute
import com.careercompass.feature.feed.presentation.postingDetail
import com.careercompass.feature.feed.presentation.profile
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.SuitabilityJudgement
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.net.UnknownHostException

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PostingDetailViewModelTest {
    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private val reporter = RecordingErrorReporter()

    private fun viewModel(
        repository: FakePostingRepository,
        profile: UserProfile? = profile(),
        postingId: Long = POSTING_ID,
    ): PostingDetailViewModel =
        PostingDetailViewModel(
            route = FeedRoute.PostingDetail(postingId),
            openPostingDetail = OpenPostingDetailUseCase(repository),
            togglePostingBookmark = TogglePostingBookmarkUseCase(repository),
            userProfileRepository = FakeUserProfileRepository(initialProfile = profile),
            errorReporter = reporter,
            clock = FIXED_CLOCK,
        )

    private fun repositoryWith(detail: PostingDetail) = FakePostingRepository(details = listOf(detail))

    @Test
    fun `상세를 열면 읽음 처리하고 읽은 상태로 보여 준다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID, isRead = false))

        val state = viewModel(repository).state.value

        assertEquals(listOf(POSTING_ID), repository.readCalls.toList())
        assertTrue(requireNotNull(state.detail).isRead)
    }

    @Test
    fun `직무·태그가 모두 비면 프로필 미입력이다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID))

        val state = viewModel(repository, profile = profile(jobInterests = emptyList(), tags = emptyList())).state.value

        assertEquals(SuitabilityJudgement.ProfileIncomplete, state.suitabilityJudgement)
    }

    @Test
    fun `프로필이 있어도 점수가 없으면 분석 중이다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID))

        assertEquals(SuitabilityJudgement.Analyzing, viewModel(repository).state.value.suitabilityJudgement)
        assertEquals(SuitabilityJudgement.Analyzing, viewModel(repository, profile = null).state.value.suitabilityJudgement)
    }

    @Test
    fun `점수가 있으면 프로필과 무관하게 준비됨이다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID, suitability = sampleSuitability()))

        val state = viewModel(repository, profile = profile(jobInterests = emptyList(), tags = emptyList())).state.value

        assertEquals(SuitabilityJudgement.Ready, state.suitabilityJudgement)
    }

    @Test
    fun `북마크는 먼저 뒤집고 실패하면 되돌린다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val repository =
                FakePostingRepository(
                    details = listOf(postingDetail(id = POSTING_ID, isBookmarked = false)),
                    onSetBookmarked = { _, _ ->
                        gate.await()
                        Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException()))
                    },
                )
            val viewModel = viewModel(repository)

            viewModel.onEvent(PostingDetailEvent.BookmarkToggled)
            assertTrue(requireNotNull(viewModel.state.value.detail).isBookmarked)

            gate.complete(Unit)

            assertFalse(requireNotNull(viewModel.state.value.detail).isBookmarked)
            assertEquals(PostingDetailMessage.BookmarkFailed, viewModel.state.value.message)
            assertEquals(listOf("bookmark"), reporter.stages)
        }

    @Test
    fun `북마크 성공은 서버 값으로 확정한다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID, isBookmarked = false))
        val viewModel = viewModel(repository)

        viewModel.onEvent(PostingDetailEvent.BookmarkToggled)

        assertTrue(requireNotNull(viewModel.state.value.detail).isBookmarked)
        assertEquals(listOf(POSTING_ID to true), repository.bookmarkCalls.toList())
    }

    @Test
    fun `공유·원문·유사 공고·프로필·뒤로가기는 단발 신호로 올라간다`() {
        val viewModel = viewModel(repositoryWith(postingDetail(id = POSTING_ID)))

        viewModel.onEvent(PostingDetailEvent.ShareClicked)
        assertEquals(
            PostingShareRequest(title = "공고 $POSTING_ID", url = "https://example.com/postings/$POSTING_ID"),
            viewModel.state.value.shareRequest,
        )
        viewModel.onShareConsumed()
        assertNull(viewModel.state.value.shareRequest)

        viewModel.onEvent(PostingDetailEvent.ViewOriginalClicked)
        assertEquals(PostingDetailDestination.Raw(POSTING_ID), viewModel.state.value.pendingNavigation)
        viewModel.onEvent(PostingDetailEvent.SimilarPostingSelected("9"))
        assertEquals(PostingDetailDestination.Posting(9L), viewModel.state.value.pendingNavigation)
        viewModel.onEvent(PostingDetailEvent.CompleteProfileClicked)
        assertEquals(PostingDetailDestination.Profile, viewModel.state.value.pendingNavigation)
        viewModel.onEvent(PostingDetailEvent.BackClicked)
        assertEquals(PostingDetailDestination.Back, viewModel.state.value.pendingNavigation)
        viewModel.onNavigationConsumed()
        assertNull(viewModel.state.value.pendingNavigation)
    }

    /** 초안 화면에서 뒤로 오면 이 상세가 그대로 남아 있어야 한다 — 상세를 걷어내는 이동이 아니다. */
    @Test
    fun `초안 작성은 문항 확인 화면으로 보낸다`() {
        val viewModel = viewModel(repositoryWith(postingDetail(id = POSTING_ID)))

        viewModel.onEvent(PostingDetailEvent.CreateDraftClicked)

        assertEquals(
            PostingDetailDestination.ApplicationSetup(POSTING_ID),
            viewModel.state.value.pendingNavigation,
        )
        assertNull(viewModel.state.value.message)
    }

    @Test
    fun `네트워크 단절과 서버 오류를 구분하고 다시 시도로 복구한다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID))
        repository.onGetPostingDetail = { Result.failure(CoreDataFailure.NetworkUnavailable(UnknownHostException())) }
        val viewModel = viewModel(repository)

        assertEquals(PostingDetailLoadState.Failed(FeedFailureReason.NetworkUnavailable), viewModel.state.value.loadState)

        repository.onGetPostingDetail = null
        viewModel.onEvent(PostingDetailEvent.RetryClicked)

        assertTrue(viewModel.state.value.loadState is PostingDetailLoadState.Loaded)
    }

    @Test
    fun `서버 점검 503 은 점검 상태가 되고 다시 시도로 복구한다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID))
        repository.onGetPostingDetail = { Result.failure(CoreDataFailure.ServiceUnavailable("LLM_UNAVAILABLE", RuntimeException())) }
        val viewModel = viewModel(repository)

        assertEquals(PostingDetailLoadState.Failed(FeedFailureReason.Maintenance), viewModel.state.value.loadState)

        repository.onGetPostingDetail = null
        viewModel.onEvent(PostingDetailEvent.RetryClicked)

        assertTrue(viewModel.state.value.loadState is PostingDetailLoadState.Loaded)
    }

    @Test
    fun `그 밖의 실패만 일반 실패로 접고 결함으로 기록한다`() {
        val repository = repositoryWith(postingDetail(id = POSTING_ID))
        repository.onGetPostingDetail = { Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException())) }

        assertEquals(PostingDetailLoadState.Failed(FeedFailureReason.Generic), viewModel(repository).state.value.loadState)
        assertEquals(listOf("posting_detail"), reporter.stages)
    }

    @Test
    fun `401 은 세션 종료 신호를 올린다`() {
        val repository =
            FakePostingRepository.strict().apply {
                onGetPostingDetail = { Result.failure(CoreDataFailure.Unauthorized("AUTH_REQUIRED", RuntimeException())) }
            }

        assertTrue(viewModel(repository).state.value.sessionEnded)
    }

    // ---- 적합도 자동 재조회 (#221) ----

    /** 재조회 횟수를 세는 저장소 — [detail] 을 바꾸면 다음 재조회부터 그 값이 나간다. */
    private class CountingRepository(
        var detail: PostingDetail,
        private val failAfterFirst: Throwable? = null,
    ) {
        var fetches = 0
        val repository =
            FakePostingRepository(
                onGetPostingDetail = {
                    fetches++
                    val failure = failAfterFirst
                    if (failure != null && fetches > 1) Result.failure(failure) else Result.success(detail)
                },
            )
    }

    @Test
    fun `분석 중이면 간격마다 조용히 다시 읽고 점수가 오면 멈춘다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true))
            val viewModel = viewModel(counting.repository)
            assertEquals(1, counting.fetches)
            assertEquals(SuitabilityJudgement.Analyzing, viewModel.state.value.suitabilityJudgement)

            advanceInterval()
            assertEquals(2, counting.fetches)
            // 화면은 흔들리지 않는다 — 읽은 상세가 그대로 `Loaded` 다.
            assertTrue(viewModel.state.value.loadState is PostingDetailLoadState.Loaded)
            assertFalse(viewModel.state.value.isSuitabilityRecheckExhausted)

            counting.detail = counting.detail.copy(suitability = sampleSuitability())
            advanceInterval()
            assertEquals(3, counting.fetches)
            assertEquals(SuitabilityJudgement.Ready, viewModel.state.value.suitabilityJudgement)

            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT * 3)
            assertEquals(3, counting.fetches)
            assertFalse(viewModel.state.value.isSuitabilityRecheckExhausted)
            // 이미 읽은 공고라 읽음 요청도 되풀이되지 않는다.
            assertTrue(counting.repository.readCalls.isEmpty())
        }

    @Test
    fun `자동 재조회를 다 쓰면 멈추고 다시 확인은 한 번만 더 묻는다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true))
            val viewModel = viewModel(counting.repository)

            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT)
            assertEquals(1 + SUITABILITY_AUTO_RECHECK_LIMIT, counting.fetches)
            assertTrue(viewModel.state.value.isSuitabilityRecheckExhausted)

            // 무한 폴링이 아니다 — 시간이 더 지나도 묻지 않는다.
            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT * 3)
            assertEquals(1 + SUITABILITY_AUTO_RECHECK_LIMIT, counting.fetches)

            viewModel.onEvent(PostingDetailEvent.SuitabilityRecheckClicked)
            assertEquals(2 + SUITABILITY_AUTO_RECHECK_LIMIT, counting.fetches)
            // 여전히 분석 중이면 다시 소진 상태다 — 버튼이 남아 있어야 한 번 더 누를 수 있다.
            assertTrue(viewModel.state.value.isSuitabilityRecheckExhausted)
            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT)
            assertEquals(2 + SUITABILITY_AUTO_RECHECK_LIMIT, counting.fetches)

            counting.detail = counting.detail.copy(suitability = sampleSuitability())
            viewModel.onEvent(PostingDetailEvent.SuitabilityRecheckClicked)
            assertEquals(SuitabilityJudgement.Ready, viewModel.state.value.suitabilityJudgement)
            assertFalse(viewModel.state.value.isSuitabilityRecheckExhausted)
        }

    @Test
    fun `프로필 미입력이면 재조회하지 않는다 — 기다릴 일이 아니라 할 일이 있다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true))
            val viewModel = viewModel(counting.repository, profile = profile(jobInterests = emptyList(), tags = emptyList()))
            assertEquals(SuitabilityJudgement.ProfileIncomplete, viewModel.state.value.suitabilityJudgement)

            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT * 2)

            assertEquals(1, counting.fetches)
            assertFalse(viewModel.state.value.isSuitabilityRecheckExhausted)
            viewModel.onEvent(PostingDetailEvent.SuitabilityRecheckClicked)
            assertEquals(1, counting.fetches)
        }

    /**
     * 프로필이 늦게 와서 「프로필 미입력」으로 갈리면 그 자리에서 멈춘다 — 판정이 폴링의 게이트다(#100 의 분리가
     * 여기서도 지켜진다). 프로필을 채우고 돌아와 다시 「분석 중」이 되면 그때 처음부터 다시 센다.
     */
    @Test
    fun `프로필 판정이 바뀌면 재조회가 멈추고 다시 분석 중이 되면 처음부터 센다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true))
            val profileRepository = FakeUserProfileRepository(initialProfile = null)
            val viewModel =
                PostingDetailViewModel(
                    route = FeedRoute.PostingDetail(POSTING_ID),
                    openPostingDetail = OpenPostingDetailUseCase(counting.repository),
                    togglePostingBookmark = TogglePostingBookmarkUseCase(counting.repository),
                    userProfileRepository = profileRepository,
                    errorReporter = reporter,
                    clock = FIXED_CLOCK,
                )
            assertEquals(SuitabilityJudgement.Analyzing, viewModel.state.value.suitabilityJudgement)
            advanceInterval()
            assertEquals(2, counting.fetches)

            profileRepository.profileState.value = profile(jobInterests = emptyList(), tags = emptyList())
            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT * 2)
            assertEquals(2, counting.fetches)
            assertFalse(viewModel.state.value.isSuitabilityRecheckExhausted)

            profileRepository.profileState.value = profile()
            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT)
            assertEquals(2 + SUITABILITY_AUTO_RECHECK_LIMIT, counting.fetches)
            assertTrue(viewModel.state.value.isSuitabilityRecheckExhausted)
        }

    @Test
    fun `재조회 실패는 화면을 흔들지 않고 별도 단계로 기록한다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting =
                CountingRepository(
                    postingDetail(id = POSTING_ID, isRead = true),
                    failAfterFirst = CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException()),
                )
            val viewModel = viewModel(counting.repository)

            advanceInterval()

            assertEquals(2, counting.fetches)
            assertTrue(viewModel.state.value.loadState is PostingDetailLoadState.Loaded)
            assertEquals(listOf("suitability_recheck"), reporter.stages)
        }

    @Test
    fun `화면 전체 다시 시도는 소진을 지우고 자동 재조회를 처음부터 센다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true))
            val viewModel = viewModel(counting.repository)
            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT)
            assertTrue(viewModel.state.value.isSuitabilityRecheckExhausted)

            viewModel.onEvent(PostingDetailEvent.RetryClicked)

            assertFalse(viewModel.state.value.isSuitabilityRecheckExhausted)
            assertEquals(2 + SUITABILITY_AUTO_RECHECK_LIMIT, counting.fetches)
            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT)
            assertEquals(2 + SUITABILITY_AUTO_RECHECK_LIMIT * 2, counting.fetches)
            assertTrue(viewModel.state.value.isSuitabilityRecheckExhausted)
        }

    /** 소진 여부는 화면 계약의 「분석 중」에 실려 카드가 두 모양을 그린다. */
    @Test
    fun `소진 여부는 화면 계약의 분석 중 상태에 실린다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true))
            val viewModel = viewModel(counting.repository)
            val resources = RuntimeEnvironment.getApplication().resources

            assertEquals(
                PostingSuitabilityState.Analyzing(isAutoRecheckExhausted = false),
                viewModel.state.value
                    .toUiState(resources, FIXED_CLOCK)
                    .loadedSuitability(),
            )

            advanceInterval(times = SUITABILITY_AUTO_RECHECK_LIMIT)

            assertEquals(
                PostingSuitabilityState.Analyzing(isAutoRecheckExhausted = true),
                viewModel.state.value
                    .toUiState(resources, FIXED_CLOCK)
                    .loadedSuitability(),
            )
        }

    // ---- 북마크 응답과 재조회의 경합 (#235) ----

    /** 북마크 응답이 오가는 사이 재조회가 적합도를 실어 와도, 응답이 그것을 옛 스냅샷으로 덮지 않는다. */
    @Test
    fun `북마크 응답이 늦어도 그 사이 재조회로 도착한 적합도를 지우지 않는다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true, isBookmarked = false))
            counting.repository.onSetBookmarked = { _, _ ->
                gate.await()
                Result.success(Unit)
            }
            val viewModel = viewModel(counting.repository)

            viewModel.onEvent(PostingDetailEvent.BookmarkToggled)
            assertTrue(requireNotNull(viewModel.state.value.detail).isBookmarked)

            counting.detail = counting.detail.copy(suitability = sampleSuitability())
            advanceInterval()
            assertEquals(SuitabilityJudgement.Ready, viewModel.state.value.suitabilityJudgement)

            gate.complete(Unit)

            val detail = requireNotNull(viewModel.state.value.detail)
            assertTrue(detail.isBookmarked)
            assertEquals(SuitabilityJudgement.Ready, viewModel.state.value.suitabilityJudgement)
        }

    @Test
    fun `북마크 실패의 되돌리기도 그 사이 도착한 적합도를 지우지 않는다`() =
        runTest(mainDispatcherRule.dispatcher) {
            val gate = CompletableDeferred<Unit>()
            val counting = CountingRepository(postingDetail(id = POSTING_ID, isRead = true, isBookmarked = false))
            counting.repository.onSetBookmarked = { _, _ ->
                gate.await()
                Result.failure(CoreDataFailure.ServerError("INTERNAL_ERROR", RuntimeException()))
            }
            val viewModel = viewModel(counting.repository)

            viewModel.onEvent(PostingDetailEvent.BookmarkToggled)
            counting.detail = counting.detail.copy(suitability = sampleSuitability())
            advanceInterval()

            gate.complete(Unit)

            val detail = requireNotNull(viewModel.state.value.detail)
            assertFalse(detail.isBookmarked)
            assertEquals(SuitabilityJudgement.Ready, viewModel.state.value.suitabilityJudgement)
            assertEquals(PostingDetailMessage.BookmarkFailed, viewModel.state.value.message)
        }

    private fun PostingDetailUiState.loadedSuitability(): PostingSuitabilityState =
        (content as PostingDetailContentState.Loaded).posting.suitability

    private fun kotlinx.coroutines.test.TestScope.advanceInterval(times: Int = 1) {
        repeat(times) {
            advanceTimeBy(SUITABILITY_AUTO_RECHECK_INTERVAL.inWholeMilliseconds)
            runCurrent()
        }
    }

    private fun sampleSuitability(): Suitability =
        Suitability(
            score = 88,
            label = SuitabilityLabel.VerySuitable,
            breakdown = emptyList(),
            strengthComment = null,
            weaknessComment = null,
        )

    private companion object {
        const val POSTING_ID = 7L
    }
}
