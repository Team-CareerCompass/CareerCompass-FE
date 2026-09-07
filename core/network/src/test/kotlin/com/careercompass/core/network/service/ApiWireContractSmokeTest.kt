package com.careercompass.core.network.service

import com.careercompass.core.network.dto.BiometricRegisterRequestDto
import com.careercompass.core.network.dto.BoardDetectRequestDto
import com.careercompass.core.network.dto.BoardRegisterRequestDto
import com.careercompass.core.network.dto.BoardUpdateRequestDto
import com.careercompass.core.network.dto.CreateApplicationRequestDto
import com.careercompass.core.network.dto.ExperienceRequestDto
import com.careercompass.core.network.dto.ExportRequestDto
import com.careercompass.core.network.dto.JobInterestDto
import com.careercompass.core.network.dto.JobInterestsRequestDto
import com.careercompass.core.network.dto.LogoutRequestDto
import com.careercompass.core.network.dto.RefreshRequestDto
import com.careercompass.core.network.dto.RegenerateItemRequestDto
import com.careercompass.core.network.dto.SocialLoginRequestDto
import com.careercompass.core.network.dto.TagsRequestDto
import com.careercompass.core.network.dto.UpdateApplicationResultRequestDto
import com.careercompass.core.network.dto.UpdateItemAnswerRequestDto
import com.careercompass.core.network.dto.UpdateItemCategoryRequestDto
import com.careercompass.core.network.dto.UpdateProfileRequestDto
import com.careercompass.core.network.sse.asServerSentEvents
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.junit.AfterClass
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.testcontainers.DockerClientFactory
import org.testcontainers.mockserver.MockServerContainer
import org.testcontainers.utility.DockerImageName
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory

/**
 * Retrofit 선언과 kotlinx-serialization DTO 를 실제 HTTP 소켓 경계까지 통과시키는 smoke 계약.
 *
 * 단순 fixture decode 테스트와 달리 method/path/header/request body 가 하나라도 바뀌면 MockServer 의
 * strict matcher 가 응답하지 않아 실패한다. 실제 운영 서버를 호출하지 않으므로 계정·secret 은 필요 없다.
 * 일반 unit-test 실행에서는 환경 플래그로 건너뛰고, 전용 Actions workflow 가 명시적으로 활성화한다.
 * 전용 workflow 에서 Docker 런타임을 찾지 못하면 skip 하지 않고 즉시 실패한다.
 *
 * 엔드포인트가 늘면 여기에 케이스를 추가한다 — API_SPEC v0.1 의 각 도메인이 대상이다.
 */
class ApiWireContractSmokeTest {
    private lateinit var authService: AuthApiService
    private lateinit var tokenService: TokenApiService
    private lateinit var userService: UserApiService
    private lateinit var experienceService: ExperienceApiService
    private lateinit var pastApplicationService: PastApplicationApiService
    private lateinit var postingService: PostingApiService
    private lateinit var boardService: BoardApiService
    private lateinit var boardDetectService: BoardDetectApiService
    private lateinit var applicationService: ApplicationApiService
    private lateinit var applicationStreamService: ApplicationStreamApiService
    private lateinit var forYouService: ForYouApiService

    @Before
    fun setUp() {
        controlPut("/mockserver/reset")

        val okHttpClient =
            OkHttpClient
                .Builder()
                .addInterceptor { chain ->
                    chain.proceed(
                        chain
                            .request()
                            .newBuilder()
                            .header("Authorization", "Bearer contract-token")
                            .build(),
                    )
                }.build()
        val publicRetrofit =
            Retrofit
                .Builder()
                .baseUrl("${mockServer.endpoint}/api/v1/")
                .addConverterFactory(
                    wireJson.asConverterFactory("application/json".toMediaType()),
                ).client(okHttpClient)
                .build()

        authService = publicRetrofit.create(AuthApiService::class.java)
        tokenService = publicRetrofit.create(TokenApiService::class.java)
        userService = publicRetrofit.create(UserApiService::class.java)
        experienceService = publicRetrofit.create(ExperienceApiService::class.java)
        pastApplicationService = publicRetrofit.create(PastApplicationApiService::class.java)
        postingService = publicRetrofit.create(PostingApiService::class.java)
        boardService = publicRetrofit.create(BoardApiService::class.java)
        boardDetectService = publicRetrofit.create(BoardDetectApiService::class.java)
        applicationService = publicRetrofit.create(ApplicationApiService::class.java)
        applicationStreamService = publicRetrofit.create(ApplicationStreamApiService::class.java)
        forYouService = publicRetrofit.create(ForYouApiService::class.java)
    }

    @Test
    fun `Kakao social login preserves HTTP route, strict request JSON, and response schema`() =
        runTest {
            assertSocialLoginContract(
                provider = SocialLoginProvider.Kakao,
                expectedPath = "/api/v1/auth/social/kakao",
            )
        }

