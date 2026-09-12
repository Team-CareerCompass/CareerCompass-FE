package com.careercompass.feature.feed.presentation.board

/** Maximum number of preview posts returned by structure detection (spec F2-1). */
public const val BOARD_MAX_PREVIEW_COUNT: Int = 5

/** Default cap on registered boards (spec F2-1). */
public const val BOARD_DEFAULT_MAX_COUNT: Int = 20

/** Posting category a board is registered under (spec F2-1). */
public enum class BoardType {
    Scholarship,
    Employment,
    Contest,
    ExternalActivity,
    Other,
}

/** How often a board is crawled. */
public enum class BoardCollectCycle(
    public val hours: Int,
) {
    Daily(24),
    TwiceDaily(12),
    Weekly(168),
}

/** One recent post shown in the detection preview. */
public data class BoardPreviewItemUiModel(
    val title: String,
    val url: String,
    val dateLabel: String?,
) {
    init {
        require(title.isNotBlank()) { "title must not be blank" }
        require(url.isNotBlank()) { "url must not be blank" }
        require(dateLabel == null || dateLabel.isNotBlank()) { "dateLabel must be null or non-blank" }
    }
}

/** Why automatic structure detection could not register the board (spec F2-1). */
public enum class BoardDetectionFailure {
    LoginRequired,
    Spa,
    Blocked,
    Failed,
}

/** Lifecycle of the structure detection request. */
public sealed interface BoardDetectionState {
    public data object Idle : BoardDetectionState

    public data object Detecting : BoardDetectionState

    /**
     * 서버 응답을 기다리다 끊긴 상태 — 서버가 사유를 알린 [Failed] 와 갈라 둔다.
     *
     * 둘을 같은 문구로 안내하면 사용자가 「이 사이트는 지원되지 않는다」로 읽고 등록을 접거나, 재시도를
     * 눌러 서버에 같은 크롤링을 처음부터 다시 시킨다(#134). 여기서는 사이트가 느렸을 뿐이라는 사실과
     * 다시 시도할 길만 안내한다.
     */
    public data object TimedOut : BoardDetectionState

    /**
     * 서버 점검(503 `LLM_UNAVAILABLE`)으로 감지 **요청 자체가** 끝난 상태.
     *
     * [Failed] 와 갈라 두는 이유는 둘이 서로 다른 층의 사실이기 때문이다. [Failed] 는 서버가 외부 사이트를
     * 실제로 훑어보고 「이 게시판은 이래서 안 된다」고 알린 **감지 결과**(`detect_status` = `login_required`·
     * `spa`·`blocked`·`failed`)이고, 여기는 서버가 훑어보지도 못한 **요청의 실패**다. 한 문구로 접으면
     * 「구조를 분석하지 못했어요」가 되어, 사용자는 멀쩡한 자기 게시판 URL 을 의심하며 재시도를 되풀이한다
     * (#134 가 타임아웃에서 고친 것과 같은 오해다). 감지는 서버가 외부 사이트를 처음부터 다시 크롤링하는
     * 비싼 호출이라 헛된 재시도의 대가도 크다.
     *
     * [TimedOut] 과도 처방이 다르다. 타임아웃은 사이트가 느렸을 뿐이라 「다시 시도」가 뜻이 있지만, 점검은
     * 서버가 돌아와야 답이 달라져 재시도를 권하면 안 된다(#144 — 「할 수 있는 일이 있는 상태에만 행동 버튼」).
     * 그래서 화면은 이 상태에 행동 버튼을 그리지 않는다.
     */
    public data object Maintenance : BoardDetectionState

    public data class Success(
        val preview: List<BoardPreviewItemUiModel>,
        val dateDetected: Boolean,
    ) : BoardDetectionState {
        init {
            require(preview.size in 1..BOARD_MAX_PREVIEW_COUNT) {
                "preview must contain 1..$BOARD_MAX_PREVIEW_COUNT items"
            }
        }
    }

    /**
     * 서버가 외부 사이트를 훑어보고 알린 **감지 결과**(`detect_status`)다 — 요청 자체가 실패한
     * [Maintenance]·[TimedOut] 과 다른 층이다. 여기까지 왔다는 것은 서버가 답을 줬다는 뜻이다.
     */
    public data class Failed(
        val reason: BoardDetectionFailure,
    ) : BoardDetectionState
}

/** Complete, display-ready state for [BoardRegisterContent]. */
public data class BoardRegisterUiState(
    val url: String,
    val urlError: String?,
    val detection: BoardDetectionState,
    val name: String,
    val type: BoardType?,
    val cycle: BoardCollectCycle,
    val isSubmitting: Boolean,
) {
    init {
        require(urlError == null || urlError.isNotBlank()) { "urlError must be null or non-blank" }
    }

    /** Structure detection needs a URL and must not overlap an in-flight detection or submission. */
    val isDetectEnabled: Boolean
        get() = url.isNotBlank() && detection != BoardDetectionState.Detecting && !isSubmitting

    /** Registration requires a successful detection plus the mandatory name and type. */
    val isRegisterEnabled: Boolean
        get() = detection is BoardDetectionState.Success && name.isNotBlank() && type != null && !isSubmitting
}

