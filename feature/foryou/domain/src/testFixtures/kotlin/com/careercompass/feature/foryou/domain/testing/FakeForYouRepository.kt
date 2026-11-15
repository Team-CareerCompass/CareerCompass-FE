package com.careercompass.feature.foryou.domain.testing

import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.ForYouFeed
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import java.util.concurrent.CopyOnWriteArrayList

/** [ForYouRepository] fake 정본 — 훅이 없으면 「불릴 리 없는 호출」이라 실패시킨다. */
public class FakeForYouRepository(
    public var onGetForYouFeed: (suspend () -> Result<ForYouFeed>)? = null,
    public var onGetRoadmapComparison: (suspend (RoadmapCohort) -> Result<RoadmapComparison>)? = null,
    public var onExport: (suspend (ExportFormat, List<ExportSection>) -> Result<StrengthExport>)? = null,
) : ForYouRepository {
    public var feedCallCount: Int = 0
        private set
    public val roadmapCalls: CopyOnWriteArrayList<RoadmapCohort> = CopyOnWriteArrayList()
    public val exportCalls: CopyOnWriteArrayList<Pair<ExportFormat, List<ExportSection>>> = CopyOnWriteArrayList()

    override suspend fun getForYouFeed(): Result<ForYouFeed> {
        feedCallCount += 1
        return onGetForYouFeed?.invoke() ?: error("onGetForYouFeed 훅이 필요합니다")
    }

    override suspend fun getRoadmapComparison(cohort: RoadmapCohort): Result<RoadmapComparison> {
        roadmapCalls += cohort
        return onGetRoadmapComparison?.invoke(cohort) ?: error("onGetRoadmapComparison 훅이 필요합니다")
    }

    override suspend fun export(
        format: ExportFormat,
        sections: List<ExportSection>,
    ): Result<StrengthExport> {
        exportCalls += format to sections
        return onExport?.invoke(format, sections) ?: error("onExport 훅이 필요합니다")
    }
}