    @Test
    fun `Google social login preserves HTTP route, strict request JSON, and response schema`() =
        runTest {
            assertSocialLoginContract(
                provider = SocialLoginProvider.Google,
                expectedPath = "/api/v1/auth/social/google",
            )
        }

    // ── §1 인증 ──

    @Test
    fun `token refresh preserves route, strict body, and token schema`() =
        runTest {
            installExpectation(
                method = "POST",
                path = "/api/v1/auth/refresh",
                requestBody = wireJson.parseToJsonElement("""{"refreshToken":"refresh-1"}""").jsonObject,
                responseBody = """{"ok":true,"data":{"accessToken":"access-2","refreshToken":"refresh-2","expiresIn":3600}}""",
            )

            val data = requireNotNull(tokenService.refresh(RefreshRequestDto("refresh-1")).data)

            assertEquals("access-2", data.accessToken)
            assertEquals("refresh-2", data.refreshToken)
            assertEquals(3600L, data.expiresIn)
            assertExactlyOneRecordedRequest("POST", "/api/v1/auth/refresh")
        }

    @Test
    fun `logout and biometric register preserve routes and strict bodies`() =
        runTest {
            installExpectation(
                method = "POST",
                path = "/api/v1/auth/logout",
                requestBody = wireJson.parseToJsonElement("""{"refreshToken":"refresh-1"}""").jsonObject,
                responseBody = """{"ok":true}""",
            )
            installExpectation(
                method = "POST",
                path = "/api/v1/auth/biometric/register",
                requestBody = wireJson.parseToJsonElement("""{"deviceId":"device-uuid"}""").jsonObject,
                responseBody = """{"ok":true}""",
            )

            assertEquals(true, authService.logout(LogoutRequestDto("refresh-1")).ok)
            assertEquals(true, authService.registerBiometric(BiometricRegisterRequestDto("device-uuid")).ok)
            assertExactlyOneRecordedRequest("POST", "/api/v1/auth/logout")
            assertExactlyOneRecordedRequest("POST", "/api/v1/auth/biometric/register")
        }

    // ── §2 사용자 프로필 ──

    @Test
    fun `user profile read and partial update preserve routes and schema`() =
        runTest {
            val profile =
                """
                {"ok":true,"data":{"id":1,"name":"정일혁","school":"건국대학교","department":"컴퓨터공학부","gpa":3.87,
                 "gradYear":2027,"jobInterests":[{"code":"backend","priority":1}],"tags":["AI","스타트업"],
                 "onboardingDone":true,"completion":78}}
                """.trimIndent()
            installExpectation(method = "GET", path = "/api/v1/users/me", responseBody = profile)
            installExpectation(
                method = "PATCH",
                path = "/api/v1/users/me",
                requestBody = wireJson.parseToJsonElement("""{"name":"정일혁","gpa":3.87}""").jsonObject,
                responseBody = profile,
            )

            val me = requireNotNull(userService.getMe().data)
            val updated = requireNotNull(userService.updateMe(UpdateProfileRequestDto(name = "정일혁", gpa = 3.87)).data)

            assertEquals(1L, me.id)
            assertEquals(listOf("AI", "스타트업"), me.tags)
            assertEquals("backend", me.jobInterests.single().code)
            assertEquals(78, updated.completion)
            assertExactlyOneRecordedRequest("GET", "/api/v1/users/me")
            assertExactlyOneRecordedRequest("PATCH", "/api/v1/users/me")
        }

    @Test
    fun `job interests and tags replacement preserve routes and strict bodies`() =
        runTest {
            installExpectation(
                method = "PUT",
                path = "/api/v1/users/me/job-interests",
                requestBody =
                    wireJson
                        .parseToJsonElement("""{"interests":[{"code":"backend","priority":1},{"code":"frontend","priority":2}]}""")
                        .jsonObject,
                responseBody = """{"ok":true}""",
            )
            installExpectation(
                method = "PUT",
                path = "/api/v1/users/me/tags",
                requestBody = wireJson.parseToJsonElement("""{"tags":["AI","스타트업","환경"]}""").jsonObject,
                responseBody = """{"ok":true}""",
            )

            assertEquals(
                true,
                userService
                    .replaceJobInterests(
                        JobInterestsRequestDto(listOf(JobInterestDto("backend", 1), JobInterestDto("frontend", 2))),
                    ).ok,
            )
            assertEquals(true, userService.replaceTags(TagsRequestDto(listOf("AI", "스타트업", "환경"))).ok)
            assertExactlyOneRecordedRequest("PUT", "/api/v1/users/me/job-interests")
            assertExactlyOneRecordedRequest("PUT", "/api/v1/users/me/tags")
        }

    // ── §3 경험 카드 ──

