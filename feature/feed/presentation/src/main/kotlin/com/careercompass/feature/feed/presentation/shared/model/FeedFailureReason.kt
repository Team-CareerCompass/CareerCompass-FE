package com.careercompass.feature.feed.presentation.shared.model

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind

/**
 * 실패 화면이 갈라 그려야 하는 사유. 사용자가 할 일이 다른 것만 가른다.
 *
 * - [NetworkUnavailable] — 내 연결을 확인하면 된다.
 * - [Maintenance] — 서버가 스스로 「지금은 안 된다」고 알린 상태(503 `LLM_UNAVAILABLE`)다. 재시도만
 *   되풀이해도 소용없다는 것을 말해 줘야 한다.
 * - [Generic] — 전용 화면을 두지 않은 나머지. 무엇을 띄울지는 실패 표가 정하므로, 원 실패가 표의 어느
 *   행이었는지([Generic.kind])를 들고 간다.
 */
public sealed interface FeedFailureReason {
    public data object NetworkUnavailable : FeedFailureReason

    public data object Maintenance : FeedFailureReason

    /**
     * 실패 표의 행을 그대로 그리는 사유. [kind] 가 제목·본문과 재시도 유무를 정한다.
     *
     * 전에는 여기가 사유를 버리는 자리였다. 404·403·422·429 가 모두 「사유 미확인」으로 접혀
     * [FailureKind.Unexpected] 한 행으로 나갔고, 삭제된 공고에 딥링크로 들어간 사용자는 「공고를 찾을 수
     * 없어요」 대신 「문제가 생겼어요 / 잠시 후 다시 시도해 주세요」와 재시도 버튼을 봤다. 눌러도 같은
     * 404 가 돌아온다(#342). 이제 원 실패의 행을 그대로 실어, 재시도 유무도 표의 판정
     * (`FailureDisplay.isRetryable`)을 따른다.
     */
    public data class Generic(
        val kind: FailureKind,
    ) : FeedFailureReason
}

public fun Throwable.toFeedFailureReason(): FeedFailureReason =
    when (this) {
        is CoreDataFailure.NetworkUnavailable -> FeedFailureReason.NetworkUnavailable
        is CoreDataFailure.ServiceUnavailable -> FeedFailureReason.Maintenance
        else -> FeedFailureReason.Generic(toFailureKind())
    }

/**
 * 이 실패를 **조회 조건 탓으로 볼 여지가 있는가** — 실패 화면에 「조건 지우고 다시 보기」를 열지의 근거다.
 *
 * 사유를 갈라 놓고도 처방을 하나로 뭉치면, 지하철에서 연결이 끊긴 사용자에게 「조건을 지워 보라」고 하게
 * 된다. 지워도 요청은 여전히 서버에 닿지 못하므로 눌러도 아무 일 없는 버튼이다.
 *
 * - [FeedFailureReason.NetworkUnavailable] — **아니다.** 요청이 서버에 닿지도 못했으니 조건이 답을 바꿀
 *   여지가 없다. 여기서 할 일은 연결을 되살리거나 저장해 둔 스냅샷을 보는 것이다.
 * - [FeedFailureReason.Maintenance] — **그렇다.** 503 `LLM_UNAVAILABLE` 은 서버가 「그 조건은 지금 못
 *   한다」고 답한 자리다. 적합도는 LLM 산출값이라 정렬을 「적합도순」으로 바꾸거나 최소 적합도를 걸면
 *   실제로 갈린다(이슈 #144 의 재현).
 * - [FeedFailureReason.Generic] — **그렇다.** 실린 갈래가 무엇이든 이 판정은 갈리지 않는다(#342 에서
 *   갈래를 실은 뒤에도 그대로다). 잘못된 조합에 대한 400·422·500 이 모두 여기로 오고, 되돌릴 조건이
 *   실제로 걸려 있을 때에 한해서만 열리므로(`FeedViewState.canResetFailedQuery`) 헛다리를 짚어도 잃는
 *   것이 없고, 닫아 두면 빠져나갈 길이 없는 화면이 남는다.
 *
 * 401(세션 만료)이 여기 없는 이유 — 그 실패는 실패 화면을 그리지 않고 곧장 로그인으로 보낸다
 * (`FeedViewState.sessionEnded`). 사유 목록에 없는 것이 곧 답이다.
 */
public val FeedFailureReason.isQueryAttributable: Boolean
    get() =
        when (this) {
            FeedFailureReason.NetworkUnavailable -> false

            FeedFailureReason.Maintenance,
            is FeedFailureReason.Generic,
            -> true
        }

/**
 * 이 사유를 실패 표(`core:ui` 의 `FailureDisplay`)의 어느 행으로 읽을지 — **문구의 정본은 표 하나다**(#204).
 *
 * 사유를 지우고 표로 갈아치우지 않았다. 이 셋은 「사용자가 할 일이 다르다」로 갈라 놓은 판정이고
 * ([isQueryAttributable] 의 #144, 점검 전용 화면의 #101), 표는 그 갈래마다 **무슨 문장을 띄울지**만
 * 정한다. 판정과 문구가 한 몸이었다면 문구를 고치려다 판정이 함께 흔들린다.
 *
 * [FeedFailureReason.NetworkUnavailable] 이 [FailureKind.NoConnection] 으로만 가는 것은 의도다 —
 * 「우리가 먼저 끊었다」(타임아웃)를 갈라 안내하는 화면은 게시판 구조 감지 하나뿐이고, 그 화면은
 * 이 사유를 거치지 않고 `CoreDataFailure.NetworkUnavailable.isTimeout` 을 직접 본다(#134).
 *
 * [FeedFailureReason.Generic] 만 행이 고정돼 있지 않다. 원 실패가 정한 행을 그대로 돌려준다(#342).
 */
public val FeedFailureReason.failureKind: FailureKind
    get() =
        when (this) {
            FeedFailureReason.NetworkUnavailable -> FailureKind.NoConnection
            FeedFailureReason.Maintenance -> FailureKind.ServiceUnavailable
            is FeedFailureReason.Generic -> kind
        }
