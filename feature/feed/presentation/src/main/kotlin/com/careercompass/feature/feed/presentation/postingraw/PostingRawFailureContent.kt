package com.careercompass.feature.feed.presentation.postingraw

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassNetworkErrorState
import com.careercompass.feature.feed.presentation.R
import com.careercompass.feature.feed.presentation.shared.component.FeedMaintenanceState
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason

/**
 * 원문을 못 받았을 때 사유별로 갈리는 화면 — [PostingRawScreen] 가 상태 없이 그리는 부분만 떼어 냈다
 * (게시판 목록의 `BoardListFailureContent` 와 같은 모양이다).
 *
 * 원문은 스냅샷을 저장하지 않으므로 「오프라인 모드로 보기」는 어느 사유에서도 열지 않는다.
 *
 * **행동 버튼이 사유마다 갈린다.**
 * - [FeedFailureReason.NetworkUnavailable] — 「다시 시도」. 사용자가 연결을 살린 뒤 누르면 답이 달라진다.
 * - [FeedFailureReason.Maintenance] — 「새로고침」(부품이 고정한 문구). 서버가 돌아와야 답이 달라지는
 *   실패라 「다시 시도」로 재시도를 권하지 않는다. 바로 앞 화면(공고 상세)과 같은 부품·같은 문구다.
 * - [FeedFailureReason.Generic] — 「다시 시도」. 원인을 특정하지 못했으니 한 번 더 해 보는 것이 최선이다.
 *
 * 어느 사유든 상단 바는 [PostingRawScreen] 가 남기므로 뒤로가기는 살아 있다.
 */
@Composable
internal fun PostingRawFailureContent(
    reason: FeedFailureReason,
    onRetryClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (reason) {
        FeedFailureReason.NetworkUnavailable -> {
            CareerCompassNetworkErrorState(
                onRetryClick = onRetryClick,
                onOfflineClick = null,
                modifier = modifier,
            )
        }

        FeedFailureReason.Maintenance -> {
            FeedMaintenanceState(
                onRetryClick = onRetryClick,
                onOfflineClick = null,
                modifier = modifier,
            )
        }

        is FeedFailureReason.Generic -> {
            // 「원문」이라는 명사는 실패 표에 문맥이 없어 이 화면의 문구를 쓴다. 부품은 실패 전용이다(#222).
            CareerCompassFailureState(
                title = stringResource(R.string.feed_posting_raw_error_title),
                description = stringResource(R.string.feed_posting_raw_error_description),
                actionText = stringResource(R.string.feed_posting_raw_error_retry),
                onActionClick = onRetryClick,
                modifier = modifier,
            )
        }
    }
}
