package com.careercompass.feature.profile.domain

import com.careercompass.core.model.experience.Experience
import com.careercompass.core.model.experience.ExperienceDetails
import com.careercompass.core.model.experience.ExperiencePoint
import com.careercompass.core.model.user.JobInterest
import com.careercompass.core.model.user.UserProfile

/** 테스트가 프로필의 「채워진 상태」에서 한 칸씩 비워 볼 수 있게 하는 기준값. */
internal fun userProfile(
    name: String? = "이준혁",
    school: String? = "건국대학교",
    department: String? = "컴퓨터공학부",
    gpa: Double? = 3.9,
    gradYear: Int? = 2027,
    jobInterests: List<JobInterest> = listOf(JobInterest(code = "android", priority = 1)),
    tags: List<String> = listOf("모바일"),
    onboardingDone: Boolean = true,
    completion: Int = 78,
): UserProfile =
    UserProfile(
        id = 1L,
        name = name,
        school = school,
        department = department,
        gpa = gpa,
        gradYear = gradYear,
        jobInterests = jobInterests,
        tags = tags,
        onboardingDone = onboardingDone,
        completion = completion,
    )

internal fun experienceCard(id: Long): Experience =
    Experience(
        id = id,
        title = "카드 $id",
        startPoint = ExperiencePoint.YearMonth(2025, 3),
        endPoint = null,
        details = ExperienceDetails.Project(role = null, techs = emptyList(), summary = null, link = null),
        createdAt = null,
    )
