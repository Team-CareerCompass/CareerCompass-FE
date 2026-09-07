package com.careercompass.core.data.repoimpl.board

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.data.mapper.BoardMapper
import com.careercompass.core.domain.repository.BoardRepository
import com.careercompass.core.model.board.Board
import com.careercompass.core.model.board.BoardDetection
import com.careercompass.core.model.board.BoardRegistration
import com.careercompass.core.model.board.BoardUpdate
import com.careercompass.core.network.dto.BoardDetectRequestDto
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.model.requireOk
import com.careercompass.core.network.service.BoardApiService
import com.careercompass.core.network.service.BoardDetectApiService
import javax.inject.Inject

internal class BoardRepositoryImpl
    @Inject
    constructor(
        private val boardApiService: BoardApiService,
        private val boardDetectApiService: BoardDetectApiService,
    ) : BoardRepository {
        override suspend fun detect(url: String): Result<BoardDetection> {
            require(url.isNotBlank()) { "url must not be blank" }
            return runCatchingCancellable {
                BoardMapper.toDetection(boardDetectApiService.detect(BoardDetectRequestDto(url.trim())).requireData())
            }.mapDataFailure()
        }

        override suspend fun register(registration: BoardRegistration): Result<Board> =
            runCatchingCancellable {
                BoardMapper.toBoard(boardApiService.register(BoardMapper.toRegisterRequest(registration)).requireData())
            }.mapDataFailure()

        override suspend fun getBoards(): Result<List<Board>> =
            runCatchingCancellable {
                boardApiService
                    .getBoards()
                    .requireData()
                    .boards
                    .map(BoardMapper::toBoard)
            }.mapDataFailure()

        override suspend fun update(
            id: Long,
            update: BoardUpdate,
        ): Result<Board> {
            require(!update.isEmpty) { "update must change at least one field" }
            return runCatchingCancellable {
                BoardMapper.toBoard(boardApiService.update(id, BoardMapper.toUpdateRequest(update)).requireData())
            }.mapDataFailure()
        }

        override suspend fun delete(id: Long): Result<Unit> =
            runCatchingCancellable { boardApiService.delete(id).requireOk() }.mapDataFailure()

        override suspend fun retry(id: Long): Result<Unit> =
            runCatchingCancellable { boardApiService.retry(id).requireOk() }.mapDataFailure()
    }
