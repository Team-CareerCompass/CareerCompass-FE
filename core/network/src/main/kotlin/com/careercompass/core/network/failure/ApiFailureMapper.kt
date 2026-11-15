package com.careercompass.core.network.failure

import com.careercompass.core.domain.error.CoreAuthFailure
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.network.interceptor.TokenReissueFailureException
import com.careercompass.core.network.model.ApiException
import java.io.IOException

/**
 * `ApiException`(서버 봉투 실패)·`IOException`(전송 실패)을 도메인 사유로 옮긴다 — API_SPEC v0.1 §9.
 *
 * 사유가 확인된 실패만 치환하고 나머지는 원본 그대로 두어 소비처가 일반 문구로 내려앉는다. 취소는 다시 보지
 * 않는다 — 호출부가 전부 `runCatchingCancellable` 이라 `CancellationException` 이 [Result] 에 담기지 않는다.
 *
 * `IOException` 을 전부 `NetworkUnavailable` 하나로 접는 것은 **화면용 분류**다 — 사용자에게는 cleartext 차단도
 * TLS 회귀도 「네트워크 오류」다. 관측용 분류는 그 안에 든 원본을 다시 읽는다
 * (`core:common` 의 `transportFailureKind`), 그래서 원본을 잃지 않도록 그대로 cause 에 실어 보낸다.
 *
 * 접지 않는 `IOException` 이 하나 있다 — 토큰 재발급이 끝낸 요청(`TokenReissueFailureException`)이다. OkHttp
 * `Authenticator` 가 `IOException` 만 던질 수 있어 타입이 전송 실패와 같아졌을 뿐, 서버는 이미 답을 했다.
 * 타입만 보고 접으면 재발급 503 이 「인터넷 연결을 확인해 주세요」가 되므로 실려 온 사유와 원본 응답을 다시
 * 읽어 일반 요청과 같은 표로 옮긴다(#361).
 *
 * 오래 걸리는 서버 작업을 기다리는 화면은 그 원본에서 타임아웃 하나만 더 갈라 본다
 * (`CoreDataFailure.NetworkUnavailable.isTimeout`). 여기서 사유를 새로 만들지 않은 것은 의도다 — 사유를 늘리면
 * 갈라 볼 이유가 없는 나머지 화면까지 `is NetworkUnavailable` 이 빗나가 일반 오류로 내려앉는다.
 *
 * **`core:data` 가 아니라 여기 사는 이유** — 옮기기 전에는 `core:data` 의 `internal` 이었고, 그래서 §6 을
 * 받는 `feature:editor:data` 처럼 **`core:data` 밖에 있는 data 모듈**은 이 번역을 부를 수 없었다. 부를 수
 * 없으면 같은 §9 표를 한 벌 더 쓰게 되고, 두 벌이 되는 순간 새 에러 코드가 한쪽에만 들어간다. 번역의 입력은
 * 네트워크 계층의 타입(`ApiException`·`IOException`)이고 출력은 `core:domain` 의 사유이므로, 계약(§9)이
 * 선언된 자리인 이 모듈이 한 벌을 두기에 맞다.
 */
public fun <T> Result<T>.mapDataFailure(): Result<T> =
    when (val exception = exceptionOrNull()) {
        // 재발급 실패도 IOException 이라 아래 전송 실패 가지보다 먼저 갈라야 한다.
        is TokenReissueFailureException -> exception.toDataFailure()?.let { Result.failure(it) } ?: this

        is ApiException -> Result.failure(exception.toDataFailure())

        is IOException -> Result.failure(CoreDataFailure.NetworkUnavailable(exception))

        else -> this
    }

/**
 * 토큰 재발급이 끝낸 요청을 재발급 쪽 사유로 옮긴다. 사유를 확인하지 못했으면 null 을 돌려 원본을 남긴다.
 *
 * 서버가 답을 한 갈래(`Server`·`Unexpected`)는 그 응답이 cause 에 실려 있으므로 일반 요청과 같은 §9 표를
 * 그대로 탄다 — 503 은 점검, 나머지 5xx 는 서버 오류, 429 는 요청 과다다. 전송 갈래는 재발급 요청 자체의
 * `IOException` 을 꺼내 감싼다: 껍데기를 그대로 감싸면 `NetworkUnavailable.isTimeout` 이 늘 false 라
 * 응답을 기다리다 끊긴 것과 연결이 없는 것이 구분되지 않는다.
 *
 * 세션이 끝난 갈래는 여기서 사유를 확정하지 않는다. 그 답은 문구가 아니라 로그인 화면으로 보내는 이동인데
 * 이 경로에는 아직 그 이동이 없어, 사유만 새로 달면 틀린 문구를 다른 틀린 문구로 바꾸는 일이 된다.
 */
