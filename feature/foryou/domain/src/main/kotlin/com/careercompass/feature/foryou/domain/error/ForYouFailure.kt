package com.careercompass.feature.foryou.domain.error

/**
 * For You 도메인이 **요청 전에** 확정하는 실패.
 *
 * 서버가 돌려준 실패는 `CoreDataFailure` 로 흐른다 — 추천·로드맵이 프로필·경험 카드 부족으로 산출되지 않는
 * 422 `PROFILE_INCOMPLETE` 는 `CoreDataFailure.ProfileIncomplete` 다. 여기서 사유를 새로 만들지 않는다.
 */
public sealed class ForYouFailure(
    message: String,
) : Exception(message) {
    /**
     * 내보낼 묶음을 하나도 고르지 않았다.
     *
     * 그대로 보내면 서버는 **빈 문서를 성공으로 돌려준다** — 사용자는 무엇이 잘못됐는지 모른 채 빈 화면을
     * 받는다. 실패로 만들어 고를 것을 고르게 한다.
     */
    public data object NoExportSection : ForYouFailure("no export section selected")
}
