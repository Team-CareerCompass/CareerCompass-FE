package com.careercompass.feature.feed.presentation.board

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.careercompass.core.common.reporting.ErrorReporter
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.board.BoardRegistration
import com.careercompass.core.model.board.MAX_BOARDS
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.toFailureKind
import com.careercompass.core.ui.mvi.MviIntent
import com.careercompass.core.ui.mvi.MviViewModel
import com.careercompass.core.ui.mvi.ReducerEvent
import com.careercompass.core.ui.mvi.UiState
import com.careercompass.feature.feed.domain.error.FeedFailure
import com.careercompass.feature.feed.domain.usecase.DetectBoardUseCase
import com.careercompass.feature.feed.domain.usecase.RegisterBoardUseCase
import com.careercompass.feature.feed.presentation.reporting.FeedFailureStage
import com.careercompass.feature.feed.presentation.reporting.recordFeedFailure
import com.careercompass.feature.feed.presentation.shared.model.FeedFailureReason
import com.careercompass.feature.feed.presentation.shared.model.toFeedFailureReason
import com.careercompass.feature.feed.presentation.shared.util.toDetectionState
import com.careercompass.feature.feed.presentation.shared.util.toDomainBoardType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import javax.inject.Inject

/** URL 입력란 아래 오류. 문구는 Screen 이 만든다. */
public enum class BoardUrlError {
    Invalid,
    Duplicate,
}

public sealed interface BoardRegisterMessage {
    public data object NetworkUnavailable : BoardRegisterMessage

    public data object DetectFailed : BoardRegisterMessage

    /**
     * 등록 제출이 실패했다. 실패 표(#204)의 어느 행이었는지([kind])를 그대로 들고 간다.
     *
     * 전에는 사유를 가리지 않는 문구 하나였다. `BOARD_BLOCKED`·`RATE_LIMITED`·`INVALID_INPUT` 이 모두
     * 「게시판을 등록하지 못했어요. 잠시 후 다시 시도해 주세요」로 접혀, 수집을 막아 둔 사이트를 등록하려던
     * 사용자는 잠시 후 같은 주소로 다시 시도하라는 말을 들었다(#360).
     */
    public data class RegisterFailed(
        val kind: FailureKind,
    ) : BoardRegisterMessage

    /**
     * 서버 점검(503 `LLM_UNAVAILABLE`)으로 **등록 제출**이 실패했다는 안내.
     *
     * [RegisterFailed] 로 접으면 「게시판을 등록하지 못했어요」가 되어, 서버가 잠깐 쉬는 것을 사용자가 자기
     * 입력이나 게시판 탓으로 읽는다(#212). 문구는 새로 짓지 않고 실패 표의 점검 행(`FailureKind.ServiceUnavailable`, #204)을
     * 그대로 쓴다 — 같은 사실을 화면마다 다르게 말하지 않는다.
     *
     * **감지(`BoardDetectionState.Maintenance`)와 자리가 다른 이유** — 감지는 서버가 외부 사이트를 처음부터
     * 다시 크롤링하는 비싼 호출이라, 사라지는 스낵바로 알리면 사용자가 「구조 분석하기」를 되풀이해 누른다.
     * 그래서 화면에 남는 상태로 둔다. 제출은 우리 서버가 자기 DB 에 쓰는 한 번의 호출이고, 실패해도 감지
     * 결과와 폼이 그대로 살아 있어 사용자가 서 있는 자리를 잃지 않는다. 그 자리에서 알리는 것이 스낵바다
     * (중복·상한·연결 단절도 모두 같은 자리를 쓴다).
     */
    public data object Maintenance : BoardRegisterMessage

    /**
     * 제출 중에 화면을 벗어나려 했을 때의 안내.
     *
     * 뒤로가기를 삼키기만 하면 사용자에게는 앱이 굳은 것과 구별되지 않아 더 세게 누른다. 왜 안 나가지는지와
     * 곧 끝난다는 사실을 그 자리에서 말해 준다.
     */
    public data object SubmitInProgress : BoardRegisterMessage

    /**
     * 이미 등록된 게시판이라 등록이 거절됐다는 안내.
     *
     * 같은 사실을 URL 입력란 아래에도 남기지만([BoardUrlError.Duplicate]) 그것만으로는 닿지 않는다 — 등록
     * 버튼은 화면 맨 아래에 있고 URL 입력란은 정보 카드 위쪽이라, 폼이 길면 오류가 화면 밖에 그려진다.
     * 사용자에게는 스피너가 조용히 사라진 것으로만 보인다(#146). 스낵바는 누른 자리 옆에 뜬다.
     */
    public data object AlreadyRegistered : BoardRegisterMessage

