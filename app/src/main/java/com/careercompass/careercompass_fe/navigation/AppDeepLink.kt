package com.careercompass.careercompass_fe.navigation

import android.net.Uri

/** 딥링크 URI scheme — `careercompass://…`. */
public const val DEEP_LINK_SCHEME: String = "careercompass"

/** 공고 상세 딥링크 host — `careercompass://postings/{id}`. */
public const val DEEP_LINK_HOST_POSTINGS: String = "postings"

/**
 * 앱 밖에서 들어오는 진입 계약 — 알림의 `PendingIntent`(notification 모듈)와
 * `adb shell am start -a android.intent.action.VIEW -d "<uri>"` 가 쓴다.
 *
 * URI 형식은 `careercompass://postings/{id}` 하나다 — scheme [DEEP_LINK_SCHEME], host [DEEP_LINK_HOST_POSTINGS],
 * 경로는 공고 id(양의 정수) 한 세그먼트. 예: `careercompass://postings/101` → [PostingDetail] (postingId = 101).
 *
 * notification 모듈은 `Intent(Intent.ACTION_VIEW, Uri.parse("careercompass://postings/$id"))` 에 앱 패키지를 지정해
 * `MainActivity` 로 보낸다. `MainActivity` 는 `launchMode` 가 standard 라, 이미 떠 있는 인스턴스가 `onNewIntent` 로 받게
 * 하려면 intent 에 `FLAG_ACTIVITY_SINGLE_TOP`(필요하면 `FLAG_ACTIVITY_CLEAR_TOP` 도)을 붙인다.
 *
 * 딥링크는 인증 뒤에만 적용된다 — 로그인·온보딩 중이면 `MainViewModel.pendingDeepLink` 에 보관했다가 피드 그래프에 들어온
 * 순간 이동하고(그사이 프로세스가 죽어도 저장 상태에 실려 돌아온다), 세션 종료로 루트 백스택이 다시 세워지면 소비되지
 * 않은 딥링크는 버린다.
 */
public sealed interface AppDeepLink {
    /** 공고 상세 — `careercompass://postings/{postingId}`. */
    public data class PostingDetail(
        val postingId: Long,
    ) : AppDeepLink
}

/**
 * URI → [AppDeepLink]. 계약에 맞지 않는 URI(다른 scheme·host, 경로 세그먼트가 하나가 아님, id 가 양의 정수가 아님)는
 * null 로 돌려주고 호출자가 무시한다 — 잘못된 알림 payload 가 앱을 죽이거나 엉뚱한 화면을 열지 않게.
 */
public object AppDeepLinkParser {
    public fun parse(uri: Uri?): AppDeepLink? {
        if (uri == null) return null
        return parse(scheme = uri.scheme, host = uri.host, pathSegments = uri.pathSegments)
    }

    /** [Uri] 를 분해한 값으로 판정하는 순수 함수 — JVM 단위 테스트가 이쪽으로 URI 형식을 고정한다. */
    public fun parse(
        scheme: String?,
        host: String?,
        pathSegments: List<String>,
    ): AppDeepLink? {
        if (!DEEP_LINK_SCHEME.equals(scheme, ignoreCase = true)) return null
        if (!DEEP_LINK_HOST_POSTINGS.equals(host, ignoreCase = true)) return null
        val postingId = pathSegments.singleOrNull()?.toPositiveLongOrNull() ?: return null
        return AppDeepLink.PostingDetail(postingId)
    }

    /** 숫자만으로 된 양의 Long — `+`·`-` 부호와 Long 범위 초과는 거른다. */
    private fun String.toPositiveLongOrNull(): Long? {
        if (isEmpty() || !all { it in '0'..'9' }) return null
        return toLongOrNull()?.takeIf { it > 0 }
    }
}
