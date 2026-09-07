package com.careercompass.core.model.user

/**
 * 온보딩 Step 1 학교 선택 목록 — 기능 스펙 F1-2 「학교: 검색 선택, 학교 목록에서 선택」.
 *
 * API_SPEC v0.1 에는 학교 목록 엔드포인트가 없어 국내 주요 대학을 로컬 상수로 둔다. 서버 목록이 생기면 이
 * 객체만 갈아끼운다.
 */
public object SchoolCatalog {
    public val schools: List<String> =
        listOf(
            "가천대학교",
            "강원대학교",
            "건국대학교",
            "경북대학교",
            "경희대학교",
            "고려대학교",
            "광운대학교",
            "국민대학교",
            "단국대학교",
            "동국대학교",
            "동아대학교",
            "명지대학교",
            "부산대학교",
            "서강대학교",
            "서울과학기술대학교",
            "서울대학교",
            "서울시립대학교",
            "성균관대학교",
            "성신여자대학교",
            "세종대학교",
            "숙명여자대학교",
            "숭실대학교",
            "아주대학교",
            "연세대학교",
            "영남대학교",
            "울산과학기술원",
            "이화여자대학교",
            "인하대학교",
            "전남대학교",
            "전북대학교",
            "중앙대학교",
            "충남대학교",
            "충북대학교",
            "한국과학기술원",
            "한국외국어대학교",
            "한국항공대학교",
            "한양대학교",
            "한양대학교 ERICA",
            "홍익대학교",
            "포항공과대학교",
        )

    /**
     * 공백을 무시한 부분 일치 검색. 빈 검색어면 전체 목록을 돌려준다.
     *
     * "건국 대" 처럼 띄어 써도 "건국대학교" 를 찾도록 검색어와 학교명 모두에서 공백을 제거해 비교한다.
     */
    public fun search(query: String): List<String> {
        val normalized = query.normalized()
        if (normalized.isEmpty()) return schools
        return schools.filter { school -> school.normalized().contains(normalized) }
    }

    public fun contains(school: String): Boolean = schools.contains(school)

    private fun String.normalized(): String = filterNot(Char::isWhitespace).lowercase()
}