/** User intents emitted by the stateless board registration UI. */
public sealed interface BoardRegisterEvent {
    public data class UrlChanged(
        val value: String,
    ) : BoardRegisterEvent

    public data object DetectClicked : BoardRegisterEvent

    public data class NameChanged(
        val value: String,
    ) : BoardRegisterEvent

    public data class TypeSelected(
        val type: BoardType,
    ) : BoardRegisterEvent

    public data class CycleSelected(
        val cycle: BoardCollectCycle,
    ) : BoardRegisterEvent

    public data object RegisterClicked : BoardRegisterEvent

    public data object BackClicked : BoardRegisterEvent
}

/**
 * 등록된 게시판의 수집 상태.
 *
 * [Failing] 은 수집이 실패하는 중이지만 아직 켜져 있는 상태고, [Deactivated] 는 연속 실패로 **서버가 끈** 상태다
 * (기능 스펙 F2-2 「3회 연속 실패 시 비활성화」). 사용자가 직접 끈 [Paused] 와 갈라 두는 이유는 화면이 할 말이
 * 다르기 때문이다 — 꺼진 이유와 되살리는 방법을 [Deactivated] 에서만 안내한다.
 */
public enum class BoardStatus {
    Active,
    Paused,
    Failing,
    Deactivated,
}

/**
 * Display-only data for one registered board.
 *
 * [postingCount] is `null` when the source does not report how many postings the board produced;
 * the count row is then omitted instead of showing a misleading zero.
 *
 * [isRetrying] 는 재시도 요청이 오가는 중이라는 뜻이다. 그동안 재시도 버튼은 잠긴다(#341).
 */
public data class BoardUiModel(
    val id: String,
    val name: String,
    val url: String,
    val type: BoardType,
    val typeLabel: String,
    val status: BoardStatus,
    val isActive: Boolean,
    val failCount: Int,
    val lastCollectedLabel: String?,
    val postingCount: Int?,
    val isRetrying: Boolean = false,
) {
    init {
        require(id.isNotBlank()) { "id must not be blank" }
        require(name.isNotBlank()) { "name must not be blank" }
        require(url.isNotBlank()) { "url must not be blank" }
        require(typeLabel.isNotBlank()) { "typeLabel must not be blank" }
        require(failCount >= 0) { "failCount must not be negative" }
        require(lastCollectedLabel == null || lastCollectedLabel.isNotBlank()) {
            "lastCollectedLabel must be null or non-blank"
        }
        require(postingCount == null || postingCount >= 0) { "postingCount must be null or non-negative" }
        // 실패 상태 둘은 토글 값으로 갈린다. 어긋난 조합을 허용하면 화면이 안내를 잘못 고르므로 만들지 못하게 막는다.
        require(status != BoardStatus.Deactivated || !isActive) { "Deactivated boards must not be active" }
        require(status != BoardStatus.Failing || isActive) { "Failing boards must be active" }
    }
}

/** Mutually exclusive loading states for the board list. */
public sealed interface BoardListContentState {
    public data object Loading : BoardListContentState

    public data object Empty : BoardListContentState

    public data class Loaded(
        val boards: List<BoardUiModel>,
    ) : BoardListContentState {
        init {
            require(boards.isNotEmpty()) { "Use BoardListContentState.Empty when there are no boards" }
            require(boards.map(BoardUiModel::id).distinct().size == boards.size) { "board ids must be unique" }
        }
    }
}

/** Complete, display-ready state for [BoardListContent]. */
public data class BoardListUiState(
    val content: BoardListContentState,
    val maxBoardCount: Int = BOARD_DEFAULT_MAX_COUNT,
) {
    init {
        require(maxBoardCount > 0) { "maxBoardCount must be positive" }
        if (content is BoardListContentState.Loaded) {
            require(content.boards.size <= maxBoardCount) {
                "boards must not exceed maxBoardCount ($maxBoardCount)"
            }
        }
    }
}

/** User intents emitted by the stateless board list UI. */
public sealed interface BoardListEvent {
    public data object AddBoardClicked : BoardListEvent

    public data class BoardToggled(
        val boardId: String,
    ) : BoardListEvent

    public data class RetryClicked(
        val boardId: String,
    ) : BoardListEvent

    public data class DeleteClicked(
        val boardId: String,
    ) : BoardListEvent

    public data class BoardSelected(
        val boardId: String,
    ) : BoardListEvent

    public data object BackClicked : BoardListEvent
}