    @Test
    fun `experience list, create, update, and delete preserve routes and schema`() =
        runTest {
            val experience =
                """
                {"id":7,"type":"project","title":"CareerCompass","startDate":"2025-09-01","endDate":null,
                 "data":{"role":"프론트엔드","techs":["Android","Kotlin"],"summary":"공고 분석 앱","link":"https://github.com/x"}}
                """.trimIndent()
            installExpectation(
                method = "GET",
                path = "/api/v1/experiences",
                requestQueryParameters = mapOf("type" to "project", "limit" to "20"),
                responseBody = """{"ok":true,"data":{"experiences":[$experience],"nextCursor":null}}""",
            )
            val requestBody =
                wireJson
                    .parseToJsonElement(
                        """{"type":"project","title":"CareerCompass","startDate":"2025-09-01","data":{"role":"프론트엔드","techs":["Android","Kotlin"]}}""",
                    ).jsonObject
            installExpectation(
                method = "POST",
                path = "/api/v1/experiences",
                requestBody = requestBody,
                responseBody = """{"ok":true,"data":$experience}""",
            )
            installExpectation(
                method = "PATCH",
                path = "/api/v1/experiences/7",
                requestBody = requestBody,
                responseBody = """{"ok":true,"data":$experience}""",
            )
            installExpectation(method = "DELETE", path = "/api/v1/experiences/7", responseBody = """{"ok":true}""")

            val page = requireNotNull(experienceService.getExperiences(type = "project", cursor = null, limit = 20).data)
            val request =
                ExperienceRequestDto(
                    type = "project",
                    title = "CareerCompass",
                    startDate = "2025-09-01",
                    data = requestBody.getValue("data").jsonObject,
                )

            assertEquals(7L, page.experiences.single().id)
            assertEquals(
                "프론트엔드",
                page.experiences
                    .single()
                    .data
                    .getValue("role")
                    .jsonPrimitive.content,
            )
            assertEquals(7L, requireNotNull(experienceService.createExperience(request).data).id)
            assertEquals("CareerCompass", requireNotNull(experienceService.updateExperience(7, request).data).title)
            assertEquals(true, experienceService.deleteExperience(7).ok)
            assertExactlyOneRecordedRequest("GET", "/api/v1/experiences")
            assertExactlyOneRecordedRequest("POST", "/api/v1/experiences")
            assertExactlyOneRecordedRequest("PATCH", "/api/v1/experiences/7")
            assertExactlyOneRecordedRequest("DELETE", "/api/v1/experiences/7")
        }

    // ── §4 과거 지원서 ──

    @Test
    fun `past application upload sends multipart file and label parts`() =
        runTest {
            val application =
                """
                {"id":7,"label":"2024 카카오 인턴 자소서","filePath":"s3://bucket/7.pdf",
                 "items":[{"id":1,"category":"motivation","content":"...","confident":true},{"id":2,"category":"other","content":"...","confident":false}]}
                """.trimIndent()
            installExpectation(
                method = "POST",
                path = "/api/v1/past-applications/upload",
                responseBody = """{"ok":true,"data":$application}""",
            )
            installExpectation(
                method = "GET",
                path = "/api/v1/past-applications",
                responseBody = """{"ok":true,"data":{"applications":[$application]}}""",
            )
            installExpectation(
                method = "PATCH",
                path = "/api/v1/past-applications/7/items/2",
                requestBody = wireJson.parseToJsonElement("""{"category":"aspiration"}""").jsonObject,
                responseBody = """{"ok":true,"data":{"id":2,"category":"aspiration","content":"...","confident":true}}""",
            )
            installExpectation(method = "DELETE", path = "/api/v1/past-applications/7", responseBody = """{"ok":true}""")

            val filePart =
                MultipartBody.Part.createFormData(
                    "file",
                    "resume.pdf",
                    "%PDF-1.4".toRequestBody("application/pdf".toMediaType()),
                )
            val uploaded =
                requireNotNull(
                    pastApplicationService
                        .upload(
                            file = filePart,
                            label = "2024 카카오 인턴 자소서".toRequestBody("text/plain".toMediaType()),
                        ).data,
                )

            assertEquals(7L, uploaded.id)
            assertEquals(2, uploaded.items.size)
            assertEquals(1, requireNotNull(pastApplicationService.getPastApplications().data).applications.size)
            assertEquals(
                "aspiration",
                requireNotNull(pastApplicationService.updateItemCategory(7, 2, UpdateItemCategoryRequestDto("aspiration")).data).category,
            )
            assertEquals(true, pastApplicationService.delete(7).ok)

            val upload = recordedRequests("POST", "/api/v1/past-applications/upload").single().jsonObject
            val contentType = upload.headerValues("Content-Type").joinToString()
            assertTrue("multipart content type expected but was $contentType", contentType.contains("multipart/form-data"))
            val body = upload.recordedBodyText()
            assertTrue("file part missing: $body", body.contains("name=\"file\"") && body.contains("filename=\"resume.pdf\""))
            assertTrue("label part missing: $body", body.contains("name=\"label\""))
        }

