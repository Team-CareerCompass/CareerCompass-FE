package com.careercompass.core.ui.navigation

import androidx.compose.runtime.Stable

/**
 * 피처의 로컬 Navigation 3 스택이 앱 셸과 만나는 **최소 경계**.
 *
 * 로컬 스택은 제 화면 사이의 push/pop 을 스스로 처리하고, 스스로 답할 수 없는 두 가지만 셸에
 * 돌려준다 — 스택 바닥에서의 back([exit])과 바텀바 표시 판정에 필요한 깊이([onAtRootChanged]).
 * 그 밖의 이동(다른 소관 그래프로 가기)은 피처마다 달라서 각 host 가 제 콜백으로 받는다.
 *
 * [exit] 의 구현은 루트 백스택의 pop 이다(#260). 루트가 Nav2 였을 때는 `NavController.popBackStack()` 이었고,
 * 계약은 그대로인 채 구현만 갈렸다.
 */
@Stable
public interface FeatureStackBoundary {
    /** 로컬 스택 바닥에서 back 이 눌렸다. 셸이 이 피처를 백스택에서 내린다. */
    public fun exit()

    /**
     * 로컬 스택이 바닥(= 피처 시작 화면)인지 바뀌었다.
     *
     * 셸의 바텀바 판정은 루트 키만 보므로 로컬 스택 깊이를 모른다. 깊이를 아는 쪽이
     * 셸에 올려 판정에 합성한다.
     *
     * 마지막으로 올린 값은 host 가 컴포지션에서 빠져도 유효하다. 로컬 스택은 부모 entry 에 남아 있어
     * 돌아오면 같은 깊이로 다시 그려지기 때문이다. 떠날 때 `true` 로 되돌리면 돌아오는 첫 프레임이
     * 그 값으로 그려진다(#354). host 는 컴포지션에 들어올 때마다 제 깊이를 다시 올리므로, 스택이
     * 새로 세워진 경우도 스스로 바로잡는다.
     */
    public fun onAtRootChanged(isAtRoot: Boolean) {}
}
