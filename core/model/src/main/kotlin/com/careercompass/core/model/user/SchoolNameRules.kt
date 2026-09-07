package com.careercompass.core.model.user

/**
 * 학교 이름 표기 규칙 — 목록에서 고른 값과 직접 입력한 값이 **저장 시점에 같은 모양**이 되게 한다.
 *
 * [SchoolCatalog] 40개에 없는 학교(전문대·대학원·해외 대학·개편된 교명)를 직접 입력으로 열어 주면
 * 표기가 흔들린다 — 「건국대 」·「건국  대학교」 처럼 공백만 다른 값이 서버에 쌓이면 나중에 학교 목록을
 * 서버로 옮길 때 정리 비용이 된다. 그래서 규칙을 화면·ViewModel 이 아니라 목록 옆 한 곳에 두고,
 * 두 경로 모두 저장 직전에 여기를 지난다.
 *
 * 규칙은 「다듬기」 뿐이다. 실재하는 학교인지는 목록 없이 판정할 수 없으므로 검증하지 않는다 —
 * 막다른 길을 막는 것이 목적이고, 있지도 않은 이름을 걸러 내는 것은 서버 목록이 생긴 뒤의 일이다.
 */
public object SchoolNameRules {
    /**
     * 학교 이름 길이 상한.
     *
     * 기능 스펙 F1-2 는 학교 길이를 정하지 않았다(이름 20자·학과 30자만 정한다). 직접 입력이 열리면
     * 「University of California, Berkeley」(33자) 같은 해외 대학과 「○○대학교 대학원 ○○학과」 꼴이
     * 들어오므로 학과보다 넉넉하게 잡되, 문장이 통째로 들어오는 것은 막는다.
     */
    public const val MAX_LENGTH: Int = 50

    /**
     * 앞뒤 공백을 걷어내고 가운데 연속 공백을 한 칸으로 줄인다.
     *
     * 「한양대학교 ERICA」 처럼 이름 안의 공백은 표기의 일부라 지우지 않는다 — 지우면 목록 값과
     * 모양이 달라진다. 지우는 것은 사용자가 의도하지 않은 잉여 공백뿐이다.
     */
    public fun normalize(raw: String): String = raw.trim().replace(WHITESPACE_RUN, " ")

    /** 다듬은 뒤 비지 않고 [MAX_LENGTH] 이내인가. 공백만 입력한 값은 다듬으면 비므로 여기서 걸린다. */
    public fun isValid(raw: String): Boolean {
        val name = normalize(raw)
        return name.isNotEmpty() && name.length <= MAX_LENGTH
    }

    private val WHITESPACE_RUN = Regex("""\s+""")
}
