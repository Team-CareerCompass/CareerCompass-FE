package com.careercompass.feature.feed.presentation.postingdetail

import android.content.Intent
import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.posting.Suitability
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.description
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.title
import com.careercompass.feature.feed.presentation.R
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.SuitabilityJudgement
import com.careercompass.feature.feed.presentation.shared.model.failureKind
import com.careercompass.feature.feed.presentation.shared.model.judgeSuitability
import com.careercompass.feature.feed.presentation.shared.util.toDetailUiModel
import com.careercompass.feature.feed.presentation.shared.util.toSuitabilityUiModel
import kotlinx.coroutines.launch
import java.time.Clock

/**
 * 공고 상세 진입점 — 도메인 상세를 [PostingDetailContent] 계약으로 옮기고 공유·이동·스낵바 신호를 소비한다.
 *
 * 유사 공고 선택은 [onPostingClick] 으로 상세를 하나 더 쌓고, 프로필 입력 안내는 [onProfileClick] 으로 앱 셸에 맡긴다.
 * 서버 점검은 한 줄 오류 문구가 아니라 [PostingDetailContentState.Maintenance] 로 옮겨 전용 안내 화면이 그려진다.
 */
@Composable
public fun PostingDetailScreen(
    onBackClick: () -> Unit,
    onPostingClick: (Long) -> Unit,
    onRawClick: (Long) -> Unit,
    onProfileClick: () -> Unit,
    onCreateDraftClick: (Long) -> Unit,
    onSessionEnded: () -> Unit,
    viewModel: PostingDetailViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    val pendingNavigation = state.pendingNavigation
    LaunchedEffect(pendingNavigation) {
        if (pendingNavigation == null) return@LaunchedEffect
        viewModel.onIntent(PostingDetailIntent.ConsumeNavigation)
        when (pendingNavigation) {
            PostingDetailDestination.Back -> onBackClick()
            is PostingDetailDestination.Raw -> onRawClick(pendingNavigation.postingId)
            is PostingDetailDestination.Posting -> onPostingClick(pendingNavigation.postingId)
            PostingDetailDestination.Profile -> onProfileClick()
            is PostingDetailDestination.ApplicationSetup -> onCreateDraftClick(pendingNavigation.postingId)
        }
    }
    val sessionEnded = state.sessionEnded
    LaunchedEffect(sessionEnded) {
        if (sessionEnded) {
            viewModel.onIntent(PostingDetailIntent.ConsumeSessionEnded)
            onSessionEnded()
        }
    }
    val shareRequest = state.shareRequest
    LaunchedEffect(shareRequest) {
        if (shareRequest == null) return@LaunchedEffect
        viewModel.onIntent(PostingDetailIntent.ConsumeShare)
        val sendIntent =
            Intent(Intent.ACTION_SEND).apply {
                type = SHARE_MIME_TYPE
                putExtra(Intent.EXTRA_SUBJECT, shareRequest.title)
                putExtra(
                    Intent.EXTRA_TEXT,
                    resources.getString(R.string.feed_posting_detail_share_text, shareRequest.title, shareRequest.url),
                )
            }
        context.startActivity(Intent.createChooser(sendIntent, resources.getString(R.string.feed_posting_detail_share_chooser)))
    }
    val message = state.message
    LaunchedEffect(message) {
        if (message == null) return@LaunchedEffect
        viewModel.onIntent(PostingDetailIntent.ConsumeMessage)
        val messageRes =
            when (message) {
                PostingDetailMessage.BookmarkFailed -> R.string.feed_bookmark_failed
            }
        snackbarScope.launch { snackbarHostState.showSnackbar(resources.getString(messageRes)) }
    }

    val uiState =
        remember(state.loadState, state.profile, state.isSuitabilityRecheckExhausted, resources) {
            state.toUiState(resources, viewModel.clock)
        }

    Box(modifier = modifier.fillMaxSize()) {
        PostingDetailContent(
            state = uiState,
            onEvent = { viewModel.onIntent(PostingDetailIntent.Screen(it)) },
        )
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

internal fun PostingDetailViewState.toUiState(
    resources: Resources,
    clock: Clock,
): PostingDetailUiState =
    PostingDetailUiState(
        content =
            when (val loadState = loadState) {
                PostingDetailLoadState.Loading -> {
                    PostingDetailContentState.Loading
                }

                is PostingDetailLoadState.Failed -> {
                    when (loadState.reason) {
                        FeedFailureReason.Maintenance -> {
                            PostingDetailContentState.Maintenance
                        }

                        FeedFailureReason.NetworkUnavailable -> {
                            PostingDetailContentState.NetworkUnavailable
                        }

                        // 문구는 실패 표에서 읽는다(#204). 제목·본문을 따로 들어 실패 부품이 두 줄로 그리고,
                        // 재시도 유무도 표의 판정을 그대로 싣는다(#222).
                        is FeedFailureReason.Generic -> {
                            val display = loadState.reason.failureKind.display(FailureSurface.Posting)
                            PostingDetailContentState.Error(
                                title = display.title(resources),
                                description = display.description(resources),
                                isRetryable = display.isRetryable,
                            )
                        }
                    }
                }

                is PostingDetailLoadState.Loaded -> {
                    PostingDetailContentState.Loaded(
                        loadState.detail.toDetailUiModel(
                            resources = resources,
                            clock = clock,
                            suitability =
                                toSuitabilityState(
                                    judgement = judgeSuitability(hasScore = loadState.detail.suitability != null, profile = profile),
                                    suitability = loadState.detail.suitability,
                                    isAutoRecheckExhausted = isSuitabilityRecheckExhausted,
                                    resources = resources,
                                ),
                            profile = profile,
                        ),
                    )
                }
            },
    )

/**
 * 판정 → 카드 상태. 「준비됨」인데 점수가 없는 모순은 「분석 중」으로 접는다.
 *
 * 판정은 새로 하지 않는다 — [judgeSuitability] 하나를 목록 카드와 나눠 쓴다(#100). 여기서 더하는 것은
 * 「분석 중」이 기다리는 중인지, 기다리기를 그만뒀는지([isAutoRecheckExhausted])뿐이다(#221).
 */
internal fun toSuitabilityState(
    judgement: SuitabilityJudgement,
    suitability: Suitability?,
    isAutoRecheckExhausted: Boolean,
    resources: Resources,
): PostingSuitabilityState =
    when (judgement) {
        SuitabilityJudgement.ProfileIncomplete -> {
            PostingSuitabilityState.ProfileIncomplete
        }

        SuitabilityJudgement.Analyzing -> {
            PostingSuitabilityState.Analyzing(isAutoRecheckExhausted = isAutoRecheckExhausted)
        }

        SuitabilityJudgement.Ready -> {
            suitability?.let { PostingSuitabilityState.Ready(it.toSuitabilityUiModel(resources)) }
                ?: PostingSuitabilityState.Analyzing(isAutoRecheckExhausted = isAutoRecheckExhausted)
        }
    }

private const val SHARE_MIME_TYPE = "text/plain"
