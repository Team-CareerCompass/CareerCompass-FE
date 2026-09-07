package com.careercompass.core.model.user

/**
 * 관심 분야 태그 표기 규칙 — 온보딩 Step 2 와 마이 탭의 관심 편집이 같은 값을 쓴다(#177).
 *
 * 사용자는 해시태그를 앞에 붙이는 습관이 있고(`#AI`), 그대로 저장하면 서버에 `#AI` 와 `AI` 가 따로 쌓인다.
 * 두 화면이 각자 다듬으면 한쪽에서 넣은 태그가 다른 쪽에서 중복으로 걸리지 않는 날이 온다.
 */
public object InterestTagRules {
    /** 앞의 `#` 과 양끝 공백을 걷어낸다. 다듬은 결과가 비면 태그가 아니다. */
    public fun normalize(raw: String): String = raw.trim().trimStart('#').trim()

    /**
     * 서버 값을 화면이 그릴 목록으로 다듬는다 — 중복을 걷고 상한([MAX_PROFILE_TAGS])까지만 남긴다.
     *
     * 상한을 넘겨 받는 일이 없어야 하지만, 서버가 그렇게 주면 화면은 우리가 못 고치는 값 때문에 열리지
     * 않는 대신 보일 수 있는 만큼만 보인다.
     */
    public fun sanitize(tags: List<String>): List<String> =
        tags
            .map(::normalize)
            .filter(String::isNotEmpty)
            .distinct()
            .take(MAX_PROFILE_TAGS)
}
