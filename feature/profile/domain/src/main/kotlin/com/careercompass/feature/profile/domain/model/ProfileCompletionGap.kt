package com.careercompass.feature.profile.domain.model

/**
 * 프로필에서 **아직 비어 있는 칸** — 완성도 카드가 「무엇을 채우면 되는지」 한 줄을 고르는 근거다.
 *
 * ### 왜 서버가 아니라 우리가 세는가
 * `GET /users/me` 는 완성도를 숫자 하나(`completion`)로만 준다. 숫자만으로는 「78%」를 보여 줄 수 있어도
 * 사용자가 다음에 무엇을 할지 알 수 없고, 시안의 안내 문장(「어학 점수와 인턴 경험을 추가하면…」)은
 * 계약에 없는 항목을 말한다. 그래서 **우리가 아는 필드가 비었는지**로만 안내한다 — 서버가 완성도 산식을
 * 공개하면 그때 이 목록을 그 산식으로 옮긴다(그때까지 이 목록은 「빈 칸」이지 「가중치」가 아니다).
 *
 * 선언 순서가 곧 안내 순서다. 기본 정보(이름·학교·학과·학점·졸업 연도) → 관심(직무·태그) → 경험 카드로,
 * **채우는 데 드는 품이 적은 것부터** 둔다. 경험 카드가 맨 끝인 이유는 그것만 화면 한 장을 더 거치기
 * 때문이다.
 */
public enum class ProfileCompletionGap {
    Name,
    School,
    Department,
    Gpa,
    GradYear,
    JobInterests,
    Tags,
    ExperienceCards,
}

/**
 * 비어 있는 칸을 [ProfileCompletionGap] 선언 순서로 모은다. 다 채웠으면 빈 목록이다.
 *
 * [ProfileHome.experienceCardCount] 를 모르면 경험 카드는 **비었다고 보지 않는다** — 세지 못한 것과
 * 0개인 것은 다르고, 있는 카드를 두고 「경험 카드를 추가해 보세요」라고 하면 안내가 거짓이 된다.
 */
public fun ProfileHome.completionGaps(): List<ProfileCompletionGap> =
    ProfileCompletionGap.entries.filter { gap ->
        when (gap) {
            ProfileCompletionGap.Name -> profile.name == null
            ProfileCompletionGap.School -> profile.school == null
            ProfileCompletionGap.Department -> profile.department == null
            ProfileCompletionGap.Gpa -> profile.gpa == null
            ProfileCompletionGap.GradYear -> profile.gradYear == null
            ProfileCompletionGap.JobInterests -> profile.jobInterests.isEmpty()
            ProfileCompletionGap.Tags -> profile.tags.isEmpty()
            ProfileCompletionGap.ExperienceCards -> experienceCardCount == 0
        }
    }
