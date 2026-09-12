package com.careercompass.feature.feed.presentation.board

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassNetworkErrorState
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.feature.feed.presentation.shared.component.FeedMaintenanceState
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.failureKind

/**
 * 게시판 목록을 못 받았을 때 사유별로 갈리는 화면 — [BoardListScreen] 가 상태 없이 그리는 부분만 떼어 냈다.
 *
 * 게시판 목록은 스냅샷을 저장하지 않으므로 「오프라인 모드로 보기」는 어느 사유에서도 열지 않는다.
 */
@Composable
internal fun BoardListFailureContent(
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
            // 문구는 실패 표에서 읽는다(#204). 같은 사유라도 「게시판」이라는 명사는 문맥이 채우고, 버튼은 표의
            // 행동이 있을 때만 붙는다. 실패 전용 부품이라 「검색 결과 없음」과 삽화부터 갈린다(#222).
            // 목록이 열 수 있는 길은 재조회뿐이므로 표가 다른 행동을 가리키면 콜백을 넘기지 않는다(#342).
            val display = reason.failureKind.display(FailureSurface.Board)
            CareerCompassFailureState(
                display = display,
                onActionClick = onRetryClick.takeIf { display.isRetryable },
                modifier = modifier,
            )
        }
    }
}
