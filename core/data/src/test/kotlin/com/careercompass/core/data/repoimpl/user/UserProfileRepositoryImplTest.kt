package com.careercompass.core.data.repoimpl.user

import com.careercompass.core.data.support.FakeLocalStoreRegistry
import com.careercompass.core.datastore.ProfileDataSource
import com.careercompass.core.datastore.StoreScope
import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfileUpdate
import com.careercompass.core.network.dto.JobInterestDto
import com.careercompass.core.network.dto.JobInterestsRequestDto
import com.careercompass.core.network.dto.TagsRequestDto
import com.careercompass.core.network.dto.UpdateProfileRequestDto
import com.careercompass.core.network.dto.UserProfileDto
import com.careercompass.core.network.model.ApiException
import com.careercompass.core.network.model.BaseResponse
import com.careercompass.core.network.service.UserApiService
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileRepositoryImplTest {
    private class FakeUserApi : UserApiService {
        var profile = UserProfileDto(1, "정일혁", "건국대학교", "컴퓨터공학부", 3.87, 2027, listOf(JobInterestDto("backend", 1)), listOf("AI"), true, 78)
        var getMeThrows: Throwable? = null
        var onGetMe: suspend () -> Unit = {}
        val updates = mutableListOf<UpdateProfileRequestDto>()
        val jobInterests = mutableListOf<JobInterestsRequestDto>()
        val tags = mutableListOf<TagsRequestDto>()

        override suspend fun getMe(): BaseResponse<UserProfileDto> {
            onGetMe()
            getMeThrows?.let { throw it }
            return BaseResponse(ok = true, data = profile)
        }

        override suspend fun updateMe(body: UpdateProfileRequestDto): BaseResponse<UserProfileDto> {
            updates += body
            profile = profile.copy(name = body.name ?: profile.name, gpa = body.gpa ?: profile.gpa)
            return BaseResponse(ok = true, data = profile)
        }

        override suspend fun replaceJobInterests(body: JobInterestsRequestDto): BaseResponse<Unit> {
            jobInterests += body
            return BaseResponse(ok = true)
        }

        override suspend fun replaceTags(body: TagsRequestDto): BaseResponse<Unit> {
            tags += body
            return BaseResponse(ok = true)
        }
    }

    private val api = FakeUserApi()
    private val registry = FakeLocalStoreRegistry()
    private val profileDataSource = ProfileDataSource(registry.store("Profile", StoreScope.SESSION), registry)
    private val repository = UserProfileRepositoryImpl(api, profileDataSource, Json { ignoreUnknownKeys = true })

    @Test
    fun `조회 성공은 프로필을 영속하고 세션 정리 때 함께 비운다`() =
        runTest {
            assertNull(repository.profile.first())
            assertNull(profileDataSource.userId.first())

            repository.refreshProfile().getOrThrow()

            assertEquals("정일혁", repository.profile.first()?.name)
            assertEquals(1L, profileDataSource.userId.first())
            assertEquals(true, repository.lastKnownOnboardingDone())
            registry.clearScope(StoreScope.SESSION)
            assertNull(repository.profile.first())
            assertNull(profileDataSource.userId.first())
        }

    /**
     * #348 — 마이 탭에서 조회 중 로그아웃하면 정리와 화면 취소 사이에 응답이 도착한다. 세대 가드가 없으면 그
     * 응답이 앞 계정의 JSON·user_id 를 비워진 저장소에 다시 써, 다음 계정의 지문 게이트와 온보딩 판정이 그 값으로 열린다.
     */
    @Test
    fun `clearScope 뒤 도착한 persist 가 저장소를 비운 채로 둔다`() =
        runTest {
            val response = CompletableDeferred<Unit>()
            api.onGetMe = { response.await() }
            val refresh = async { repository.refreshProfile() }
            runCurrent()

            registry.clearScope(StoreScope.SESSION)
            response.complete(Unit)
            refresh.await().getOrThrow()

            assertNull(repository.profile.first())
            assertNull(profileDataSource.userId.first())
        }

    @Test
    fun `빈 수정은 요청 없이 현재 프로필을 돌려준다`() =
        runTest {
            repository.refreshProfile().getOrThrow()

            val profile = repository.updateProfile(UserProfileUpdate()).getOrThrow()

            assertEquals("정일혁", profile.name)
            assertTrue(api.updates.isEmpty())
        }

    @Test
    fun `부분 수정과 직무·태그 교체는 영속 프로필에 반영된다`() =
        runTest {
            repository.refreshProfile().getOrThrow()

            repository.updateProfile(UserProfileUpdate(name = "일혁")).getOrThrow()
            repository.replaceJobInterests(listOf(JobInterest("frontend", 1), JobInterest("data", 2))).getOrThrow()
            repository.replaceTags(listOf("AI", "환경")).getOrThrow()

            val cached = requireNotNull(repository.profile.first())
            assertEquals("일혁", cached.name)
            assertEquals(listOf("frontend", "data"), cached.jobInterests.map { it.code })
            assertEquals(listOf("AI", "환경"), cached.tags)
            assertEquals(UpdateProfileRequestDto(name = "일혁"), api.updates.single())
        }

    @Test
    fun `저장된 프로필이 없으면 직무·태그 교체는 서버만 갱신한다`() =
        runTest {
            repository.replaceTags(listOf("AI")).getOrThrow()

            assertNull(repository.profile.first())
            assertEquals(listOf("AI"), api.tags.single().tags)
        }

    @Test
    fun `마지막 완료 여부는 프로필이 우선이고 없으면 로그인 힌트를 쓴다`() =
        runTest {
            assertNull(repository.lastKnownOnboardingDone())

            profileDataSource.setOnboardingDoneHint(true)
            assertEquals(true, repository.lastKnownOnboardingDone())

            api.profile = api.profile.copy(onboardingDone = false)
            repository.refreshProfile().getOrThrow()
            assertEquals(false, repository.lastKnownOnboardingDone())
        }

    @Test
    fun `해석할 수 없는 저장 프로필은 캐시 없음으로 본다`() =
        runTest {
            profileDataSource.saveProfile("""{"id":"not-a-number"}""", userId = 1L)

            assertNull(repository.profile.first())
            assertNull(repository.lastKnownOnboardingDone())
        }

    @Test
    fun `직무는 1~3개, 태그는 1~5개 범위를 벗어나면 요청 전에 거부한다`() =
        runTest {
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { repository.replaceJobInterests(emptyList()) }
            }
            assertThrows(IllegalArgumentException::class.java) {
                kotlinx.coroutines.runBlocking { repository.replaceTags(List(6) { "tag$it" }) }
            }
            assertTrue(api.jobInterests.isEmpty() && api.tags.isEmpty())
        }

    @Test
    fun `서버 실패는 도메인 사유로 옮긴다`() =
        runTest {
            api.getMeThrows = ApiException("AUTH_REQUIRED", null, "인증 필요", status = 401)

            assertTrue(repository.refreshProfile().exceptionOrNull() is CoreDataFailure.Unauthorized)
        }
}
