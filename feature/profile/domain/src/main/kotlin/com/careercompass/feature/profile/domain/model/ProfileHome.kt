package com.careercompass.feature.profile.domain.model

import com.careercompass.core.model.user.UserProfile

/**
 * 마이 홈 한 장이 그리는 값 — 프로필 하나와 그 아래 메뉴가 배지로 보이는 개수 둘.
 *
 * ### 개수가 왜 nullable 인가
 * 프로필과 개수는 **다른 요청**이다. `GET /users/me` 는 성공했는데 `GET /experiences` 만 실패하는 일이
 * 실제로 일어나고(상한·권한이 아니라 그 순간의 네트워크·서버 사정), 그때 화면 전체를 실패로 덮으면
 * 사용자는 멀쩡히 받아 온 이름·완성도까지 못 본다. 그래서 개수는 「모른다」를 담을 수 있게 두고,
 * 화면은 모르는 배지를 **그리지 않는다** — 0 으로 적으면 「카드가 하나도 없다」는 거짓을 말하게 된다.
 *
 * 프로필 조회의 실패는 반대다. 이 화면이 그릴 것이 아무것도 남지 않으므로 `Result` 의 실패로 올라간다
 * ([com.careercompass.feature.profile.domain.usecase.LoadProfileHomeUseCase]).
 *
 * @property experienceCardCount 등록한 경험 카드 수. null 이면 세지 못했다.
 * @property pastApplicationCount 등록한 과거 지원서 수. null 이면 세지 못했다.
 */
public data class ProfileHome(
    val profile: UserProfile,
    val experienceCardCount: Int?,
    val pastApplicationCount: Int?,
) {
    init {
        require(experienceCardCount == null || experienceCardCount >= 0) { "experienceCardCount must be null or non-negative" }
        require(pastApplicationCount == null || pastApplicationCount >= 0) { "pastApplicationCount must be null or non-negative" }
    }
}