private fun TokenReissueFailureException.toDataFailure(): Throwable? =
    when (reason) {
        TokenReissueFailureException.Reason.Transport -> {
            CoreDataFailure.NetworkUnavailable(cause as? IOException ?: this)
        }

        TokenReissueFailureException.Reason.Server, TokenReissueFailureException.Reason.Unexpected -> {
            (cause as? ApiException)?.toDataFailure()
        }

        TokenReissueFailureException.Reason.SessionEnded -> {
            null
        }
    }

/** 인증 API 전용 — 소셜 로그인 거절과 전송 실패만 인증 사유로 갈고, 나머지는 데이터 사유와 같다. */
public fun <T> Result<T>.mapAuthFailure(): Result<T> =
    when (val exception = exceptionOrNull()) {
        is ApiException -> {
            when (exception.code) {
                CODE_AUTH_REQUIRED, CODE_AUTH_INVALID -> Result.failure(CoreAuthFailure.SocialLoginRejected(exception))
                else -> Result.failure(exception.toDataFailure())
            }
        }

        is IOException -> {
            Result.failure(CoreAuthFailure.NetworkUnavailable(exception))
        }

        else -> {
            this
        }
    }

public fun ApiException.toDataFailure(): Throwable =
    when (code) {
        CODE_INVALID_INPUT -> {
            CoreDataFailure.InvalidInput(code, field, this)
        }

        CODE_AUTH_REQUIRED, CODE_AUTH_INVALID -> {
            CoreDataFailure.Unauthorized(code, this)
        }

        CODE_PERMISSION_DENIED -> {
            CoreDataFailure.Forbidden(code, this)
        }

        CODE_RESOURCE_NOT_FOUND, CODE_POSTING_NOT_FOUND -> {
            CoreDataFailure.NotFound(code, this)
        }

        CODE_DUPLICATE_BOARD -> {
            CoreDataFailure.DuplicateBoard(code, this)
        }

        CODE_LIMIT_EXCEEDED -> {
            CoreDataFailure.LimitExceeded(code, this)
        }

        CODE_PROFILE_INCOMPLETE -> {
            CoreDataFailure.ProfileIncomplete(code, this)
        }

        CODE_PARSING_FAILED -> {
            CoreDataFailure.ParsingFailed(code, this)
        }

        CODE_BOARD_BLOCKED -> {
            CoreDataFailure.BoardBlocked(code, this)
        }

        CODE_RATE_LIMITED -> {
            CoreDataFailure.RateLimited(code, this)
        }

        CODE_LLM_UNAVAILABLE -> {
            CoreDataFailure.ServiceUnavailable(code, this)
        }

        CODE_INTERNAL_ERROR -> {
            CoreDataFailure.ServerError(code, this)
        }

        else -> {
            when (status) {
                401 -> CoreDataFailure.Unauthorized(code, this)
                403 -> CoreDataFailure.Forbidden(code, this)
                404 -> CoreDataFailure.NotFound(code, this)
                429 -> CoreDataFailure.RateLimited(code, this)
                503 -> CoreDataFailure.ServiceUnavailable(code, this)
                in 500..599 -> CoreDataFailure.ServerError(code, this)
                else -> this
            }
        }
    }

private const val CODE_INVALID_INPUT = "INVALID_INPUT"
private const val CODE_AUTH_REQUIRED = "AUTH_REQUIRED"
private const val CODE_AUTH_INVALID = "AUTH_INVALID"
private const val CODE_PERMISSION_DENIED = "PERMISSION_DENIED"
private const val CODE_RESOURCE_NOT_FOUND = "RESOURCE_NOT_FOUND"
private const val CODE_POSTING_NOT_FOUND = "POSTING_NOT_FOUND"
private const val CODE_DUPLICATE_BOARD = "DUPLICATE_BOARD"
private const val CODE_LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
private const val CODE_PROFILE_INCOMPLETE = "PROFILE_INCOMPLETE"
private const val CODE_PARSING_FAILED = "PARSING_FAILED"
private const val CODE_BOARD_BLOCKED = "BOARD_BLOCKED"
private const val CODE_RATE_LIMITED = "RATE_LIMITED"
private const val CODE_LLM_UNAVAILABLE = "LLM_UNAVAILABLE"
private const val CODE_INTERNAL_ERROR = "INTERNAL_ERROR"