    // ── §5 공고 ──

    @Test
    fun `posting list preserves query parameters, route, and schema`() =
        runTest {
            installExpectation(
                method = "GET",
                path = "/api/v1/postings",
                requestQueryParameters =
                    mapOf(
                        "minScore" to "60",
                        "unreadOnly" to "true",
                        "sort" to "score_desc",
                        "cursor" to "abc",
                        "limit" to "20",
                    ),
                responseBody =
                    """
                    {"ok":true,"data":{"postings":[{"id":101,"title":"2026 카카오 SW 인턴십","type":"recruit","board":{"id":3,"name":"공식 채용"},
                     "dueDate":"2026-05-25","collectedAt":"2026-05-18T07:00:00+09:00","score":88,"scoreLabel":"very_suitable","isRead":false,"isBookmarked":false}],
                     "nextCursor":"def"}}
                    """.trimIndent(),
            )

            val page =
                requireNotNull(
                    postingService
                        .getPostings(
                            boardIds = listOf(3),
                            types = listOf("recruit"),
                            minScore = 60,
                            unreadOnly = true,
                            sort = "score_desc",
                            cursor = "abc",
                            limit = 20,
                        ).data,
                )

            assertEquals(101L, page.postings.single().id)
            assertEquals("def", page.nextCursor)
            val recorded = recordedRequests("GET", "/api/v1/postings").single().jsonObject
            val query = recorded.getValue("queryStringParameters").toString()
            assertTrue("array query missing: $query", query.contains("boardIds") && query.contains("types"))
        }

    @Test
    fun `posting detail, bookmark, and read preserve routes and schema`() =
        runTest {
            installExpectation(
                method = "GET",
                path = "/api/v1/postings/101",
                responseBody =
                    """
                    {"ok":true,"data":{"id":101,"title":"2026 카카오 SW 인턴십","type":"recruit","board":{"id":3,"name":"공식 채용"},
                     "rawContent":"본문","url":"https://careers.kakao.com/1","dueDate":"2026-05-25","collectedAt":"2026-05-18T07:00:00+09:00",
                     "isRead":true,"isBookmarked":false,
                     "parsed":{"keywords":["Spring","Kotlin"],"qualifications":{"year":"2학년 이상","gpa":null},"preferences":["RDB 1년+"],
                       "formQuestions":[{"order":1,"question":"지원 동기를 작성해 주세요","maxChars":500}]},
                     "suitability":{"score":88,"label":"very_suitable","breakdown":[{"axis":"field_similarity","score":95,"weight":40}],
                       "strengthComment":"강점","weaknessComment":null},
                     "similar":[]}}
                    """.trimIndent(),
            )
            installExpectation(method = "POST", path = "/api/v1/postings/101/bookmark", responseBody = """{"ok":true}""")
            installExpectation(method = "DELETE", path = "/api/v1/postings/101/bookmark", responseBody = """{"ok":true}""")
            installExpectation(method = "POST", path = "/api/v1/postings/101/read", responseBody = """{"ok":true}""")

            val detail = requireNotNull(postingService.getPostingDetail(101).data)

            assertEquals(listOf("Spring", "Kotlin"), requireNotNull(detail.parsed).keywords)
            assertEquals(88, requireNotNull(detail.suitability).score)
            assertEquals(true, postingService.addBookmark(101).ok)
            assertEquals(true, postingService.removeBookmark(101).ok)
            assertEquals(true, postingService.markRead(101).ok)
            assertExactlyOneRecordedRequest("POST", "/api/v1/postings/101/bookmark")
            assertExactlyOneRecordedRequest("DELETE", "/api/v1/postings/101/bookmark")
            assertExactlyOneRecordedRequest("POST", "/api/v1/postings/101/read")
        }

    // ── §5 게시판 ──

