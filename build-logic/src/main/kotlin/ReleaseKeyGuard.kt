import org.gradle.api.Project

/**
 * 소셜 로그인 키를 읽으면서 그 자리에서 release 가드까지 배선한다.
 *
 * 키는 루트 `local.properties`(gitignore) → 환경변수 순으로 읽는다 — 빌드 스크립트에 키
 * 문자열을 직접 박지 않기 위한 경로 제한(저장소 유출 시 도용 위험). 읽기와
 * [requireKeyForReleaseBuild] 를 한 호출로 묶은 이유는 회귀 방지다: 같은 키를 굽는 주입
 * 지점이 앞으로 늘 때 가드 호출 한 줄을 빠뜨리면, 빈 키로 소셜 로그인이 불능인 release
 * 산출물이 조용히 다시 나온다 — 이 가드가 막으려는 바로 그 사고(07-13 배포본).
 */
fun Project.socialLoginKey(keyName: String): String {
    val value = externalBuildValue(keyName) ?: ""
    requireKeyForReleaseBuild(keyName, value)
    return value
}

/**
 * release variant 빌드 시 [value]가 빈 값이면 명확한 메시지로 빌드를 실패시킨다.
 *
 * `localProperties.getProperty(...) ?: System.getenv(...) ?: ""` 폴백은 키가 없어도 빌드를
 * 통과시켜, 소셜 로그인이 불능인 release 산출물을 조용히 만들어낸다. 검증 태스크를
 * `preReleaseBuild` 앞에 걸어 release variant 를 실제로 빌드하는 경우에만 실패시키고,
 * debug 빌드는 기존처럼 빈 값을 허용한다(로컬 개발 편의).
 *
 * 일반적인 소셜 키 주입 지점은 [socialLoginKey] 를 쓸 것 — 읽기와 가드가 한 호출로 묶여
 * 가드 누락 여지가 없다. 이 함수는 값을 다른 경로로 이미 들고 있는 경우의 저수준 진입점.
 */
fun Project.requireKeyForReleaseBuild(
    keyName: String,
    value: String,
) {
    val taskSuffix =
        keyName.split("_").joinToString("") { word ->
            word.lowercase().replaceFirstChar { it.uppercase() }
        }
    registerReleaseBuildGuard(
        taskName = "check${taskSuffix}ForRelease",
        taskDescription = "release 빌드 전에 $keyName 주입 여부를 검증한다.",
        isMissing = value.isBlank(),
        failureMessage =
            """
            |$keyName 가 비어 있어 release 빌드를 중단합니다.
            |빈 키로 빌드된 release APK 는 소셜 로그인이 동작하지 않습니다.
            |루트 local.properties 또는 CI 환경변수 $keyName 를 설정한 뒤 다시 빌드하세요.
            |발급·설정 방법은 docs/release/credentials.md 의 「로컬에서 빌드하려면」 절 참고.
            """.trimMargin(),
    )
}

/**
 * release 서명 자격 네 키가 하나도 없을 때 release variant 빌드를 시작 시점에 끊는다.
 *
 * 두지 않아도 AGP 가 `packageRelease` 에서 실패시키기는 한다. 다만 R8·lintVital 을 모두 마친
 * 뒤라 느리고(실측 1분 31초) 무엇을 채워야 하는지도 알리지 않는다.
 *
 * [missingKeys] 가 비어 있으면 통과한다. 일부만 기재된 경우는 debug 빌드에서도 잘못된 상태라
 * 호출부가 configuration 단계에서 끊는다.
 */
fun Project.requireReleaseSigningForReleaseBuild(missingKeys: List<String>) {
    registerReleaseBuildGuard(
        taskName = "checkReleaseSigningForRelease",
        taskDescription = "release 빌드 전에 서명 자격 네 키 주입 여부를 검증한다.",
        isMissing = missingKeys.isNotEmpty(),
        failureMessage =
            """
            |release 서명 자격이 없어 release 빌드를 중단합니다.
            |루트 local.properties 누락 항목: ${missingKeys.joinToString()}
            |네 키는 하나의 단위입니다 — 모두 채우세요. debug 와 달리 release 에는 폴백 keystore 가 없습니다.
            |인계·설정 방법은 docs/release/distribution.md 의 「운영 자격 인계」 절 참고.
            """.trimMargin(),
    )
}

/**
 * `preReleaseBuild` 에 매달아 release variant 를 실제로 빌드할 때만 도는 검증 태스크를 등록한다.
 *
 * 등록 자체는 [registerVariantBuildGuard] 가 한다 — debug 주소 가드(BaseUrlGuard.kt)와 같은 배선을
 * 쓰기 위해서다. 여기 남은 몫은 붙일 자리(`preReleaseBuild`)를 고정하는 것뿐이다.
 */
private fun Project.registerReleaseBuildGuard(
    taskName: String,
    taskDescription: String,
    isMissing: Boolean,
    failureMessage: String,
) {
    registerVariantBuildGuard(
        preBuildTaskName = "preReleaseBuild",
        taskName = taskName,
        taskDescription = taskDescription,
        shouldFail = isMissing,
        failureMessage = failureMessage,
    )
}
