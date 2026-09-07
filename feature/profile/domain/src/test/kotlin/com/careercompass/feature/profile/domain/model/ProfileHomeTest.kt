package com.careercompass.feature.profile.domain.model

import com.careercompass.feature.profile.domain.userProfile
import org.junit.Test

class ProfileHomeTest {
    @Test(expected = IllegalArgumentException::class)
    fun `음수 개수는 담지 않는다`() {
        ProfileHome(profile = userProfile(), experienceCardCount = -1, pastApplicationCount = 0)
    }
}