    @Test
    fun `board detect, register, list, update, delete, and retry preserve routes and bodies`() =
        runTest {
            val board =
                """
                {"id":3,"url":"https://konkuk.ac.kr/board/notice","name":"건국대 공지","type":"scholarship","cycleHours":24,
                 "isActive":true,"status":"active","failCount":0,"lastCollectedAt":"2026-05-18T07:00:00+09:00"}
                """.trimIndent()
            installExpectation(
                method = "POST",
                path = "/api/v1/boards/detect",
                requestBody = wireJson.parseToJsonElement("""{"url":"https://konkuk.ac.kr/board/notice"}""").jsonObject,
                responseBody =
                    """
                    {"ok":true,"data":{"detectStatus":"success","selectors":{"title":".t","link":"a","date":".d"},
                     "preview":[{"title":"장학금 공지","url":"https://konkuk.ac.kr/1","date":"2026-05-10"}]}}
                    """.trimIndent(),
            )
            installExpectation(
                method = "POST",
                path = "/api/v1/boards",
                requestBody =
                    wireJson
                        .parseToJsonElement(
                            """{"url":"https://konkuk.ac.kr/board/notice","name":"건국대 공지","type":"scholarship","cycleHours":24}""",
                        ).jsonObject,
                responseBody = """{"ok":true,"data":$board}""",
            )
            installExpectation(method = "GET", path = "/api/v1/boards", responseBody = """{"ok":true,"data":{"boards":[$board]}}""")
            installExpectation(
                method = "PATCH",
                path = "/api/v1/boards/3",
                requestBody = wireJson.parseToJsonElement("""{"isActive":false}""").jsonObject,
                responseBody = """{"ok":true,"data":$board}""",
            )
            installExpectation(method = "DELETE", path = "/api/v1/boards/3", responseBody = """{"ok":true}""")
            installExpectation(method = "POST", path = "/api/v1/boards/3/retry", responseBody = """{"ok":true}""")

            val detection = requireNotNull(boardDetectService.detect(BoardDetectRequestDto("https://konkuk.ac.kr/board/notice")).data)
            val registered =
                requireNotNull(
                    boardService.register(BoardRegisterRequestDto("https://konkuk.ac.kr/board/notice", "건국대 공지", "scholarship", 24)).data,
                )

            assertEquals("success", detection.detectStatus)
            assertEquals(1, requireNotNull(detection.preview).size)
            assertEquals(3L, registered.id)
            assertEquals(1, requireNotNull(boardService.getBoards().data).boards.size)
            assertEquals(3L, requireNotNull(boardService.update(3, BoardUpdateRequestDto(isActive = false)).data).id)
            assertEquals(true, boardService.delete(3).ok)
            assertEquals(true, boardService.retry(3).ok)
            assertExactlyOneRecordedRequest("POST", "/api/v1/boards/detect")
            assertExactlyOneRecordedRequest("POST", "/api/v1/boards")
            assertExactlyOneRecordedRequest("GET", "/api/v1/boards")
            assertExactlyOneRecordedRequest("PATCH", "/api/v1/boards/3")
            assertExactlyOneRecordedRequest("DELETE", "/api/v1/boards/3")
            assertExactlyOneRecordedRequest("POST", "/api/v1/boards/3/retry")
        }

    private suspend fun assertSocialLoginContract(
        provider: SocialLoginProvider,
        expectedPath: String,
    ) {
        installExpectation(
            method = "POST",
            path = expectedPath,
            requestBody =
                wireJson
                    .parseToJsonElement(
                        """{"accessToken":"provider-token","deviceId":"device-uuid","fcmToken":"fcm-token"}""",
                    ).jsonObject,
            responseBody =
                """
                {
                  "ok": true,
                  "data": {
                    "accessToken": "access",
                    "refreshToken": "refresh",
                    "isNewUser": true,
                    "expiresIn": 3600
                  }
                }
                """.trimIndent(),
        )

        val result =
            authService.socialLogin(
                provider = provider,
                body =
                    SocialLoginRequestDto(
                        accessToken = "provider-token",
                        deviceId = "device-uuid",
                        fcmToken = "fcm-token",
                    ),
            )
        val data = requireNotNull(result.data)

        assertEquals(true, result.ok)
        assertEquals("access", data.accessToken)
        assertEquals("refresh", data.refreshToken)
        assertEquals(true, data.isNewUser)
        assertEquals(3600L, data.expiresIn)
        assertExactlyOneRecordedRequest("POST", expectedPath)
    }

    // ── §6 지원서 작성 ──

    @Test
    fun `application draft creation preserves route, strict request JSON, and response schema`() =
        runTest {
            installExpectation(
                method = "POST",
                path = "/api/v1/applications",
                requestBody = wireJson.parseToJsonElement("""{"postingId":101,"tone":"formal"}""").jsonObject,
                responseBody =
                    """
                    {"ok":true,"data":{"id":42,"status":"generating","postingId":101,"items":[
                     {"id":1,"order":1,"question":"지원 동기...","maxChars":500,"status":"done","answer":"..."},
                     {"id":2,"order":2,"question":"강점과 약점...","maxChars":400,"status":"loading","answer":null}]}}
                    """.trimIndent(),
            )

            val draft = requireNotNull(applicationService.createApplication(CreateApplicationRequestDto(101L, "formal")).data)

            assertEquals(42L, draft.id)
            assertEquals("generating", draft.status)
            assertEquals(listOf("done", "loading"), draft.items.map { it.status })
            assertEquals(listOf(500, 400), draft.items.map { it.maxChars })
            assertExactlyOneRecordedRequest("POST", "/api/v1/applications")
        }

