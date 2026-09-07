package com.careercompass.feature.profile.domain.model

import com.careercompass.feature.profile.domain.userProfile
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileCompletionGapTest {
    @Test
    fun `다 채운 프로필에는 빈 칸이 없다`() {
        val home = ProfileHome(profile = userProfile(), experienceCardCount = 3, pastApplicationCount = 1)

        assertEquals(emptyList<ProfileCompletionGap>(), home.completionGaps())
    }

    @Test
    fun `비어 있는 칸을 선언 순서대로 모은다`() {
        val home =
            ProfileHome(
                profile = userProfile(gpa = null, tags = emptyList(), name = null),
                experienceCardCount = 2,
                pastApplicationCount = 0,
            )

        assertEquals(
            listOf(ProfileCompletionGap.Name, ProfileCompletionGap.Gpa, ProfileCompletionGap.Tags),
            home.completionGaps(),
        )
    }

    @Test
    fun `경험 카드가 0개면 빈 칸으로 센다`() {
        val home = ProfileHome(profile = userProfile(), experienceCardCount = 0, pastApplicationCount = 0)

        assertEquals(listOf(ProfileCompletionGap.ExperienceCards), home.completionGaps())
    }

    @Test
    fun `개수를 모르면 경험 카드를 빈 칸으로 세지 않는다`() {
        val home = ProfileHome(profile = userProfile(), experienceCardCount = null, pastApplicationCount = null)

        assertEquals(emptyList<ProfileCompletionGap>(), home.completionGaps())
    }
}
