import com.google.firebase.appdistribution.gradle.firebaseAppDistribution
import java.util.Properties

plugins {
    id("careercompass.android.application")
    id("careercompass.android.navigation")
    alias(libs.plugins.google.services)
    alias(libs.plugins.firebase.app.distribution)
    alias(libs.plugins.firebase.crashlytics)
    id("careercompass.kover")
    alias(libs.plugins.androidx.baselineprofile)
}

val localProperties = Properties()
val localPropertiesFile = rootProject.file("local.properties")
if (localPropertiesFile.exists()) {
    localPropertiesFile.inputStream().use { localProperties.load(it) }
}

// OAuth redirect(`kakao{KEY}://oauth`) 핸들러 등록용 manifestPlaceholder 와 SDK 런타임 초기화용
// BuildConfig 가 같은 값을 쓴다 — 주입 지점이 둘이어도 키는 여기서 한 번만 읽는다.
val kakaoKey = socialLoginKey("KAKAO_NATIVE_APP_KEY")

android {
    namespace = "com.careercompass.careercompass_fe"

    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        applicationId = "com.cambridge.careercompass_fe"
        versionCode = resolveCareerCompassVersionCode(System.getenv(CAREERCOMPASS_VERSION_CODE_ENV))
        versionName = "1.0"

        testInstrumentationRunner = "com.careercompass.careercompass_fe.test.CareerCompassTestRunner"
        testInstrumentationRunnerArguments["clearPackageData"] = "true"

        manifestPlaceholders["KAKAO_NATIVE_APP_KEY"] = kakaoKey
        buildConfigField("String", "KAKAO_NATIVE_APP_KEY", "\"$kakaoKey\"")
    }

    testOptions {
        animationsDisabled = true
        execution = "ANDROIDX_TEST_ORCHESTRATOR"
        managedDevices {
            localDevices {
                create("pixel2Api26") {
                    device = "Pixel 2"
                    apiLevel = 26
                    systemImageSource = "aosp"
                }
                create("pixel2Api30") {
                    device = "Pixel 2"
                    apiLevel = 30
                    systemImageSource = "aosp"
                }
                create("pixel2Api34") {
                    device = "Pixel 2"
                    apiLevel = 34
                    systemImageSource = "aosp"
                }
                create("pixel2Api36") {
                    device = "Pixel 2"
                    apiLevel = 36
                    systemImageSource = "aosp"
                }
            }
        }
    }

    signingConfigs {
        // 네 키를 하나의 단위로 다루는 근거는 아래 debug 주석과 같다. 다른 점은 폴백 대상이
        // 없다는 것뿐이라, 전부 미기재면 release 빌드만 끊는다.
        create("release") {
            val releaseSigningKeys =
                listOf(
                    "RELEASE_STORE_FILE",
                    "RELEASE_STORE_PASSWORD",
                    "RELEASE_KEY_ALIAS",
                    "RELEASE_KEY_PASSWORD",
                )
            val provided =
                releaseSigningKeys
                    .mapNotNull { key ->
                        localProperties.getProperty(key)?.takeIf { it.isNotBlank() }?.let { key to it }
                    }.toMap()

            when (provided.size) {
                0 -> {
                    requireReleaseSigningForReleaseBuild(releaseSigningKeys)
                }

                releaseSigningKeys.size -> {
                    val releaseKeystore = file(provided.getValue("RELEASE_STORE_FILE"))
                    if (!releaseKeystore.isFile) {
                        throw GradleException(
                            """
                            |RELEASE_STORE_FILE 이 가리키는 release keystore 를 찾을 수 없습니다: $releaseKeystore
                            |파일을 해당 경로에 두거나, RELEASE_* 네 키를 모두 지우세요.
                            |인계·설정 방법은 docs/release/distribution.md 의 「운영 자격 인계」 절 참고.
                            """.trimMargin(),
                        )
                    }
                    storeFile = releaseKeystore
                    storePassword = provided.getValue("RELEASE_STORE_PASSWORD")
                    keyAlias = provided.getValue("RELEASE_KEY_ALIAS")
                    keyPassword = provided.getValue("RELEASE_KEY_PASSWORD")
                }

                else -> {
                    throw GradleException(
                        """
                        |release 서명 설정이 불완전합니다.
                        |루트 local.properties 누락 항목: ${(releaseSigningKeys - provided.keys).joinToString()}
                        |네 키는 하나의 단위입니다 — 모두 채우거나 모두 지우세요.
                        |인계·설정 방법은 docs/release/distribution.md 의 「운영 자격 인계」 절 참고.
                        """.trimMargin(),
                    )
                }
            }
        }
        // 팀 공유 debug keystore 로 서명해 카카오 키 해시를 머신 간 통일한다. opt-in 이라
        // `DEBUG_*` 미기재 머신·CI 는 AGP 기본 `~/.android/debug.keystore` 로 폴백하고, 그때는
        // 본인 해시를 카카오 콘솔에 등록하면 된다(https://developer.android.com/studio/publish/app-signing).
        //
        // 트레이드오프: 파일이 유출되면 제3자가 같은 키 해시로 우리 앱 행세를 할 수 있다. 영향은
        // debug 한정이고(release 는 별도 keystore, debug 서명 앱은 스토어 배포 불가) 실수 커밋은
        // `.gitignore` 가 막지만, 전달 경로는 개인 채널로 유지한다.
        //
        // 네 키는 하나의 단위로 다룬다. `DEBUG_STORE_FILE` 만 보고 진입하면 자격 값이 null 로
        // 덮여 폴백도 아니고 원인도 안 보이는 서명 실패가 난다.
        getByName("debug") {
            val debugSigningKeys =
                listOf(
                    "DEBUG_STORE_FILE",
                    "DEBUG_STORE_PASSWORD",
                    "DEBUG_KEY_ALIAS",
                    "DEBUG_KEY_PASSWORD",
                )
            val provided =
                debugSigningKeys
                    .mapNotNull { key ->
                        localProperties.getProperty(key)?.takeIf { it.isNotBlank() }?.let { key to it }
                    }.toMap()

            when (provided.size) {
                0 -> {
                    // 전부 미기재 — AGP 기본 debug keystore 로 서명(공유 keystore 미수령 머신·CI).
                }

                debugSigningKeys.size -> {
                    val sharedKeystore = file(provided.getValue("DEBUG_STORE_FILE"))
                    if (!sharedKeystore.isFile) {
                        throw GradleException(
                            """
                            |DEBUG_STORE_FILE 이 가리키는 공유 debug keystore 를 찾을 수 없습니다: $sharedKeystore
                            |파일을 해당 경로에 두거나, DEBUG_* 네 키를 모두 지워 기본 debug keystore 로 되돌리세요.
                            |keystore 파일은 팀에서 개인 채널로 받는다. 카카오 키 해시는 docs/release/credentials.md 의 「카카오」 절 참고.
                            """.trimMargin(),
                        )
                    }
                    storeFile = sharedKeystore
                    storePassword = provided.getValue("DEBUG_STORE_PASSWORD")
                    keyAlias = provided.getValue("DEBUG_KEY_ALIAS")
                    keyPassword = provided.getValue("DEBUG_KEY_PASSWORD")
                }

                else -> {
                    throw GradleException(
                        """
                        |공유 debug keystore 설정이 불완전합니다.
                        |루트 local.properties 누락 항목: ${(debugSigningKeys - provided.keys).joinToString()}
                        |네 키는 하나의 단위입니다 — 모두 채우거나 모두 지우세요(모두 지우면 기본 debug keystore 로 서명).
                        |keystore 파일은 팀에서 개인 채널로 받는다. 카카오 키 해시는 docs/release/credentials.md 의 「카카오」 절 참고.
                        """.trimMargin(),
                    )
                }
            }
        }
    }

    buildTypes {
        debug {
            // 설치된 앱이 어느 커밋으로 빌드됐는지 `adb shell dumpsys package` 한 줄로 읽히게 한다.
            // 실기 QA 증거는 전체 커밋 sha 로 대장에 남으므로(`docs/qa/evidence/<full-head-sha>.json`),
            // 앱이 스스로 커밋을 들고 있지 않으면 검증한 코드를 특정할 수 없다(build-logic 의 BuildFingerprint).
            // release `versionName` 은 사용자에게 보이므로 건드리지 않는다.
            versionNameSuffix = resolveDebugVersionNameSuffix()
        }

        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            signingConfig = signingConfigs.getByName("release")
            firebaseAppDistribution {
                groups = "careercompass"
            }
        }
    }
}