    /**
     * SSE 는 본문을 **모으지 않고** 잘라 읽는다. `@Streaming` 선언이 빠지면 Retrofit 이 스트림이 닫힐 때까지
     * 기다렸다 통째로 넘기므로, 소켓을 실제로 지나는 이 검증이 그 선언의 유일한 회귀 가드다.
     */
    @Test
    fun `application progress stream preserves route and server-sent event framing`() =
        runTest {
            installExpectation(
                method = "GET",
                path = "/api/v1/applications/42/stream",
                responseBody =
                    ": keep-alive\n\n" +
                        "event: item_done\ndata: {\"itemId\": 2, \"answer\": \"...\"}\n\n" +
                        "event: status\ndata: {\"status\": \"ready\"}\n\n",
            )

            val events = applicationStreamService.stream(42L).asServerSentEvents().toList()

            assertEquals(listOf("item_done", "status"), events.map { it.event })
            assertEquals("""{"itemId": 2, "answer": "..."}""", events.first().data)
            assertExactlyOneRecordedRequest("GET", "/api/v1/applications/42/stream")
        }

    @Test
    fun `application item regenerate and edit preserve routes and strict request JSON`() =
        runTest {
            installExpectation(
                method = "POST",
                path = "/api/v1/applications/42/items/2/regenerate",
                requestBody = wireJson.parseToJsonElement("""{"tone":"casual","emphasizeCardIds":[5,12]}""").jsonObject,
                responseBody =
                    """{"ok":true,"data":{"id":2,"order":2,"question":"강점과 약점...","maxChars":400,"status":"done","answer":"다시 쓴 답"}}""",
            )
            installExpectation(
                method = "PATCH",
                path = "/api/v1/applications/42/items/2",
                requestBody = wireJson.parseToJsonElement("""{"answer":"손으로 고친 답"}""").jsonObject,
                responseBody =
                    """{"ok":true,"data":{"id":2,"order":2,"question":"강점과 약점...","maxChars":400,"status":"done","answer":"손으로 고친 답"}}""",
            )

            val regenerated =
                requireNotNull(
                    applicationService
                        .regenerateItem(42L, 2L, RegenerateItemRequestDto(tone = "casual", emphasizeCardIds = listOf(5L, 12L)))
                        .data,
                )
            val edited =
                requireNotNull(applicationService.updateItemAnswer(42L, 2L, UpdateItemAnswerRequestDto("손으로 고친 답")).data)

            assertEquals("다시 쓴 답", regenerated.answer)
            assertEquals("손으로 고친 답", edited.answer)
            assertExactlyOneRecordedRequest("POST", "/api/v1/applications/42/items/2/regenerate")
            assertExactlyOneRecordedRequest("PATCH", "/api/v1/applications/42/items/2")
        }

    /** 옵션 필드를 안 보내는 것과 `null` 을 보내는 것은 서버에 다른 뜻이다 — 안 보낸 쪽을 고정한다. */
    @Test
    fun `application item regenerate omits absent options from the request body`() =
        runTest {
            installExpectation(
                method = "POST",
                path = "/api/v1/applications/42/items/2/regenerate",
                requestBody = wireJson.parseToJsonElement("{}").jsonObject,
                responseBody =
                    """{"ok":true,"data":{"id":2,"order":2,"question":"강점과 약점...","maxChars":400,"status":"done","answer":"..."}}""",
            )

            assertEquals(true, applicationService.regenerateItem(42L, 2L, RegenerateItemRequestDto()).ok)

            assertExactlyOneRecordedRequest("POST", "/api/v1/applications/42/items/2/regenerate")
        }

    @Test
    fun `application save, result, history, and delete preserve routes and query parameters`() =
        runTest {
            val saved =
                """{"id":42,"status":"saved","postingId":101,"result":"pending","items":[
                   {"id":1,"order":1,"question":"지원 동기...","maxChars":500,"status":"done","answer":"..."}]}"""
            installExpectation(method = "POST", path = "/api/v1/applications/42/save", responseBody = """{"ok":true,"data":$saved}""")
            installExpectation(
                method = "PATCH",
                path = "/api/v1/applications/42/result",
                requestBody = wireJson.parseToJsonElement("""{"result":"pass"}""").jsonObject,
                responseBody = """{"ok":true,"data":$saved}""",
            )
            installExpectation(
                method = "GET",
                path = "/api/v1/applications",
                requestQueryParameters = mapOf("status" to "saved", "limit" to "20"),
                responseBody = """{"ok":true,"data":{"applications":[$saved],"nextCursor":"eyJ"}}""",
            )
            installExpectation(method = "DELETE", path = "/api/v1/applications/42", responseBody = """{"ok":true}""")

            assertEquals("saved", requireNotNull(applicationService.save(42L).data).status)
            assertEquals(
                "pending",
                requireNotNull(applicationService.updateResult(42L, UpdateApplicationResultRequestDto("pass")).data).result,
            )
            val history = requireNotNull(applicationService.getApplications(status = "saved", cursor = null, limit = 20).data)
            assertEquals(true, applicationService.delete(42L).ok)

            assertEquals("eyJ", history.nextCursor)
            assertEquals(1, history.applications.size)
            assertExactlyOneRecordedRequest("POST", "/api/v1/applications/42/save")
            assertExactlyOneRecordedRequest("PATCH", "/api/v1/applications/42/result")
            assertExactlyOneRecordedRequest("DELETE", "/api/v1/applications/42")
            // cursor 는 안 보냈다 — Retrofit 이 null @Query 를 빼는 것을 소켓에서 확인한다.
            val recorded = recordedRequests("GET", "/api/v1/applications").single().jsonObject
            assertTrue("cursor must be absent: $recorded", !recorded.toString().contains("cursor"))
        }

