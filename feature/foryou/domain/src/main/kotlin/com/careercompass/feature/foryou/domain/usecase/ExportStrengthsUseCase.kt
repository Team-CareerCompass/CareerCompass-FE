package com.careercompass.feature.foryou.domain.usecase

import com.careercompass.feature.foryou.domain.error.ForYouFailure
import com.careercompass.feature.foryou.domain.model.ExportFormat
import com.careercompass.feature.foryou.domain.model.ExportSection
import com.careercompass.feature.foryou.domain.model.StrengthExport
import com.careercompass.feature.foryou.domain.repository.ForYouRepository
import javax.inject.Inject

/**
 * 강점 데이터 내보내기 — `POST /export` (API_SPEC v0.1 §7).
 *
 * 묶음을 하나도 고르지 않은 요청은 **보내지 않는다.** 서버는 그것을 성공으로 받아 빈 문서를 돌려주고,
 * 사용자는 무엇이 잘못됐는지 모른 채 빈 화면을 본다.
 *
 * 고른 순서가 아니라 [ExportSection] 의 선언 순서로 보낸다 — 문서의 절 순서는 사용자가 체크한 순서가 아니라
 * 읽는 순서여야 하고, 그 순서를 화면마다 정하면 같은 선택이 다른 문서를 만든다. 중복도 여기서 걷는다.
 */
public class ExportStrengthsUseCase
    @Inject
    constructor(
        private val forYouRepository: ForYouRepository,
    ) {
        public suspend operator fun invoke(
            format: ExportFormat,
            sections: List<ExportSection>,
        ): Result<StrengthExport> {
            val ordered = ExportSection.entries.filter { it in sections }
            if (ordered.isEmpty()) return Result.failure(ForYouFailure.NoExportSection)
            return forYouRepository.export(format, ordered)
        }
    }
