package com.careercompass.feature.foryou.domain.repository

import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.ForYouFeed
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.StrengthExport

/** API_SPEC v0.1 §7 의 도메인 계약 — For You 추천 · 커리어 로드맵 · 강점 Export. */
public interface ForYouRepository {
    /** `GET /feed/for-you`. */
    public suspend fun getForYouFeed(): Result<ForYouFeed>

    /** `GET /roadmap/compare?cohort=...`. */
    public suspend fun getRoadmapComparison(cohort: RoadmapCohort): Result<RoadmapComparison>

    /** `POST /export`. */
    public suspend fun export(
        format: ExportFormat,
        sections: List<ExportSection>,
    ): Result<StrengthExport>
}
