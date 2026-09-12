package com.careercompass.core.data.repoimpl.user

import com.careercompass.core.common.result.runCatchingCancellable
import com.careercompass.core.data.mapper.UserMapper
import com.careercompass.core.datastore.ProfileDataSource
import com.careercompass.core.domain.repository.UserProfileRepository
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.MAX_JOB_INTERESTS
import com.careercompass.core.model.user.MAX_PROFILE_TAGS
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.model.user.UserProfileUpdate
import com.careercompass.core.network.dto.JobInterestsRequestDto
import com.careercompass.core.network.dto.TagsRequestDto
import com.careercompass.core.network.dto.UserProfileDto
import com.careercompass.core.network.failure.mapDataFailure
import com.careercompass.core.network.model.requireData
import com.careercompass.core.network.model.requireOk
import com.careercompass.core.network.service.UserApiService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 프로필 캐시는 SESSION 스코프 저장소에 wire JSON 으로 영속한다 — 로그아웃·세션 정리 때 레지스트리가 함께 비우고
 * 프로세스 종료를 견딘다. 저장된 JSON 을 해석하지 못하면(스키마 변경) 캐시 없음으로 본다.
 */
@Singleton
internal class UserProfileRepositoryImpl
    @Inject
    constructor(
        private val userApiService: UserApiService,
        private val profileDataSource: ProfileDataSource,
        private val json: Json,
    ) : UserProfileRepository {
        override val profile: Flow<UserProfile?> =
            profileDataSource.profileJson.map { stored -> stored?.let(::decodeOrNull)?.let(UserMapper::toProfile) }

        override suspend fun refreshProfile(): Result<UserProfile> =
            runCatchingCancellable {
                val startedAt = profileDataSource.currentSessionGeneration()
                store(userApiService.getMe().requireData(), startedAt)
            }.mapDataFailure()

        override suspend fun lastKnownOnboardingDone(): Boolean? =
            profile.first()?.onboardingDone ?: profileDataSource.onboardingDoneHint.first()

        override suspend fun updateProfile(update: UserProfileUpdate): Result<UserProfile> {
            if (update.isEmpty) return profile.first()?.let { Result.success(it) } ?: refreshProfile()
            val startedAt = profileDataSource.currentSessionGeneration()
            return runCatchingCancellable {
                store(userApiService.updateMe(UserMapper.toUpdateRequest(update)).requireData(), startedAt)
            }.mapDataFailure()
        }

        override suspend fun replaceJobInterests(interests: List<JobInterest>): Result<Unit> {
            require(interests.size in 1..MAX_JOB_INTERESTS) { "job interests must be 1..$MAX_JOB_INTERESTS" }
            require(interests.map(JobInterest::code).distinct().size == interests.size) { "job interest codes must be unique" }
            val startedAt = profileDataSource.currentSessionGeneration()
            return runCatchingCancellable {
                userApiService.replaceJobInterests(JobInterestsRequestDto(interests.map(UserMapper::toJobInterestDto))).requireOk()
                updateStored(startedAt) { it.copy(jobInterests = interests.map(UserMapper::toJobInterestDto)) }
            }.mapDataFailure()
        }

        override suspend fun replaceTags(tags: List<String>): Result<Unit> {
            require(tags.size in 1..MAX_PROFILE_TAGS) { "tags must be 1..$MAX_PROFILE_TAGS" }
            require(tags.all(String::isNotBlank) && tags.distinct().size == tags.size) { "tags must be non-blank and unique" }
            val startedAt = profileDataSource.currentSessionGeneration()
            return runCatchingCancellable {
                userApiService.replaceTags(TagsRequestDto(tags)).requireOk()
                updateStored(startedAt) { it.copy(tags = tags) }
            }.mapDataFailure()
        }

        private suspend fun store(
            dto: UserProfileDto,
            startedAt: Long,
        ): UserProfile {
            persist(dto, startedAt)
            return UserMapper.toProfile(dto)
        }

        /** 저장된 프로필이 없으면 건너뛴다 — 부분 갱신만으로 프로필을 지어내지 않는다. */
        private suspend fun updateStored(
            startedAt: Long,
            transform: (UserProfileDto) -> UserProfileDto,
        ) {
            val current = profileDataSource.profileJson.first()?.let(::decodeOrNull) ?: return
            persist(transform(current), startedAt)
        }

        /**
         * 사용자 id 를 JSON 과 함께 남긴다 — 지문 등록 사용자와의 대조는 JSON 해석 없이 id 만 읽는다.
         *
         * [startedAt] 은 이 프로필을 서버에 청하기 시작한 시점의 세션 세대다. 응답을 기다리는 동안 세션이
         * 끝났으면 저장소는 비워진 채로 남는다(#348).
         */
        private suspend fun persist(
            dto: UserProfileDto,
            startedAt: Long,
        ) {
            profileDataSource.saveProfile(
                json = json.encodeToString(UserProfileDto.serializer(), dto),
                userId = dto.id,
                sessionGeneration = startedAt,
            )
        }

        private fun decodeOrNull(stored: String): UserProfileDto? =
            try {
                json.decodeFromString(UserProfileDto.serializer(), stored)
            } catch (exception: SerializationException) {
                null
            } catch (exception: IllegalArgumentException) {
                null
            }
    }
