package com.careercompass.feature.profile.domain.usecase

import com.careercompass.core.domain.error.CoreDataFailure
import com.careercompass.core.domain.testing.FakeExperienceRepository
import com.careercompass.core.domain.testing.FakePastApplicationRepository
import com.careercompass.core.domain.testing.FakeUserProfileRepository
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.paging.CursorPage
import com.careercompass.feature.profile.domain.experienceCard
import com.careercompass.feature.profile.domain.userProfile
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class LoadProfileHomeUseCaseTest {
    @Test
    fun `프로필과 두 개수를 함께 돌려준다`() =
        runTest {
            val profile = userProfile()
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(initialProfile = profile),
                    experienceRepository = FakeExperienceRepository(initial = List(3) { experienceCard(it + 1L) }),
                    pastApplicationRepository = FakePastApplicationRepository(),
                )

            val home = useCase().getOrThrow()

            assertEquals(profile, home.profile)
            assertEquals(3, home.experienceCardCount)
            assertEquals(0, home.pastApplicationCount)
        }

    @Test
    fun `프로필 조회가 401 이면 화면이 그릴 것이 없으므로 실패로 올린다`() =
        runTest {
            val unauthorized = CoreDataFailure.Unauthorized(code = "AUTH_INVALID", cause = IllegalStateException("401"))
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(onRefreshProfile = { Result.failure(unauthorized) }),
                )

            val result = useCase()

            assertTrue(result.isFailure)
            assertEquals(unauthorized, result.exceptionOrNull())
        }

    @Test
    fun `프로필 조회가 503 이면 실패로 올린다`() =
        runTest {
            val maintenance = CoreDataFailure.ServiceUnavailable(code = "LLM_UNAVAILABLE", cause = IllegalStateException("503"))
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(onRefreshProfile = { Result.failure(maintenance) }),
                )

            assertEquals(maintenance, useCase().exceptionOrNull())
        }

    @Test
    fun `네트워크가 끊겨 프로필을 못 받으면 실패로 올린다`() =
        runTest {
            val offline = CoreDataFailure.NetworkUnavailable(transportCause = IOException("offline"))
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(onRefreshProfile = { Result.failure(offline) }),
                )

            assertEquals(offline, useCase().exceptionOrNull())
        }

    @Test
    fun `경험 카드 조회만 실패하면 그 개수만 모른 채 성공한다`() =
        runTest {
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(initialProfile = userProfile()),
                    experienceRepository =
                        FakeExperienceRepository(
                            onGetExperiences = { _, _, _ ->
                                Result.failure(
                                    CoreDataFailure.ServiceUnavailable(code = "LLM_UNAVAILABLE", cause = IllegalStateException("503")),
                                )
                            },
                        ),
                    pastApplicationRepository = FakePastApplicationRepository(),
                )

            val home = useCase().getOrThrow()

            assertNull(home.experienceCardCount)
            assertEquals(0, home.pastApplicationCount)
        }

    @Test
    fun `과거 지원서 조회만 실패하면 그 개수만 모른 채 성공한다`() =
        runTest {
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(initialProfile = userProfile()),
                    experienceRepository = FakeExperienceRepository(initial = listOf(experienceCard(1L))),
                    pastApplicationRepository =
                        FakePastApplicationRepository(
                            onGetPastApplications = { Result.failure(CoreDataFailure.NetworkUnavailable(IOException("offline"))) },
                        ),
                )

            val home = useCase().getOrThrow()

            assertEquals(1, home.experienceCardCount)
            assertNull(home.pastApplicationCount)
        }

    @Test
    fun `상한 초과 응답도 개수를 모르는 것으로만 접힌다`() =
        runTest {
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(initialProfile = userProfile()),
                    experienceRepository = FakeExperienceRepository(initial = listOf(experienceCard(1L))),
                    pastApplicationRepository =
                        FakePastApplicationRepository(
                            onGetPastApplications = {
                                Result.failure(CoreDataFailure.LimitExceeded(code = "LIMIT_EXCEEDED", cause = IllegalStateException("422")))
                            },
                        ),
                )

            assertNull(useCase().getOrThrow().pastApplicationCount)
        }

    @Test
    fun `커서를 끝까지 따라가 경험 카드를 센다`() =
        runTest {
            val pages =
                mapOf<String?, CursorPage<Experience>>(
                    null to CursorPage(items = List(20) { experienceCard(it + 1L) }, nextCursor = "c1"),
                    "c1" to CursorPage(items = List(4) { experienceCard(it + 21L) }, nextCursor = null),
                )
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(initialProfile = userProfile()),
                    experienceRepository =
                        FakeExperienceRepository(onGetExperiences = {
                            _,
                            cursor,
                            _,
                            ->
                            Result.success(pages.getValue(cursor))
                        }),
                    pastApplicationRepository = FakePastApplicationRepository(),
                )

            assertEquals(24, useCase().getOrThrow().experienceCardCount)
        }

    @Test
    fun `서버가 커서를 비우지 못해도 상한에서 멈춘다`() =
        runTest {
            var calls = 0
            val useCase =
                loadProfileHome(
                    userProfileRepository = FakeUserProfileRepository(initialProfile = userProfile()),
                    experienceRepository =
                        FakeExperienceRepository(
                            onGetExperiences = { _, _, _ ->
                                calls++
                                Result.success(CursorPage(items = List(10) { experienceCard(calls * 100L + it) }, nextCursor = "always"))
                            },
                        ),
                    pastApplicationRepository = FakePastApplicationRepository(),
                )

            assertEquals(30, useCase().getOrThrow().experienceCardCount)
            assertEquals(3, calls)
        }

    private fun loadProfileHome(
        userProfileRepository: FakeUserProfileRepository = FakeUserProfileRepository(initialProfile = userProfile()),
        experienceRepository: FakeExperienceRepository = FakeExperienceRepository(),
        pastApplicationRepository: FakePastApplicationRepository = FakePastApplicationRepository(),
    ): LoadProfileHomeUseCase =
        LoadProfileHomeUseCase(
            userProfileRepository = userProfileRepository,
            countExperienceCards = CountExperienceCardsUseCase(experienceRepository),
            countPastApplications = CountPastApplicationsUseCase(pastApplicationRepository),
        )
}
