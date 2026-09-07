package com.careercompass.feature.onboarding.presentation

import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.component.ExperienceEditorState
import com.careercompass.core.ui.component.ExperienceQuickAddSheet
import com.careercompass.core.ui.theme.CareerCompassTheme

@PreviewTest
@Preview(name = "Experience quick add project", widthDp = 360, heightDp = 800)
@Composable
public fun ExperienceQuickAddProjectPreview() {
    ExperienceQuickAddPreviewHost(
        state =
            ExperienceEditorState(
                type = ExperienceType.Project,
                title = "CareerCompass - 졸업 프로젝트",
                startDate = "2025.09",
                primary = "안드로이드 개발",
                secondary = "공고 자동 분석 + AI 자소서 생성 서비스",
            ),
    )
}

/** 「자세히」를 펼친 프로젝트 상세(F1-3) — 기술 태그 칩과 링크가 시트 안에 어떻게 앉는지 골든으로 잡는다. */
@PreviewTest
@Preview(name = "Experience quick add project detail", widthDp = 360, heightDp = 900)
@Composable
public fun ExperienceQuickAddProjectDetailPreview() {
    ExperienceQuickAddPreviewHost(
        state =
            ExperienceEditorState(
                type = ExperienceType.Project,
                title = "CareerCompass - 졸업 프로젝트",
                startDate = "2025.09",
                primary = "안드로이드 개발",
                secondary = "공고 자동 분석 + AI 자소서 생성 서비스",
                techs = listOf("Kotlin", "Compose", "Hilt"),
                techInput = "Retrofit",
                link = "https://github.com/Team-CamBridge/CareerCompass-FE",
                isDetailExpanded = true,
            ),
    )
}

@PreviewTest
@Preview(name = "Experience quick add intern errors", widthDp = 360, heightDp = 800)
@Composable
public fun ExperienceQuickAddInternErrorPreview() {
    ExperienceQuickAddPreviewHost(
        state =
            ExperienceEditorState(
                type = ExperienceType.Intern,
                title = "카카오 인턴",
                startDateError = ProfileFieldViolation.Required,
                primaryError = ProfileFieldViolation.Required,
                secondaryError = ProfileFieldViolation.Required,
            ),
    )
}

@PreviewTest
@Preview(name = "Experience quick add certificate submitting", widthDp = 360, heightDp = 800)
@Composable
public fun ExperienceQuickAddCertificateSubmittingPreview() {
    ExperienceQuickAddPreviewHost(
        state =
            ExperienceEditorState(
                type = ExperienceType.Certificate,
                title = "정보처리기사",
                startDate = "2025.06",
                primary = "한국산업인력공단",
                isSubmitting = true,
            ),
    )
}

@PreviewTest
@Preview(name = "Experience edit intern", widthDp = 360, heightDp = 800)
@Composable
public fun ExperienceEditInternPreview() {
    ExperienceQuickAddPreviewHost(
        state =
            ExperienceEditorState(
                experienceId = 3L,
                type = ExperienceType.Intern,
                title = "카카오 인턴",
                startDate = "2025.01",
                endDate = "2025.02",
                primary = "카카오",
                secondary = "안드로이드 개발",
                // 값이 있는 카드는 「자세히」를 펼친 채로 연다 — 접혀 있으면 지워졌다고 읽힌다.
                detail = "공고 피드 화면 개발",
                isDetailExpanded = true,
            ),
    )
}

@Composable
private fun ExperienceQuickAddPreviewHost(state: ExperienceEditorState) {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.surface) {
            ExperienceQuickAddSheet(state = state, onEvent = {})
        }
    }
}