    public data class LimitReached(
        val limit: Int,
    ) : BoardRegisterMessage
}

public data class BoardRegisterViewState(
    val url: String = "",
    val urlError: BoardUrlError? = null,
    val detection: BoardDetectionState = BoardDetectionState.Idle,
    /** 감지에 실제로 쓴 정규화 URL — 등록 때 같은 값을 보낸다. 감지가 없거나 실패하면 null. */
    val detectedUrl: String? = null,
    val name: String = "",
    val type: BoardType? = null,
    val cycle: BoardCollectCycle = BoardCollectCycle.Daily,
    val isSubmitting: Boolean = false,
    val message: BoardRegisterMessage? = null,
    val isBackRequested: Boolean = false,
    val sessionEnded: Boolean = false,
) : UiState {
    public val isDetectEnabled: Boolean
        get() = url.isNotBlank() && detection != BoardDetectionState.Detecting && !isSubmitting

    public val isRegisterEnabled: Boolean
        get() = detection is BoardDetectionState.Success && name.isNotBlank() && type != null && !isSubmitting
}

/** 화면이 [BoardRegisterViewModel] 에 보내는 것. */
public sealed interface BoardRegisterIntent : MviIntent {
    public data class Screen(
        val event: BoardRegisterEvent,
    ) : BoardRegisterIntent

    public data object ConsumeBack : BoardRegisterIntent

    public data object ConsumeMessage : BoardRegisterIntent

    public data object ConsumeSessionEnded : BoardRegisterIntent
}

/** 상태가 겪은 것. [BoardRegisterViewModel] 만 만든다. */
public sealed interface BoardRegisterReducerEvent : ReducerEvent {
    /** URL 이 바뀌면 이전 감지 결과는 무효다. */
    public data class UrlChanged(
        val url: String,
    ) : BoardRegisterReducerEvent

    public data class NameChanged(
        val name: String,
    ) : BoardRegisterReducerEvent

    public data class TypeSelected(
        val type: BoardType,
    ) : BoardRegisterReducerEvent

    public data class CycleSelected(
        val cycle: BoardCollectCycle,
    ) : BoardRegisterReducerEvent

    public data class MessageRaised(
        val message: BoardRegisterMessage,
    ) : BoardRegisterReducerEvent

    public data object BackRequested : BoardRegisterReducerEvent

    public data object DetectionStarted : BoardRegisterReducerEvent

    /**
     * 감지가 끝났다 — 결과·오류·안내를 한 번에 놓는다. [message] 가 null 이면 지금 안내를 지우지 않는다.
     */
    public data class DetectionFinished(
        val detection: BoardDetectionState,
        val detectedUrl: String? = null,
        val urlError: BoardUrlError? = null,
        val message: BoardRegisterMessage? = null,
        val sessionEnded: Boolean = false,
    ) : BoardRegisterReducerEvent

    public data object SubmissionStarted : BoardRegisterReducerEvent

    /** 등록됐다 — 목록으로 돌아간다. */
    public data object Registered : BoardRegisterReducerEvent

    /** 등록이 실패했다 — 폼은 그대로 두고 오류·안내를 놓는다. */
    public data class RegistrationFailed(
        val urlError: BoardUrlError? = null,
        val message: BoardRegisterMessage? = null,
        val sessionEnded: Boolean = false,
    ) : BoardRegisterReducerEvent

    public data object BackConsumed : BoardRegisterReducerEvent

    public data object MessageConsumed : BoardRegisterReducerEvent

    public data object SessionEndedConsumed : BoardRegisterReducerEvent
}

/**
 * 게시판 등록 — URL → 구조 감지 → 이름·유형·주기 → 등록(기능 스펙 F2-1). 진입점은 [onIntent] 하나, 전이는
 * [reduce] 한 곳이다(#246).
 *
 * URL 이 바뀌면 이전 감지 결과는 무효다. 등록 성공은 [BoardRegisterViewState.isBackRequested] 로 목록에 돌아간다.
 *
 * 폼에 친 값은 [BoardRegisterInputDraft] 가 [SavedStateHandle] 에 남긴다 — 주소를 복사하러 브라우저에 다녀오는
 * 사이 프로세스가 죽어도 폼이 비지 않는다(#137). 무엇을 남기고 무엇을 버리는지는 그 클래스의 KDoc 에 있다.
 */
