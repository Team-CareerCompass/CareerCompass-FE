package com.careercompass.core.domain.auth

/**
 * 소셜 로그인 SDK 가 기기에 남긴 세션을 지우는 자리.
 *
 * 서버 로그아웃과 로컬 토큰 삭제만으로는 카카오 SDK 가 자기 저장소에 둔 OAuth 토큰이 그대로 남는다. 소셜 로그인
 * 요청은 그 토큰 하나만 받으므로, 남은 토큰을 꺼내면 로그아웃한 기기에서 서버 세션을 다시 열 수 있다(#370).
 *
 * SDK 를 아는 쪽은 그 SDK 를 의존하는 모듈뿐이라 계약만 여기 둔다. 구현은 카카오 SDK 를 가진 onboarding
 * presentation 에 있다.
 *
 * 정리는 best-effort 다. 네트워크가 없거나 SDK 가 초기화되지 않았어도 사용자는 로그아웃 상태로 가야 하므로
 * 구현은 자기 실패를 삼키고 예외를 던지지 않는다.
 */
public interface SocialSessionCleaner {
    public suspend fun clearSocialSession()
}
