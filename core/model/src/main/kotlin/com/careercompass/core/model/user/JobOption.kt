package com.careercompass.core.model.user

/**
 * 희망 직무 선택지 하나.
 *
 * @property code 서버 `PUT /users/me/job-interests` 에 보내는 코드(영문 snake_case, 예: `backend`).
 * @property label 화면 표시 문구.
 */
public data class JobOption(
    val code: String,
    val label: String,
) {
    init {
        require(code.isNotBlank()) { "code must not be blank" }
        require(CODE_PATTERN.matches(code)) { "code must be lower snake_case: $code" }
        require(label.isNotBlank()) { "label must not be blank" }
    }

    private companion object {
        val CODE_PATTERN = Regex("""^[a-z][a-z0-9_]*$""")
    }
}

/**
 * 온보딩 Step 2 에서 고르는 희망 직무 목록 — 기능 스펙 F1-2 「희망 직무는 서비스에서 제공하는 목록에서 선택」.
 *
 * 스펙은 「한국 표준 직업 분류표 / 고용사이트 템플릿」 을 출처로 적었지만 API_SPEC v0.1 에는 목록 조회 엔드포인트가
 * 없다. 서버가 목록을 내려줄 때까지 로컬 상수로 둔다 — 코드는 API 예시(`backend`·`frontend`)와 같은 꼴을 따른다.
 */
public object JobOptionCatalog {
    public val options: List<JobOption> =
        listOf(
            JobOption(code = "backend", label = "백엔드 개발"),
            JobOption(code = "frontend", label = "프론트엔드 개발"),
            JobOption(code = "mobile", label = "모바일 앱 개발"),
            JobOption(code = "data_analysis", label = "데이터 분석"),
            JobOption(code = "ai_ml", label = "AI/ML"),
            JobOption(code = "devops", label = "DevOps"),
            JobOption(code = "security", label = "보안"),
            JobOption(code = "qa", label = "QA"),
            JobOption(code = "product_management", label = "PM/기획"),
            JobOption(code = "ux_ui_design", label = "UX/UI 디자인"),
            JobOption(code = "marketing", label = "마케팅"),
            JobOption(code = "finance_accounting", label = "재무/회계"),
            JobOption(code = "human_resources", label = "인사"),
            JobOption(code = "sales", label = "영업"),
            JobOption(code = "research", label = "연구"),
        )

    public fun find(code: String): JobOption? = options.firstOrNull { it.code == code }

    public fun contains(code: String): Boolean = find(code) != null
}