baselineProfile {
    // 생성은 주간/manual workflow가 소유한다. 일반 release 빌드가 에뮬레이터를 암묵적으로
    // 띄우지 않으며, 검토 가능한 단일 profile을 source에 저장해 패키징한다.
    automaticGenerationDuringBuild = false
    mergeIntoMain = true
    saveInSrc = true
    dexLayoutOptimization = true
}

dependencies {
    implementation(libs.coil.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.core.splashscreen)
    // 지문 로그인의 BiometricPrompt 가 FragmentActivity 를 요구한다 — MainActivity 의 부모 클래스.
    implementation(libs.androidx.fragment.ktx)

    // App Startup — 기동 초기화는 app 매니페스트에 등록한 Initializer 로 실행한다.
    // WorkManager 는 두지 않는다 — 아무 모듈도 쓰지 않는데 의존만 있으면 WorkManagerInitializer 가 App Startup 으로
    // 매 콜드 스타트에 붙는다(#74). 쓰는 모듈이 생기면 그 모듈이 의존을 선언한다.
    implementation(libs.androidx.startup.runtime)
    implementation(libs.androidx.profileinstaller)

    // 카카오 OAuth redirect Activity(`com.kakao.sdk.auth.AuthCodeHandlerActivity`)를
    // app 매니페스트에서 직접 참조하므로 컴파일 classpath에 노출 필요.
    implementation(libs.kakao.sdk.auth)

    // Firebase — Crashlytics 는 크래시 자동 수집이라 초기화 코드가 필요 없다.
    // 버전은 BoM 이 관리하므로 개별 좌표에는 버전을 적지 않는다.
    // Analytics 는 넣지 않는다. breadcrumb 이 그것에 의존하지만, 얻는 값보다 자동 수집되는
    // 사용자 데이터 범위가 커서 제외했다 — 실패 지점은 `<흐름>_stage` 키로 직접 남긴다.
    // 그래서 로그에 "Could not register handler for breadcrumbs events" 가 뜨는 건 정상이다.
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.crashlytics)
    implementation(libs.firebase.messaging)

    // Core
    implementation(projects.core.common)
    implementation(projects.core.network)
    implementation(projects.core.ui)
    implementation(projects.core.model)
    implementation(projects.core.data)
    // 로컬 저장소 창구는 core:datastore 의 LocalStoreRegistry 이고, 스키마(타입드 접근자)는 각 모듈이 갖는다.
    // app 이 직접 의존하는 이유는 계측 테스트의 fake 주입과 레지스트리 초기화다.
    implementation(projects.core.datastore)
    implementation(libs.androidx.datastore.preferences)
    implementation(projects.core.domain)

    // Feature — presentation
    implementation(projects.feature.onboarding.presentation)
    implementation(projects.feature.feed.presentation)
    implementation(projects.feature.editor.presentation)
    implementation(projects.feature.profile.presentation)
    implementation(projects.feature.foryou.presentation)
    implementation(projects.feature.notification.presentation)

    // Feature — domain (내비게이션 인자·화면 계약이 도메인 타입을 받는다)
    implementation(projects.feature.onboarding.domain)
    implementation(projects.feature.feed.domain)
    implementation(projects.feature.editor.domain)
    implementation(projects.feature.profile.domain)
    implementation(projects.feature.foryou.domain)
    implementation(projects.feature.notification.domain)

    // Feature — data (Hilt @Module 바인딩이 루트 그래프에 포함되도록 app 이 classpath 에 둔다)
    implementation(projects.feature.onboarding.data)
    implementation(projects.feature.feed.data)
    implementation(projects.feature.editor.data)
    implementation(projects.feature.profile.data)
    implementation(projects.feature.foryou.data)
    implementation(projects.feature.notification.data)

    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(projects.core.domain))

    baselineProfile(project(":baselineprofile"))

    // Managed-device androidTest — 실제 서버·OAuth 대신 Hilt fake를 주입하고 Compose semantics를 검증한다.
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.core.ktx)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.paging.runtime)
    androidTestImplementation(libs.hilt.android.testing)
    androidTestImplementation(libs.coroutines.test)
    androidTestImplementation(projects.core.data)
    androidTestImplementation(testFixtures(projects.core.domain))
    androidTestImplementation(projects.feature.onboarding.domain)
    androidTestImplementation(testFixtures(projects.feature.onboarding.domain))
    androidTestImplementation(projects.feature.feed.domain)
    androidTestImplementation(testFixtures(projects.feature.feed.domain))
    androidTestImplementation(projects.feature.editor.domain)
    androidTestImplementation(testFixtures(projects.feature.editor.domain))
    androidTestImplementation(projects.feature.profile.domain)
    androidTestImplementation(testFixtures(projects.feature.profile.domain))
    androidTestImplementation(projects.feature.foryou.domain)
    androidTestImplementation(testFixtures(projects.feature.foryou.domain))
    androidTestImplementation(projects.feature.notification.domain)
    androidTestImplementation(testFixtures(projects.feature.notification.domain))
    kspAndroidTest(libs.hilt.compiler)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    androidTestUtil(libs.androidx.test.orchestrator)
}
