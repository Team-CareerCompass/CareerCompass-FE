package com.careercompass.feature.feed.presentation.reporting

import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.common.reporting.recordStagedFailure

/** 리포팅 속성 키 — 피드 기능 안에서 어느 단계가 실패했는지. */
public const val FEED_REPORT_KEY_STAGE: String = "feed_stage"

/** 리포팅 속성 키. 값은 열지 못한 공고 원문 주소의 스킴이다. */
public const val FEED_REPORT_KEY_URL_SCHEME: String = "url_scheme"

/** 피드 기능의 실패 단계. 값은 리포팅 콘솔 필터용 안정 식별자다. */
public enum class FeedFailureStage(
    public val key: String,
) {
    FeedLoad("feed_load"),
    FeedRefresh("feed_refresh"),
    FeedLoadMore("feed_load_more"),
    FeedSnapshotSave("feed_snapshot_save"),
    FeedSnapshotLoad("feed_snapshot_load"),
    TodayCount("today_count"),
    FilterBoards("filter_boards"),
    Bookmark("bookmark"),
    PostingDetail("posting_detail"),

    /** 적합도 자동 재조회(#221) — 화면을 흔들지 않는 조용한 실패라 [PostingDetail] 과 갈라 센다. */
    SuitabilityRecheck("suitability_recheck"),
    PostingRaw("posting_raw"),
    BoardDetect("board_detect"),
    BoardRegister("board_register"),
    BoardList("board_list"),
    BoardToggle("board_toggle"),
    BoardRetry("board_retry"),
    BoardDelete("board_delete"),
    BoardUpdate("board_update"),
}

/**
 * [ErrorReporter.recordFailure] 에 피드 단계 속성을 붙여 기록한다. 취소 필터링은 인터페이스가 한다.
 *
 * 무엇을 접고 무엇을 남길지는 [recordStagedFailure] 한 곳이 정한다 — 온보딩과 같은 규칙을 쓴다.
 * 여기서 다시 거르면 두 규칙이 갈라진다.
 *
 * @param attributes 단계 외에 이 실패에만 붙는 컨텍스트. [ErrorReporter.recordFailure] 가 예외 문구를
 *                   버리므로, 콘솔에 남겨야 할 값은 문구가 아니라 이쪽으로 넘긴다. 개인정보·자격증명은
 *                   넣지 않는다.
 */
public fun ErrorReporter.recordFeedFailure(
    stage: FeedFailureStage,
    throwable: Throwable,
    attributes: Map<String, String> = emptyMap(),
) {
    recordStagedFailure(
        stageKey = FEED_REPORT_KEY_STAGE,
        stage = stage.key,
        throwable = throwable,
        attributes = attributes,
    )
}