@HiltViewModel
public class BoardRegisterViewModel
    @Inject
    constructor(
        private val detectBoard: DetectBoardUseCase,
        private val registerBoard: RegisterBoardUseCase,
        private val errorReporter: ErrorReporter,
        savedStateHandle: SavedStateHandle,
    ) : MviViewModel<BoardRegisterIntent, BoardRegisterViewState, BoardRegisterReducerEvent>(
            BoardRegisterInputDraft(savedStateHandle).restoredState(),
        ) {
        private val draft = BoardRegisterInputDraft(savedStateHandle)

        private var detectJob: Job? = null

        init {
            // 값을 바꾸는 자리마다 저장하지 않고 상태 흐름 한 곳에서 남긴다 — 각자 저장하게 두면 언젠가 한
            // 곳이 빠지고, 빠진 자리는 프로세스가 죽어야 드러난다. 저장 대상이 실제로 바뀔 때만 쓴다.
            viewModelScope.launch {
                uiState
                    .map { BoardRegisterInputDraft.Input(url = it.url, name = it.name, type = it.type, cycle = it.cycle) }
                    .distinctUntilChanged()
                    .collect(draft::save)
            }
        }

        override fun onIntent(intent: BoardRegisterIntent) {
            when (intent) {
                is BoardRegisterIntent.Screen -> onEvent(intent.event)
                BoardRegisterIntent.ConsumeBack -> dispatch(BoardRegisterReducerEvent.BackConsumed)
                BoardRegisterIntent.ConsumeMessage -> dispatch(BoardRegisterReducerEvent.MessageConsumed)
                BoardRegisterIntent.ConsumeSessionEnded -> dispatch(BoardRegisterReducerEvent.SessionEndedConsumed)
            }
        }

        override fun reduce(
            state: BoardRegisterViewState,
            event: BoardRegisterReducerEvent,
        ): BoardRegisterViewState =
            when (event) {
                is BoardRegisterReducerEvent.UrlChanged -> {
                    state.copy(url = event.url, urlError = null, detection = BoardDetectionState.Idle, detectedUrl = null)
                }

                is BoardRegisterReducerEvent.NameChanged -> {
                    state.copy(name = event.name)
                }

                is BoardRegisterReducerEvent.TypeSelected -> {
                    state.copy(type = event.type)
                }

                is BoardRegisterReducerEvent.CycleSelected -> {
                    state.copy(cycle = event.cycle)
                }

                is BoardRegisterReducerEvent.MessageRaised -> {
                    state.copy(message = event.message)
                }

                BoardRegisterReducerEvent.BackRequested -> {
                    state.copy(isBackRequested = true)
                }

                BoardRegisterReducerEvent.DetectionStarted -> {
                    state.copy(detection = BoardDetectionState.Detecting, urlError = null, detectedUrl = null)
                }

                is BoardRegisterReducerEvent.DetectionFinished -> {
                    state.copy(
                        detection = event.detection,
                        detectedUrl = event.detectedUrl,
                        urlError = event.urlError,
                        message = event.message ?: state.message,
                        sessionEnded = state.sessionEnded || event.sessionEnded,
                    )
                }

                BoardRegisterReducerEvent.SubmissionStarted -> {
                    state.copy(isSubmitting = true)
                }

                BoardRegisterReducerEvent.Registered -> {
                    state.copy(isSubmitting = false, isBackRequested = true)
                }

                is BoardRegisterReducerEvent.RegistrationFailed -> {
                    state.copy(
                        isSubmitting = false,
                        urlError = event.urlError ?: state.urlError,
                        message = event.message ?: state.message,
                        sessionEnded = state.sessionEnded || event.sessionEnded,
                    )
                }

                BoardRegisterReducerEvent.BackConsumed -> {
                    state.copy(isBackRequested = false)
                }

                BoardRegisterReducerEvent.MessageConsumed -> {
                    state.copy(message = null)
                }

                BoardRegisterReducerEvent.SessionEndedConsumed -> {
                    state.copy(sessionEnded = false)
                }
            }

        private fun onEvent(event: BoardRegisterEvent) {
            when (event) {
                is BoardRegisterEvent.UrlChanged -> {
                    detectJob?.cancel()
                    dispatch(BoardRegisterReducerEvent.UrlChanged(event.value))
                }

                BoardRegisterEvent.DetectClicked -> {
                    detect()
                }

                is BoardRegisterEvent.NameChanged -> {
                    dispatch(BoardRegisterReducerEvent.NameChanged(event.value))
                }

                is BoardRegisterEvent.TypeSelected -> {
                    dispatch(BoardRegisterReducerEvent.TypeSelected(event.type))
                }

                is BoardRegisterEvent.CycleSelected -> {
                    dispatch(BoardRegisterReducerEvent.CycleSelected(event.cycle))
                }

                BoardRegisterEvent.RegisterClicked -> {
                    register()
                }

                BoardRegisterEvent.BackClicked -> {
                    onBackRequested()
                }
            }
        }

        /**
         * 제출 중에는 화면을 벗어나지 않는다 — 상단 화살표든 시스템 뒤로가기든 여기로 모인다.
         *
         * 나가게 두면 백스택 엔트리와 함께 [viewModelScope] 가 정리되며 진행 중이던 등록 요청이 끊긴다.
         * 서버가 이미 처리를 마쳤다면 게시판은 만들어졌는데 화면은 아무 말도 못 하고, 사용자는 다시 등록하다
         * 「이미 등록된 게시판」을 만난다(#146). 「나가게 두고 결과를 나중에 알린다」는 쪽은 고르지 않았다 —
         * 그러려면 화면보다 오래 사는 스코프와 결과를 전할 자리(다른 화면의 안내)가 함께 필요한데, 그 스코프는
         * 이 모듈이 소유한 것이 아니다. 대신 [BoardRegisterMessage.SubmitInProgress] 로 이유를 말한다.
         *
         * **갇히지 않는다**: 등록 요청은 일반 API OkHttp 클라이언트(`NetworkModule`)를 타고, call 타임아웃
         * 30초가 걸려 있다. 상한 확인·등록으로 호출이 둘이라 최악이 약 1분이며, 어느 경로로 끝나든
         * `isSubmitting` 은 풀린다. 게시판 구조 감지(`LongRunningOperation.BoardDetect`, 2분)와 달리 등록은
         * 우리 서버가 자기 DB 에 쓰는 호출이라 이 상한을 늘릴 이유도 없다.
         */
        private fun onBackRequested() {
            if (currentState.isSubmitting) {
                dispatch(BoardRegisterReducerEvent.MessageRaised(BoardRegisterMessage.SubmitInProgress))
            } else {
                dispatch(BoardRegisterReducerEvent.BackRequested)
            }
        }

        private fun detect() {
            val current = currentState
            if (!current.isDetectEnabled) return
            detectJob?.cancel()
            dispatch(BoardRegisterReducerEvent.DetectionStarted)
            detectJob =
                viewModelScope.launch {
                    detectBoard(current.url)
                        .onSuccess { outcome ->
                            dispatch(
                                BoardRegisterReducerEvent.DetectionFinished(
                                    detection = outcome.detection.toDetectionState(),
                                    detectedUrl = outcome.url.takeIf { outcome.detection.isRegistrable },
                                ),
                            )
                        }.onFailure { throwable -> onDetectFailed(throwable) }
                }
        }

        private fun onDetectFailed(throwable: Throwable) {
            val event =
                when (throwable) {
                    // 사용자 입력 형태 오류 — 요청 없이 끝났으므로 리포팅 대상이 아니다.
                    is FeedFailure.InvalidBoardUrl -> {
                        BoardRegisterReducerEvent.DetectionFinished(BoardDetectionState.Idle, urlError = BoardUrlError.Invalid)
                    }

                    is CoreDataFailure.BoardBlocked -> {
                        errorReporter.recordFeedFailure(FeedFailureStage.BoardDetect, throwable)
                        BoardRegisterReducerEvent.DetectionFinished(BoardDetectionState.Failed(BoardDetectionFailure.Blocked))
                    }

                    // 타임아웃은 화면에 남는 상태로, 연결 단절은 지금처럼 스낵바로 알린다. 서버가 외부 사이트를
                    // 크롤링하는 동안 우리가 먼저 끊은 것을 「연결을 확인해 주세요」로 안내하면 연결이 멀쩡한
                    // 사용자를 헛수고시키고, 감지 실패 문구로 안내하면 사이트가 지원 안 된다는 오해를 부른다.
                    // 둘 다 리포팅은 남긴다 — 일시적 전송 실패라 세션 첫 건만 표본이 되고, 그래야 「감지만
                    // 타임아웃한다」는 신호가 보인다.
                    is CoreDataFailure.NetworkUnavailable -> {
                        errorReporter.recordFeedFailure(FeedFailureStage.BoardDetect, throwable)
                        if (throwable.isTimeout) {
                            BoardRegisterReducerEvent.DetectionFinished(BoardDetectionState.TimedOut)
                        } else {
                            BoardRegisterReducerEvent.DetectionFinished(
                                BoardDetectionState.Idle,
                                message = BoardRegisterMessage.NetworkUnavailable,
                            )
                        }
                    }

                    // 남은 것은 「요청 자체가 실패했다」이지 「서버가 감지 결과를 알렸다」가 아니다. 서버가 알린
                    // 감지 결과는 성공 경로로 와 BoardDetectionState.Failed 가 되고(`detect_status` — 위의
                    // BoardBlocked 도 그 자리다), 여기 오는 것은 요청이 결과를 만들지 못하고 끝난 경우뿐이다.
                    // 그중 점검(503)만 사유가 분명하므로 갈라 화면에 남기고, 나머지는 사유를 모르는 실패라
                    // 지금처럼 스낵바 한 줄로 접는다.
                    else -> {
                        errorReporter.recordFeedFailure(FeedFailureStage.BoardDetect, throwable)
                        when (throwable.toFeedFailureReason()) {
                            FeedFailureReason.Maintenance -> {
                                BoardRegisterReducerEvent.DetectionFinished(BoardDetectionState.Maintenance)
                            }

                            FeedFailureReason.NetworkUnavailable,
                            is FeedFailureReason.Generic,
                            -> {
                                BoardRegisterReducerEvent.DetectionFinished(
                                    BoardDetectionState.Idle,
                                    message = BoardRegisterMessage.DetectFailed,
                                    sessionEnded = throwable is CoreDataFailure.Unauthorized,
                                )
                            }
                        }
                    }
                }
            dispatch(event)
        }

        /**
         * 등록 제출. 진행 중임은 [BoardRegisterViewState.isSubmitting] 하나로 말한다 — 화면의 진행 표시,
         * 입력·버튼 잠금, 이탈 차단([onBackRequested])이 모두 이 값을 본다.
         *
         * 요청은 [viewModelScope] 에 그대로 둔다. 이탈을 막았으므로 화면이 먼저 사라져 요청이 끊기는 길이
         * 사라졌고, 남은 것은 프로세스 사망뿐인데 그때는 어떤 스코프도 살아남지 못한다. 화면보다 오래 사는
         * 스코프는 `core` 소유라 여기서 새로 만들지 않는다(후속 과제).
         */
        private fun register() {
            val current = currentState
            val type = current.type ?: return
            if (!current.isRegisterEnabled) return
            val registration =
                BoardRegistration(
                    url = current.detectedUrl ?: current.url,
                    name = current.name.trim(),
                    type = type.toDomainBoardType(),
                    cycleHours = current.cycle.hours,
                )
            dispatch(BoardRegisterReducerEvent.SubmissionStarted)
            viewModelScope.launch {
                registerBoard(registration)
                    .onSuccess { dispatch(BoardRegisterReducerEvent.Registered) }
                    .onFailure { throwable -> onRegisterFailed(throwable) }
            }
        }

        private fun onRegisterFailed(throwable: Throwable) {
            if (throwable !is FeedFailure.InvalidBoardUrl) {
                errorReporter.recordFeedFailure(FeedFailureStage.BoardRegister, throwable)
            }
            val event =
                when (throwable) {
                    is FeedFailure.InvalidBoardUrl -> {
                        BoardRegisterReducerEvent.RegistrationFailed(urlError = BoardUrlError.Invalid)
                    }

                    // 입력란 아래 오류와 스낵바를 함께 남긴다 — 오류는 어느 값이 문제인지 가리키고,
                    // 스낵바는 화면 밖으로 밀려난 그 오류를 사용자 눈앞으로 가져온다.
                    is CoreDataFailure.DuplicateBoard -> {
                        BoardRegisterReducerEvent.RegistrationFailed(
                            urlError = BoardUrlError.Duplicate,
                            message = BoardRegisterMessage.AlreadyRegistered,
                        )
                    }

                    is FeedFailure.BoardLimitReached -> {
                        BoardRegisterReducerEvent.RegistrationFailed(message = BoardRegisterMessage.LimitReached(throwable.limit))
                    }

                    is CoreDataFailure.LimitExceeded -> {
                        BoardRegisterReducerEvent.RegistrationFailed(message = BoardRegisterMessage.LimitReached(MAX_BOARDS))
                    }

                    is CoreDataFailure.NetworkUnavailable -> {
                        BoardRegisterReducerEvent.RegistrationFailed(message = BoardRegisterMessage.NetworkUnavailable)
                    }

                    is CoreDataFailure.ServiceUnavailable -> {
                        BoardRegisterReducerEvent.RegistrationFailed(message = BoardRegisterMessage.Maintenance)
                    }

                    is CoreDataFailure.Unauthorized -> {
                        BoardRegisterReducerEvent.RegistrationFailed(sessionEnded = true)
                    }

                    else -> {
                        BoardRegisterReducerEvent.RegistrationFailed(
                            message = BoardRegisterMessage.RegisterFailed(throwable.toFailureKind()),
                        )
                    }
                }
            dispatch(event)
        }
    }
