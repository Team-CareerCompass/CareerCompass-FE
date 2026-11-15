package com.careercompass.feature.onboarding.presentation

import androidx.compose.runtime.Immutable

/** 경험 카드 등록 상한 — 기능 스펙 F1-3 (`core` 의 `MAX_EXPERIENCE_CARDS` 와 같은 값). */
public const val ONBOARDING_MAX_EXPERIENCE_CARDS: Int = 30

/** A localized, stable filter shown above the onboarding experience list. */
@Immutable
public data class OnboardingExperienceType(
    public val id: String,
    public val label: String,
) {
    init {
        require(id.isNotBlank()) { "Experience type id must not be blank" }
        require(label.isNotBlank()) { "Experience type label must not be blank" }
    }
}

/** Display-ready data for one experience card. */
public data class OnboardingExperience(
    public val id: String,
    public val typeId: String,
    public val title: String,
    public val period: String,
    public val role: String,
    public val tags: List<String>,
) {
    init {
        require(id.isNotBlank()) { "Experience id must not be blank" }
        require(typeId.isNotBlank()) { "Experience type id must not be blank" }
        require(title.isNotBlank()) { "Experience title must not be blank" }
        require(period.isNotBlank()) { "Experience period must not be blank" }
        require(role.isNotBlank()) { "Experience role must not be blank" }
        require(tags.all(String::isNotBlank)) { "Experience tags must not be blank" }
        require(tags.distinct().size == tags.size) { "Experience tags must be unique" }
    }
}

/** Immutable rendering state for the third onboarding step. */
public data class OnboardingStep3UiState(
    public val experienceTypes: List<OnboardingExperienceType>,
    public val selectedExperienceTypeId: String,
    public val experiences: List<OnboardingExperience> = emptyList(),
    public val isInputEnabled: Boolean = true,
    public val isListLoading: Boolean = false,
    public val currentStep: Int = 3,
    public val totalSteps: Int = 4,
) {
    init {
        require(totalSteps > 0) { "totalSteps must be positive" }
        require(currentStep in 1..totalSteps) { "currentStep must be within 1..totalSteps" }
        require(experienceTypes.isNotEmpty()) { "Experience types must not be empty" }
        require(experienceTypes.map(OnboardingExperienceType::id).distinct().size == experienceTypes.size) {
            "Experience type ids must be unique"
        }
        require(experienceTypes.map(OnboardingExperienceType::label).distinct().size == experienceTypes.size) {
            "Experience type labels must be unique"
        }
        require(experienceTypes.any { it.id == selectedExperienceTypeId }) {
            "Selected experience type must be present in experienceTypes"
        }
        require(experiences.map(OnboardingExperience::id).distinct().size == experiences.size) {
            "Experience ids must be unique"
        }
        require(experiences.all { experience -> experienceTypes.any { it.id == experience.typeId } }) {
            "Every experience type must be present in experienceTypes"
        }
    }

    /** Experiences matching the currently selected filter. */
    public val visibleExperiences: List<OnboardingExperience>
        get() = experiences.filter { it.typeId == selectedExperienceTypeId }

    /**
     * Whether onboarding can advance. Experiences are optional (spec F1-2), so only the input
     * lock gates the next action; the screen nudges users to add at least one for better analysis.
     */
    public val isNextEnabled: Boolean
        get() = isInputEnabled

    /**
     * 상한(F1-3, 30개)에 닿으면 추가만 막는다 — 하나를 지우면 다시 열린다.
     *
     * 목록을 읽는 중에도 막는다. 아직 받지 못한 목록으로 상한을 판정하면 이미 30개인 계정에서 시트가
     * 열리고, 사용자가 다 쓴 뒤에야 서버가 거절한다(#356).
     */
    public val isAddEnabled: Boolean
        get() = isInputEnabled && !isListLoading && experiences.size < ONBOARDING_MAX_EXPERIENCE_CARDS
}

/** User intentions emitted by [OnboardingStep3Content]. */
public sealed interface OnboardingStep3Event {
    public data class ExperienceTypeSelected(
        public val typeId: String,
    ) : OnboardingStep3Event

    /** 카드 본문 탭 — 그 카드를 수정한다(F1-3). */
    public data class ExperienceSelected(
        public val experienceId: String,
    ) : OnboardingStep3Event

    /** 카드 본문과 분리된 삭제 영역 탭 — 확인 다이얼로그를 연다. */
    public data class ExperienceDeleteClicked(
        public val experienceId: String,
    ) : OnboardingStep3Event

    public data object AddExperienceClicked : OnboardingStep3Event

    public data object BackClicked : OnboardingStep3Event

    public data object NextClicked : OnboardingStep3Event
}
