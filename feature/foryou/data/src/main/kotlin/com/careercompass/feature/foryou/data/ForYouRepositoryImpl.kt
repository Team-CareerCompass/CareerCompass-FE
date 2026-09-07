package com.careercompass.feature.foryou.data

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.network.dto.ExportRequestDto
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.service.ForYouApiService
import com.careercompass.feature.foryou.data.mapper.ForYouMapper
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.ForYouFeed
import com.careercompass.feature.foryou.domain.model.RoadmapCohort
import com.careercompass.feature.foryou.domain.model.RoadmapComparison
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import javax.inject.Inject

internal class ForYouRepositoryImpl
    @Inject
    constructor(
        private val forYouApiService: ForYouApiService,
    ) : ForYouRepository {
        override suspend fun getForYouFeed(): Result<ForYouFeed> =
            runCatchingCancellable {
                ForYouMapper.toFeed(forYouApiService.getForYouFeed().requireData())
            }.mapDataFailure()

        override suspend fun getRoadmapComparison(cohort: RoadmapCohort): Result<RoadmapComparison> =
            runCatchingCancellable {
                ForYouMapper.toComparison(
                    dto = forYouApiService.getRoadmapComparison(cohort.wireValue).requireData(),
                    requested = cohort,
                )
            }.mapDataFailure()

        override suspend fun export(
            format: ExportFormat,
            sections: List<ExportSection>,
        ): Result<StrengthExport> {
            require(sections.isNotEmpty()) { "sections must not be empty" }
            return runCatchingCancellable {
                ForYouMapper.toExport(
                    dto =
                        forYouApiService
                            .export(ExportRequestDto(format = format.wireValue, sections = sections.map { it.wireValue }))
                            .requireData(),
                    requested = format,
                )
            }.mapDataFailure()
        }
    }
