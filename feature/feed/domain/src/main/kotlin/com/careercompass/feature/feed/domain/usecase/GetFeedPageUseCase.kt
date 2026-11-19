package com.careercompass.feature.feed.domain.usecase

import com.careercompass.core.domain.repository.PostingRepository
import com.careercompass.feature.feed.domain.model.FeedDeadlineFilter
import com.careercompass.feature.feed.domain.model.FeedPage
import com.careercompass.feature.feed.domain.model.FeedQuery
import java.time.Clock
import java.time.LocalDate
import javax.inject.Inject

/**
 * 피드 한 페이지를 조회한다 — 검색어·마감일까지 서버 파라미터([FeedQuery.toPostingQuery])로 싣고, 서버가 v0.3 을
 * 받기 전 배포에 대비해 같은 규칙을 클라이언트에서도 한 번 더 건다(#159).
 *
 * 클라이언트 규칙은 [FeedQuery.filterClientSide] 한 곳에 있다(오프라인 스냅샷 목록도 같은 함수를 쓴다).
 *
 * 1. 마감 지난 공고는 [FeedDeadlineFilter.IncludeExpired] 가 아니면 숨긴다(기능 스펙 F2-3 「마감」).
 * 2. [FeedDeadlineFilter.WithinWeek]·[FeedDeadlineFilter.WithinMonth] 는 오늘부터 7·30일 이내 마감만
 *    남긴다(당일 포함, 마감일 없는 공고 제외).
 * 3. [FeedDeadlineFilter.Range] 는 고른 범위(양 끝 포함) 안에 마감하는 공고만 남긴다 — 오늘과 무관하고,
 *    마감일 없는 공고는 제외한다.
 * 4. 검색어가 있으면 제목에 대소문자 무시로 포함되는 공고만 남긴다.
 *
 * 「오늘」은 주입된 [Clock] 기준이다. 필터로 항목이 줄어도 `nextCursor` 는 그대로 넘기므로
 * 빈 페이지가 끝을 뜻하지 않는다([FeedPage] 참고).
 */
public class GetFeedPageUseCase
    @Inject
    constructor(
        private val postingRepository: PostingRepository,
        private val clock: Clock,
    ) {
        public suspend operator fun invoke(
            query: FeedQuery,
            cursor: String? = null,
        ): Result<FeedPage> {
            val today = LocalDate.now(clock)
            val page =
                postingRepository
                    .getPostings(query.toPostingQuery(cursor, today))
                    .getOrElse { return Result.failure(it) }
            val postings = query.filterClientSide(page.items, today)
            return Result.success(FeedPage(postings = postings, nextCursor = page.nextCursor))
        }
    }
