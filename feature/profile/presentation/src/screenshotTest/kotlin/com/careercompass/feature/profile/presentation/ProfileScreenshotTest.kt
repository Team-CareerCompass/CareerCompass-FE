package com.careercompass.feature.profile.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.android.tools.screenshot.PreviewTest
import com.careercompass.core.model.application.PastApplication
import com.careercompass.core.model.application.PastApplicationCategory
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.domain.model.ProfileCompletionGap
import com.careercompass.feature.profile.presentation.basicinfo.ProfileEditContent
import com.careercompass.feature.profile.presentation.basicinfo.ProfileEditUiState
import com.careercompass.feature.profile.presentation.experience.ExperienceListContent
import com.careercompass.feature.profile.presentation.experience.ExperienceListUiState
import com.careercompass.feature.profile.presentation.experience.component.ExperienceCardRow
import com.careercompass.feature.profile.presentation.home.ProfileHomeContent
import com.careercompass.feature.profile.presentation.home.ProfileHomeUiState
import com.careercompass.feature.profile.presentation.home.component.ProfileCompletionCard
import com.careercompass.feature.profile.presentation.home.component.ProfileMenuRow
import com.careercompass.feature.profile.presentation.home.component.ProfileSummaryCard
import com.careercompass.feature.profile.presentation.pastapplication.PastApplicationListContent
import com.careercompass.feature.profile.presentation.pastapplication.PastApplicationListUiState

/** 기본 배율의 조합·배치를 검증한다. 큰 글꼴 제외 정책과 별개인 #409 범위다. */
@PreviewTest
@Preview(name = "Profile home loaded", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ProfileHomeLoadedPreview() {
    ProfilePreviewSurface {
        ProfileHomeContent(
            state = ProfileHomeUiState(profile = previewProfile(), experienceCardCount = 12, pastApplicationCount = 5),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "Profile edit loaded", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ProfileEditLoadedPreview() {
    ProfilePreviewSurface {
        val profile = previewProfile()
        ProfileEditContent(
            state =
                ProfileEditUiState(
                    original = profile,
                    name = profile.name.orEmpty(),
                    school = profile.school.orEmpty(),
                    department = profile.department.orEmpty(),
                    gradePointAverage = "3.9",
                    graduationDate = "2027",
                    selectedJobCodes = profile.jobInterests.map { it.code },
                    interestTags = profile.tags,
                ),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "Experience list loaded", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun ExperienceListLoadedPreview() {
    ProfilePreviewSurface {
        ExperienceListContent(state = ExperienceListUiState(cards = previewExperiences(), totalCount = 2), onEvent = {})
    }
}

@PreviewTest
@Preview(name = "Past application list loaded", widthDp = 360, heightDp = 800, locale = "ko")
@Composable
public fun PastApplicationListLoadedPreview() {
    ProfilePreviewSurface {
        PastApplicationListContent(
            state =
                PastApplicationListUiState(
                    applications =
                        listOf(
                            PastApplication(
                                id = 1,
                                label = "커리어컴퍼스 모바일 개발 인턴 지원서",
                                items =
                                    listOf(
                                        PastApplicationItem(
                                            id = 11,
                                            category = PastApplicationCategory.Motivation,
                                            content = "사용자가 매일 쓰는 서비스를 만들고 싶어 지원했습니다.",
                                            confident = true,
                                        ),
                                        PastApplicationItem(
                                            id = 12,
                                            category = PastApplicationCategory.Other,
                                            content = "팀과 함께 문제를 정의하고 개선한 경험을 소개합니다.",
                                            confident = false,
                                        ),
                                    ),
                                createdAt = null,
                            ),
                        ),
                    expandedId = 1,
                ),
            onEvent = {},
        )
    }
}

@PreviewTest
@Preview(name = "Profile summary unknown counts", widthDp = 360, locale = "ko")
@Composable
public fun ProfileSummaryUnknownCountsPreview() {
    ProfileComponentGallery {
        ProfileSummaryCard(profile = previewProfile(), experienceCardCount = null, pastApplicationCount = null)
    }
}

@PreviewTest
@Preview(name = "Profile completion zero and partial", widthDp = 360, locale = "ko")
@Composable
public fun ProfileCompletionVariantsPreview() {
    ProfileComponentGallery {
        ProfileCompletionCard(completion = 0, gaps = listOf(ProfileCompletionGap.Name, ProfileCompletionGap.School))
        ProfileCompletionCard(completion = 65, gaps = listOf(ProfileCompletionGap.JobInterests, ProfileCompletionGap.ExperienceCards))
    }
}

@PreviewTest
@Preview(name = "Profile menu with badge", widthDp = 360, locale = "ko")
@Composable
public fun ProfileMenuBadgePreview() {
    ProfileComponentGallery {
        ProfileMenuRow(
            title = stringResource(R.string.profile_home_menu_experience_cards),
            description = stringResource(R.string.profile_home_menu_experience_cards_description),
            count = 30,
            onClick = {},
        )
    }
}

@PreviewTest
@Preview(name = "Experience rows with and without subtitle", widthDp = 360, locale = "ko")
@Composable
public fun ExperienceRowVariantsPreview() {
    ProfileComponentGallery {
        previewExperiences().forEach { card -> ExperienceCardRow(card = card, onClick = {}, onDeleteClick = {}) }
    }
}

@Composable
private fun ProfilePreviewSurface(content: @Composable () -> Unit) {
    CareerCompassTheme {
        Surface(color = CareerCompassTheme.colors.subtleSurface, content = content)
    }
}

@Composable
private fun ProfileComponentGallery(content: @Composable () -> Unit) {
    ProfilePreviewSurface {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) { content() }
    }
}

private fun previewProfile(): UserProfile =
    UserProfile(
        id = 1,
        name = "정일혁",
        school = "건국대학교",
        department = "컴퓨터공학부",
        gpa = 3.9,
        gradYear = 2027,
        jobInterests = listOf(JobInterest(code = "android", priority = 1)),
        tags = listOf("모바일", "사용자 경험"),
        onboardingDone = true,
        completion = 78,
    )

private fun previewExperiences(): List<Experience> =
    listOf(
        Experience(
            id = 1,
            title = "대학생을 위한 맞춤형 커리어 탐색 서비스 개발",
            startPoint = ExperiencePoint.YearMonth(2025, 3),
            endPoint = ExperiencePoint.YearMonth(2025, 8),
            details = ExperienceDetails.Project(role = "안드로이드 앱 개발 및 사용자 경험 개선", techs = listOf("Kotlin"), summary = null, link = null),
            createdAt = null,
        ),
        Experience(
            id = 2,
            title = "정보처리기사",
            startPoint = ExperiencePoint.YearMonth(2025, 9),
            endPoint = null,
            details = ExperienceDetails.Certificate(issuer = null),
            createdAt = null,
        ),
    )
