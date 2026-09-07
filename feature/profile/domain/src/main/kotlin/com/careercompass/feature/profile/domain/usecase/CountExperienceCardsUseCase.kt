package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.repository.ExperienceRepository
import com.careercompass.core.model.experience.MAX_EXPERIENCE_CARDS
import javax.inject.Inject

/**
 * 등록한 경험 카드 수 — `GET /experiences` 를 커서 끝까지 따라가며 센다.
 *
 * ### 왜 세는 엔드포인트를 쓰지 않는가
 * API_SPEC v0.1 §3 에는 개수만 주는 엔드포인트가 없다. 목록에 총 개수 필드도 없어서(커서 페이징이다)
 * 세려면 따라가는 수밖에 없다. 대신 **상한이 낮다** — 카드는 최대 [MAX_EXPERIENCE_CARDS](30)개이고
 * 기본 페이지가 20이라 실제로는 두 번이면 끝난다. 서버에 개수 필드가 생기면 이 클래스 하나만 고친다.
 *
 * ### 루프를 상한으로 막는 이유
 * 서버가 마지막 페이지에서 커서를 비우지 못하면(또는 같은 커서를 되돌려주면) 이 루프는 끝나지 않는다.
 * 그 사고가 배터리와 데이터를 먹는 무한 요청이 되지 않게, **셀 수 있는 최대치에 닿으면 멈춘다** —
 * 상한을 넘는 개수는 화면이 그릴 일이 없으므로 잃는 것도 없다.
 */
public class CountExperienceCardsUseCase
    @Inject
    constructor(
        private val experienceRepository: ExperienceRepository,
    ) {
        public suspend operator fun invoke(): Result<Int> {
            var count = 0
            var cursor: String? = null
            do {
                val page =
                    experienceRepository
                        .getExperiences(type = null, cursor = cursor)
                        .getOrElse { cause -> return Result.failure(cause) }
                count += page.items.size
                cursor = page.nextCursor
            } while (cursor != null && count < MAX_EXPERIENCE_CARDS)
            return Result.success(count)
        }
    }