    // ── §7 신규 기능(For You · 로드맵 · Export) ──

    /**
     * `reason` 이 톱 픽에서는 **배열**, 나머지에서는 **문자열**이다. 계약이 그렇게 적혀 있으므로 둘 다 소켓을
     * 지나는 것을 확인한다 — DTO 를 한 타입으로 합치면 둘 중 한쪽 응답이 파싱 실패로 떨어진다.
     */
    @Test
    fun `for you feed preserves route and the mixed reason schema`() =
        runTest {
            installExpectation(
                method = "GET",
                path = "/api/v1/feed/for-you",
                responseBody =
                    """
                    {"ok":true,"data":{
                     "topPick":{"postingId":101,"reason":["전공 일치","마감까지 여유"]},
                     "byStrength":[{"postingId":102,"reason":"프로젝트 경험이 맞아요"}],
                     "byGap":[{"postingId":103,"reason":"어학·인턴 보완용"}]}}
                    """.trimIndent(),
            )

            val feed = requireNotNull(forYouService.getForYouFeed().data)

            assertEquals(listOf("전공 일치", "마감까지 여유"), feed.topPick?.reason)
            assertEquals("프로젝트 경험이 맞아요", feed.byStrength.single().reason)
            assertEquals(103L, feed.byGap.single().postingId)
            assertExactlyOneRecordedRequest("GET", "/api/v1/feed/for-you")
        }

    /** 지표는 정수와 소수가 한 표에 섞여 온다(`"me": 4` · `"peerAvg": 0.8`). 소켓에서 그것을 고정한다. */
    @Test
    fun `roadmap compare preserves route, cohort query, and mixed number schema`() =
        runTest {
            installExpectation(
                method = "GET",
                path = "/api/v1/roadmap/compare",
                requestQueryParameters = mapOf("cohort" to "senior"),
                responseBody =
                    """
                    {"ok":true,"data":{"cohort":"senior","sampleSize":86,
                     "metrics":[{"name":"프로젝트 수","me":4,"peerAvg":2},{"name":"인턴 경험","me":0,"peerAvg":0.8}],
                     "suggestions":[{"semester":"3-2","action":"SQLD + 토익 800+","expectedLift":12}]}}
                    """.trimIndent(),
            )

            val comparison = requireNotNull(forYouService.getRoadmapComparison("senior").data)

            assertEquals("senior", comparison.cohort)
            assertEquals(86, comparison.sampleSize)
            assertEquals(listOf(2.0, 0.8), comparison.metrics.map { it.peerAvg })
            assertEquals(12, comparison.suggestions.single().expectedLift)
            assertExactlyOneRecordedRequest("GET", "/api/v1/roadmap/compare")
        }

    @Test
    fun `strength export preserves route, strict request JSON, and response schema`() =
        runTest {
            installExpectation(
                method = "POST",
                path = "/api/v1/export",
                requestBody =
                    wireJson
                        .parseToJsonElement("""{"format":"markdown","sections":["basic","skills"]}""")
                        .jsonObject,
                responseBody = """{"ok":true,"data":{"format":"markdown","content":"# 제목\n본문"}}""",
            )

            val export =
                requireNotNull(
                    forYouService.export(ExportRequestDto(format = "markdown", sections = listOf("basic", "skills"))).data,
                )

            assertEquals("markdown", export.format)
            assertTrue("content must survive newlines: ${export.content}", export.content.contains("\n"))
            assertExactlyOneRecordedRequest("POST", "/api/v1/export")
        }

