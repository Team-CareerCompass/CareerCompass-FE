package com.careercompass.core.data.repoimpl.posting

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.data.mapper.PostingMapper
import com.careercompass.core.domain.repository.PostingRepository
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.core.model.posting.Posting
import com.careercompass.core.model.posting.PostingDetail
import com.careercompass.core.model.posting.PostingQuery
import com.careercompass.core.model.posting.PostingType
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.model.requireOk
import com.careercompass.core.network.service.PostingApiService
import javax.inject.Inject

internal class PostingRepositoryImpl
    @Inject
    constructor(
        private val postingApiService: PostingApiService,
    ) : PostingRepository {
        override suspend fun getPostings(query: PostingQuery): Result<CursorPage<Posting>> =
            runCatchingCancellable {
                val page =
                    postingApiService
                        .getPostings(
                            boardIds = query.boardIds.takeIf { it.isNotEmpty() },
                            types = query.types.takeIf { it.isNotEmpty() }?.map(PostingType::wireValue),
                            minScore = query.minScore,
                            unreadOnly = query.unreadOnly.takeIf { it },
                            sort = query.sort.wireValue,
                            cursor = query.cursor,
                            limit = query.limit,
                        ).requireData()
                CursorPage(items = page.postings.map(PostingMapper::toPosting), nextCursor = page.nextCursor?.takeIf(String::isNotBlank))
            }.mapDataFailure()

        override suspend fun getPostingDetail(id: Long): Result<PostingDetail> =
            runCatchingCancellable { PostingMapper.toDetail(postingApiService.getPostingDetail(id).requireData()) }.mapDataFailure()

        override suspend fun setBookmarked(
            id: Long,
            bookmarked: Boolean,
        ): Result<Unit> =
            runCatchingCancellable {
                if (bookmarked) postingApiService.addBookmark(id).requireOk() else postingApiService.removeBookmark(id).requireOk()
            }.mapDataFailure()

        override suspend fun markRead(id: Long): Result<Unit> =
            runCatchingCancellable { postingApiService.markRead(id).requireOk() }.mapDataFailure()
    }
