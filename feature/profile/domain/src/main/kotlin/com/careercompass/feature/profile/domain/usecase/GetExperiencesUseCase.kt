package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.ExperienceRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.paging.CursorPage
import javax.inject.Inject

/**
 * 경험 카드 한 페이지 — `GET /experiences?type=&cursor=&limit=` (API_SPEC v0.1 §3).
 *
 * 정렬은 서버가 정한다(최신 등록순). 클라이언트가 다시 정렬하지 않는 이유는 커서 페이징이기 때문이다 —
 * 페이지마다 우리가 정렬하면 페이지 경계에서 순서가 뒤집힌다.
 *
 * @param type null 이면 전체. 유형 필터는 서버 파라미터라 필터를 바꾸면 커서도 처음으로 돌아간다.
 */
public class GetExperiencesUseCase
    @Inject
    constructor(
        private val experienceRepository: ExperienceRepository,
    ) {
        public suspend operator fun invoke(
            type: ExperienceType? = null,
            cursor: String? = null,
        ): Result<CursorPage<Experience>> = experienceRepository.getExperiences(type = type, cursor = cursor)
    }