    private fun installExpectation(
        method: String,
        path: String,
        requestBody: JsonElement? = null,
        requestHeaders: Map<String, String> = emptyMap(),
        requestQueryParameters: Map<String, String> = emptyMap(),
        responseBody: String,
    ) {
        val expectation =
            buildJsonObject {
                putJsonObject("httpRequest") {
                    put("method", method)
                    put("path", path)
                    if (requestHeaders.isNotEmpty()) {
                        putJsonObject("headers") {
                            requestHeaders.forEach { (name, value) ->
                                put(name, buildJsonArray { add(JsonPrimitive(value)) })
                            }
                        }
                    }
                    if (requestQueryParameters.isNotEmpty()) {
                        putJsonObject("queryStringParameters") {
                            requestQueryParameters.forEach { (name, value) ->
                                put(name, buildJsonArray { add(JsonPrimitive(value)) })
                            }
                        }
                    }
                    if (requestBody != null) {
                        putJsonObject("body") {
                            put("type", "JSON")
                            put("json", requestBody)
                            put("matchType", "STRICT")
                        }
                    }
                }
                putJsonObject("httpResponse") {
                    put("statusCode", 200)
                    putJsonObject("headers") {
                        put(
                            "Content-Type",
                            buildJsonArray { add(JsonPrimitive("application/json")) },
                        )
                    }
                    put("body", responseBody)
                }
            }

        controlPut("/mockserver/expectation", expectation.toString())
    }

    private fun assertExactlyOneRecordedRequest(
        method: String,
        path: String,
    ) {
        val recorded = recordedRequests(method, path)

        assertEquals("$method $path must cross the socket exactly once", 1, recorded.size)
    }

    private fun recordedRequests(
        method: String,
        path: String,
    ): JsonArray {
        val matcher =
            buildJsonObject {
                put("method", method)
                put("path", path)
            }

        return wireJson
            .parseToJsonElement(
                controlPut("/mockserver/retrieve?type=REQUESTS", matcher.toString()),
            ).jsonArray
    }

    /** MockServer 는 헤더를 `{"headers": {"Name": ["v"]}}` 또는 `[{"name","values"}]` 로 돌려준다 — 둘 다 받는다. */
    private fun JsonObject.headerValues(name: String): List<String> {
        val headers = this["headers"] ?: return emptyList()
        return when (headers) {
            is JsonObject -> {
                headers.entries
                    .filter { it.key.equals(name, ignoreCase = true) }
                    .flatMap { entry -> (entry.value as? JsonArray)?.map { it.jsonPrimitive.content } ?: listOf(entry.value.toString()) }
            }

            is JsonArray -> {
                headers
                    .map { it.jsonObject }
                    .filter { it["name"]?.jsonPrimitive?.content.equals(name, ignoreCase = true) }
                    .flatMap { header -> (header["values"] as? JsonArray)?.map { it.jsonPrimitive.content }.orEmpty() }
            }

            else -> {
                emptyList()
            }
        }
    }

    /** 기록된 본문은 문자열이거나 `{"type":"BINARY","base64Bytes":...}` / `{"type":"STRING","string":...}` 객체다. */
    private fun JsonObject.recordedBodyText(): String =
        when (val body = this["body"]) {
            null -> {
                ""
            }

            is JsonPrimitive -> {
                body.content
            }

            is JsonObject -> {
                body["base64Bytes"]?.jsonPrimitive?.content?.let {
                    String(
                        java.util.Base64
                            .getDecoder()
                            .decode(it),
                        Charsets.ISO_8859_1,
                    )
                }
                    ?: body["string"]?.jsonPrimitive?.content
                    ?: body.toString()
            }

            else -> {
                body.toString()
            }
        }

    private fun controlPut(
        path: String,
        payload: String = "",
    ): String {
        val request =
            Request
                .Builder()
                .url("${mockServer.endpoint}$path")
                .put(payload.toRequestBody(JSON_MEDIA_TYPE))
                .build()

        return controlClient.newCall(request).execute().use { response ->
            val responseBody = response.body.string()
            check(response.isSuccessful) {
                "MockServer control PUT $path failed: ${response.code} $responseBody"
            }
            responseBody
        }
    }

    companion object {
        private const val ENABLE_ENV = "RUN_API_CONTRACT_SMOKE"
        private const val MOCKSERVER_VERSION = "7.6.0"
        private val JSON_MEDIA_TYPE = "application/json".toMediaType()
        private val controlClient = OkHttpClient()
        private val wireJson =
            Json {
                ignoreUnknownKeys = true
            }

        private lateinit var mockServer: MockServerContainer

        @BeforeClass
        @JvmStatic
        fun startContainer() {
            assumeTrue(
                "$ENABLE_ENV=true 인 전용 workflow 에서만 Docker 계약 검증을 실행한다",
                System.getenv(ENABLE_ENV) == "true",
            )
            check(DockerClientFactory.instance().isDockerAvailable) {
                "Docker runtime is required when $ENABLE_ENV=true"
            }

            mockServer =
                MockServerContainer(
                    DockerImageName.parse("mockserver/mockserver:mockserver-$MOCKSERVER_VERSION"),
                )
            mockServer.start()
        }

        @AfterClass
        @JvmStatic
        fun stopContainer() {
            if (::mockServer.isInitialized) {
                mockServer.stop()
            }
            controlClient.dispatcher.executorService.shutdown()
            controlClient.connectionPool.evictAll()
        }
    }
}
