package com.careercompass.feature.profile.presentation.experience

import com.careercompass.core.model.experience.ExperienceType

/**
 * 목록 위의 유형 필터 — 「전체」 하나와 [ExperienceType] 다섯이다.
 *
 * 유형이 다섯인 근거는 API_SPEC v0.1 §3 의 `type` 이다. 명세서 F1-3 이 「6가지」라고 쓰고 다섯을 나열한
 * 불일치는 `docs/spec/canon.md` 가 **5종**으로 판정했다([#199](https://github.com/Team-CareerCompass/CareerCompass-FE/issues/199)).
 *
 * @property type null 이면 「전체」다. 서버 파라미터로 그대로 나간다.
 */
public data class ExperienceTypeFilter(
    val type: ExperienceType?,
) {
    public companion object {
        /** 「전체」가 맨 앞이고 나머지는 [ExperienceType] 선언 순서다. */
        public val all: List<ExperienceTypeFilter> = listOf(ExperienceTypeFilter(null)) + ExperienceType.entries.map(::ExperienceTypeFilter)
    }
}

/**
 * 카드가 0개인 이유 — 사용자가 할 일이 다르다.
 *
 * 필터 때문에 비었으면 되돌릴 조건이 화면에 있고(필터를 「전체」로), 정말 없으면 만들러 가야 한다.
 * 두 경우에 같은 문장을 쓰면 필터를 걸어 둔 사용자는 자기 카드가 사라진 줄 안다.
 */
public enum class ExperienceEmptyReason {
    /** 아직 한 장도 등록하지 않았다. */
    NoCards,

    /** 고른 유형에 해당하는 카드가 없다. */
    FilteredOut,
}

/** 스낵바 한 줄로 끝나는 알림. */
public enum class ExperienceListMessage {
    /** 상한(30개)에 닿아 추가 진입점을 막았다. */
    LimitReached,

    /** 이어 읽기가 실패했다 — 이미 보이는 목록은 그대로 둔다. */
    LoadMoreFailed,
}

/** 화면이 [ExperienceListViewModel] 에 올려 보내는 사용자 조작. */
public sealed interface ExperienceListEvent {
    public data class FilterSelected(
        val filter: ExperienceTypeFilter,
    ) : ExperienceListEvent

    public data class CardClicked(
        val id: Long,
    ) : ExperienceListEvent

    public data object AddClicked : ExperienceListEvent

    public data object LoadMore : ExperienceListEvent

    public data object RetryClicked : ExperienceListEvent

    public data object BackClicked : ExperienceListEvent
}
