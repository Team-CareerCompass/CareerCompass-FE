package com.careercompass.feature.onboarding.presentation.flow

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.careercompass.core.ui.failure.FailureKind
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.sentence
import com.careercompass.feature.onboarding.presentation.R

/**
 * 흐름 실패 사유의 배너 문구 — §9 의 코드에서 온 사유는 **실패 표**([FailureKind], #204)를 읽는다(#236).
 *
 * 온보딩만 문구를 스스로 지으면 같은 사실을 화면마다 다르게 말하게 된다 — 연결 없음이 부품에서는
 * 「연결할 수 없어요 …」인데 온보딩에서는 「네트워크 연결을 확인한 뒤 …」였다. 배너는 한 덩어리 문장이라
 * 표의 제목과 본문을 [sentence] 로 잇는다.
 *
 * 파일 형식·크기는 §9 의 코드가 아니라 화면이 서버에 보내기 전에 거르는 검증이다. 표에 넣으면 다른 기능이
 * 쓸 수 없는 행이 하나 늘 뿐이라 온보딩 문자열로 남긴다(게시판 등록이 화면 고유 안내를 표에 넣지 않은 것과
 * 같은 판정, #212).
 */
@Composable
internal fun OnboardingFailureReason.toMessage(): String =
    when (this) {
        OnboardingFailureReason.Network -> FailureKind.NoConnection.display().sentence()

        OnboardingFailureReason.Timeout -> FailureKind.Timeout.display().sentence()

        is OnboardingFailureReason.LimitExceeded -> FailureKind.LimitExceeded.display(surface).sentence()

        OnboardingFailureReason.InvalidInput -> FailureKind.InvalidInput.display().sentence()

        OnboardingFailureReason.Maintenance -> FailureKind.ServiceUnavailable.display().sentence()

        OnboardingFailureReason.Server,
        OnboardingFailureReason.Unknown,
        -> FailureKind.Unexpected.display().sentence()

        OnboardingFailureReason.UnsupportedFile -> stringResource(com.careercompass.core.ui.R.string.core_ui_upload_unsupported_file)

        OnboardingFailureReason.FileTooLarge -> stringResource(com.careercompass.core.ui.R.string.core_ui_upload_file_too_large)
    }

/**
 * 문서 카드 상태 줄에 들어가는 짧은 문구 — 「%s · 재시도」 틀에 끼워진다.
 *
 * 표의 문장이 들어갈 자리가 아니다: 한 줄짜리 상태 라벨이라 두 문장을 이으면 카드가 넘친다. 그래서 온보딩
 * 문자열로 남긴다 — 단, 사유의 갈래는 배너와 같다(점검은 「서버 오류」로 접지 않는다).
 */
@Composable
internal fun OnboardingFailureReason.toShortMessage(): String =
    when (this) {
        OnboardingFailureReason.Network,
        OnboardingFailureReason.Timeout,
        -> stringResource(R.string.onboarding_upload_failed_network)

        is OnboardingFailureReason.LimitExceeded -> stringResource(R.string.onboarding_upload_failed_limit)

        OnboardingFailureReason.InvalidInput,
        OnboardingFailureReason.UnsupportedFile,
        OnboardingFailureReason.FileTooLarge,
        -> stringResource(R.string.onboarding_upload_failed_invalid)

        OnboardingFailureReason.Maintenance -> stringResource(R.string.onboarding_upload_failed_maintenance)

        OnboardingFailureReason.Server -> stringResource(R.string.onboarding_upload_failed_server)

        OnboardingFailureReason.Unknown -> stringResource(R.string.onboarding_upload_failed_unknown)
    }
