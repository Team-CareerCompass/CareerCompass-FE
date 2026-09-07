package com.careercompass.feature.onboarding.presentation.flow

import androidx.lifecycle.SavedStateHandle
import com.careercompass.core.model.user.JobOptionCatalog
import com.careercompass.core.model.user.MAX_JOB_INTERESTS
import com.careercompass.core.model.user.MAX_PROFILE_TAGS
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputState

/**
 * 프로세스 사망을 건너 살아남는 온보딩 입력 초안 — 기능 스펙 F1-1 의 「중단된 단계부터 재개」 중 **입력** 쪽.
 *
 * 단계 자체는 `OnboardingProgressRepository` 가 서버·로컬에 남기지만, 그 단계에서 친 글자는 여태 어디에도
 * 없었다. 안드로이드는 백그라운드 앱을 언제든 죽이고(다른 앱에서 자소서를 복사해 오는 흐름이 특히 그렇다),
 * 돌아오면 폼이 통째로 비어 있었다.
 *
 * ### 왜 [SavedStateHandle] 인가
 * 온보딩의 입력 상태는 전부 그래프 스코프 [OnboardingViewModel] 로 끌어올려져 있다(Step 1~4 가 한 인스턴스를
 * 공유한다). 상태의 주인이 ViewModel 이므로 저장도 ViewModel 의 저장소인 [SavedStateHandle] 이 맡는다 —
 * 화면 쪽에 `rememberSaveable` 을 흩뿌리면 같은 값이 두 곳에 살면서 어느 쪽이 정본인지 흐려진다.
 * 예외는 Step 4 가 고른 파일의 `Uri` 하나뿐이다(`OnboardingStep4Screen` 참고) — 그건 플랫폼 타입이라
 * ViewModel 에 들이지 않는다.
 *
 * ### 서버 값과의 우선순위
 * **서버 > 초안 > 빈 값**. 각 Step 은 「다음」에서 서버에 저장하므로 서버에 있는 값이 더 확정적인 사실이다.
 * 초안은 [OnboardingFlowState] 의 **초기값**으로만 들어가고, 그 위에 `resolveEntry()` 의 프리필이 서버
 * 프로필을 덮는다 — 살아난 초안이 서버 값을 밀어내는 일이 구조적으로 없다. 서버가 아직 모르는 필드
 * (아직 「다음」을 누르지 않은 Step 의 입력, 등록 전의 관심 태그 입력칸)에서만 초안이 남는다.
 *
 * ### 복원하지 않는 것
 * - **필드 오류**: 입력 검증은 사실이 아니라 그 순간의 피드백이다. 되살리면 아직 손대지도 않은 폼이 빨갛게
 *   떠 있고, 「다음」을 누르면 어차피 다시 계산한다.
 * - **`isSubmitting`·`isResolvingEntry` 같은 진행 상태**: 살아난 프로세스에는 그 요청이 없다. 되살리면
 *   영원히 도는 버튼이 된다.
 * - **`failure`·`pendingNavigation`·`sessionEnded` 같은 단발 신호**: 이미 지나간 사건이다. 죽기 직전의 실패 안내를 새 프로세스가
 *   다시 띄우면 방금 아무 일도 안 한 사용자에게 원인 없는 경고가 된다.
 * - **시트·피커가 열려 있었는지**: 프로세스 사망은 사용자가 의도한 이동이 아니다. 돌아왔는데 시트가 떠 있으면
 *   마지막으로 본 화면과 달라 놀란다. 대신 시트를 **다시 열면** 쓰던 글이 그대로 있다 —
 *   [restoredDirectInput] 이 그 자리다.
 * - **Step 3 경험 카드 초안**: 같은 시트가 신규 등록과 기존 카드 수정을 겸한다. 수정 초안을 되살리면 그 사이
 *   서버에서 바뀐 카드 위에 옛 입력을 통째로 덮어쓸 수 있어(#139 이후 시트는 상세 필드까지 전부 실어 나른다)
 *   「서버 값과 충돌하지 않는다」를 지킬 수 없다. 목록 자체는 `loadExperiences()` 가 서버에서 다시 읽는다.
 * - **Step 4 업로드 목록**: 진행 중이던 업로드는 프로세스와 함께 끝났고, 끝난 문서는 서버 목록에서 다시 읽는다.
 *
 * ### 세션이 끝나면 초안도 함께 버린다 (#211)
 * 401 로 앱 셸이 루트 스택을 새로 세우면 host 스코프 ViewModel 과 이 [SavedStateHandle] 이 함께 사라져 초안도
 * 없어진다. 지키지 않는 쪽이 맞다 — 다음에 로그인하는 계정이 같은 계정이라는 보장이 없고(같은 기기의 다른
 * 카카오·구글 계정), 초안은 계정에 귀속되지 않은 채 저장돼 있어 남기면 앞 계정의 입력이 뒤 계정의 폼에 나타난다
 * (지문 등록 거절 기록이 같은 이유로 계정별로 갈렸다, #98). 서버에 저장된 단계는 재개 시 `resolveEntry()` 가 다시
 * 채우므로, 잃는 것은 아직 「다음」을 누르지 않은 입력뿐이다.
 *
 * 저장 값은 [SavedStateHandle] 이 그대로 `Bundle` 로 나가므로 문자열과 [ArrayList] 만 쓴다. 읽을 때는
 * 계약(중복 없음·상한·카탈로그 소속)을 다시 통과시킨다 — 낡거나 망가진 번들이 [OnboardingStep2FormState] 의
 * `require` 를 깨뜨려 앱을 죽이지 않게.
 */
internal class OnboardingInputDraft(
    private val handle: SavedStateHandle,
) {
    /** 초안을 담은 시작 상태. 서버 프리필은 이 위에 덮인다. */
    fun restoredState(): OnboardingFlowState = OnboardingFlowState(step1 = restoredStep1(), step2 = restoredStep2())

    /**
     * 지금 상태의 입력을 초안으로 남긴다.
     *
     * [OnboardingViewModel] 이 상태 흐름 하나를 구독해 여기로 보낸다 — 입력을 바꾸는 자리가 서른 곳 가까이라
     * 각자 저장하게 두면 언젠가 한 곳이 빠지고, 빠진 자리는 프로세스가 죽어야 드러난다.
     *
     * 시트 초안은 시트가 **열려 있는 동안만** 갱신한다. 닫히는 이유는 두 가지(취소·제출)이고 둘 다 버려야 할
     * 초안이라, 닫힘을 여기서 추측하지 않고 [clearDirectInput]·[clearUploadLabel] 로 명시한다.
     */
    fun save(state: OnboardingFlowState) {
        handle[KEY_STEP1_NAME] = state.step1.name
        handle[KEY_STEP1_SCHOOL] = state.step1.school
        handle[KEY_STEP1_MAJOR] = state.step1.major
        handle[KEY_STEP1_GPA] = state.step1.gradePointAverage
        handle[KEY_STEP1_GRADUATION] = state.step1.graduationDate
        handle[KEY_STEP2_JOB_CODES] = ArrayList(state.step2.selectedJobCodes)
        handle[KEY_STEP2_INTEREST_INPUT] = state.step2.interestInput
        handle[KEY_STEP2_TAGS] = ArrayList(state.step2.interestTags)
        state.directInput?.let { sheet ->
            handle[KEY_DIRECT_INPUT_LABEL] = sheet.label
            // 저장 상태는 Binder 트랜잭션(약 1MB)을 통과한다 — 다른 앱에서 통째로 붙여 넣은 글이 그 한도를
            // 위협하면 저장을 건너뛴다. 앞부분만 남기는 쪽은 고르지 않았다: 잘린 자소서가 되살아나면 사용자는
            // 잃은 줄도 모르고 그대로 제출한다. 지금은 저장을 못 지키는 대신 「원래도 없던 것」으로 남는다.
            if (sheet.content.length <= MAX_DRAFT_CONTENT_CHARS) {
                handle[KEY_DIRECT_INPUT_CONTENT] = sheet.content
            } else {
                handle.remove<String>(KEY_DIRECT_INPUT_CONTENT)
            }
        }
        state.uploadLabel?.let { sheet -> handle[KEY_UPLOAD_LABEL] = sheet.label }
    }

    /** 직접 입력 시트를 열 때의 시작값 — 죽기 전에 쓰던 글이 있으면 그대로다. */
    fun restoredDirectInput(): DirectInputState =
        DirectInputState(
            label = handle.get<String>(KEY_DIRECT_INPUT_LABEL).orEmpty(),
            content = handle.get<String>(KEY_DIRECT_INPUT_CONTENT).orEmpty(),
        )

    /**
     * 업로드 라벨 시트를 열 때의 시작값.
     *
     * 초안이 없으면 [default](확장자를 뺀 파일명)를 쓴다. 초안이 남아 있을 수 있는 경우는 「시트가 열린 채
     * 프로세스가 죽었다」 하나뿐이다 — 취소·제출은 [clearUploadLabel] 로 즉시 비우므로, 다음에 고른 **다른**
     * 파일에 남의 라벨이 붙지 않는다.
     */
    fun restoredUploadLabel(default: String): String = handle.get<String>(KEY_UPLOAD_LABEL)?.takeIf(String::isNotEmpty) ?: default

    fun clearDirectInput() {
        handle.remove<String>(KEY_DIRECT_INPUT_LABEL)
        handle.remove<String>(KEY_DIRECT_INPUT_CONTENT)
    }

    fun clearUploadLabel() {
        handle.remove<String>(KEY_UPLOAD_LABEL)
    }

    /** 오류는 되살리지 않는다 — 값만 채우고 검증은 「다음」이 다시 한다. */
    private fun restoredStep1(): OnboardingStep1FormState =
        OnboardingStep1FormState(
            name = handle.get<String>(KEY_STEP1_NAME).orEmpty(),
            school = handle.get<String>(KEY_STEP1_SCHOOL).orEmpty(),
            major = handle.get<String>(KEY_STEP1_MAJOR).orEmpty(),
            gradePointAverage = handle.get<String>(KEY_STEP1_GPA).orEmpty(),
            graduationDate = handle.get<String>(KEY_STEP1_GRADUATION).orEmpty(),
        )

    private fun restoredStep2(): OnboardingStep2FormState =
        OnboardingStep2FormState(
            selectedJobCodes =
                handle
                    .get<ArrayList<String>>(KEY_STEP2_JOB_CODES)
                    .orEmpty()
                    .filter(JobOptionCatalog::contains)
                    .distinct()
                    .take(MAX_JOB_INTERESTS),
            interestInput = handle.get<String>(KEY_STEP2_INTEREST_INPUT).orEmpty(),
            interestTags =
                handle
                    .get<ArrayList<String>>(KEY_STEP2_TAGS)
                    .orEmpty()
                    .filter(String::isNotBlank)
                    .distinct()
                    .take(MAX_PROFILE_TAGS),
        )

    private companion object {
        /**
         * 초안으로 남기는 자소서 본문의 상한(문자). UTF-16 이라 약 200KB 로, 액티비티 저장 상태 전체가 쓰는
         * Binder 한도(약 1MB) 안에 다른 값들과 함께 들어간다. 실제 자소서는 수천 자라 여기 걸릴 일이 없고,
         * 걸린다면 사람이 쓴 글이 아니라 파일을 통째로 붙여 넣은 경우다.
         */
        const val MAX_DRAFT_CONTENT_CHARS = 100_000

        // 키는 저장 계약이다 — 바꾸면 그 순간 떠 있던 앱의 초안이 복원되지 않는다.
        const val KEY_STEP1_NAME = "onboarding.draft.step1.name"
        const val KEY_STEP1_SCHOOL = "onboarding.draft.step1.school"
        const val KEY_STEP1_MAJOR = "onboarding.draft.step1.major"
        const val KEY_STEP1_GPA = "onboarding.draft.step1.gpa"
        const val KEY_STEP1_GRADUATION = "onboarding.draft.step1.graduationDate"
        const val KEY_STEP2_JOB_CODES = "onboarding.draft.step2.jobCodes"
        const val KEY_STEP2_INTEREST_INPUT = "onboarding.draft.step2.interestInput"
        const val KEY_STEP2_TAGS = "onboarding.draft.step2.interestTags"
        const val KEY_DIRECT_INPUT_LABEL = "onboarding.draft.directInput.label"
        const val KEY_DIRECT_INPUT_CONTENT = "onboarding.draft.directInput.content"
        const val KEY_UPLOAD_LABEL = "onboarding.draft.uploadLabel.label"
    }
}
