// AGP 9.3.2·Firebase App Distribution 5.3.0 이 buildscript classpath 로 끌어오는 전이 의존성 중
// 보안 권고 영향권인 것들의 하한(#921·#975~#985). 루트 classpath 는 plugins 블록 처리 시점에
// 리졸브가 끝나 아래 본문 훅으로는 늦고, buildscript 블록에서는 버전 카탈로그 accessor 를 쓸 수 없다
// — libs.versions.toml 의 같은 이름 버전과 값을 맞춰 유지할 것.
buildscript {
    dependencies {
        constraints {
            listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { artifact ->
                add("classpath", "org.bouncycastle:$artifact:1.85") {
                    because("GHSA-9pwp-9qqc-pr26·GHSA-qp49-qgx5-5m26 2건, 1.85 미만 취약 (#427)")
                }
            }
            add("classpath", "org.apache.commons:commons-lang3:3.18.0") {
                because("GHSA-j288-q9x7-2f5v — 3.18.0 미만 취약 — #981")
            }
            add("classpath", "org.bitbucket.b_c:jose4j:0.9.6") {
                because("GHSA-3677-xxcr-wjqv — 0.9.6 미만 취약 — #982")
            }
            add("classpath", "org.jdom:jdom2:2.0.6.1") {
                because("GHSA-2363-cqg2-863c — 2.0.6.1 미만 취약 — #985")
            }
        }
        // netty 는 grpc-netty 가 끌어오는 모듈이 10개가 넘고 서로 같은 버전이어야 해 BOM 으로 정렬한다
        // (GHSA-558v-64gr-wgg4 등 33건, #975~#980). 이 BOM 을 아래 본문의 allprojects 하한에 platform()
        // 으로 함께 걸면 AGP 의 android-reverse-meta-data usage 가 platform 변형을 못 찾아 리졸브가 깨진다.
        // 그래서 루트 classpath 는 이 BOM 이 맡고, AGP 가 만드는 UTP 설정 경유분은 아래 securityFloors 의
        // 평범한 constraint 가 맡는다(#1058).
        add("classpath", platform("io.netty:netty-bom:4.1.137.Final"))
    }
}

// Top-level build file where you can add configuration options common to all sub-projects/modules.
plugins {
    id("careercompass.kover")
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.jetbrains.kotlin.jvm) apply false
    alias(libs.plugins.hilt.android) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.google.services) apply false
    alias(libs.plugins.firebase.app.distribution) apply false
    alias(libs.plugins.firebase.crashlytics) apply false
}

// 빌드·테스트 클래스패스의 보안 하한(#921·#975~#985·#1058·#1072·#1262). 상류가 취약 버전을 물고 있고 상류
// 최신판도 아직 패치 버전 미만이라 constraint 로 올린다 — Robolectric 4.15.1(bcprov 1.80)·AGP
// 9.3.2(bcprov 1.79·commons-lang3 3.16.0·jose4j 0.9.5·jdom2 2.0.6)·Firebase App Distribution 5.3.0
// 과 AGP UTP 설정(netty — unified-test-platform-core 가 4.1.93, -host-emulator-control 이 4.1.110)
// ·AGP androidLintTool 과 UTP(httpclient 4.5.6)·ktlint CLI(logback 1.3.16)·Compose Accessibility Test
// Framework(protobuf-javalite 3.19.1)·Kakao SDK 2.23.2(okhttp 4.9.2→okio 2.8.0). androidLintTool·UTP
// 처럼 AGP 가 뒤늦게 만드는 configuration 까지 잡도록
// configureEach 로 걸고, require 시맨틱이라 상류가 하한 이상을 선언하게 되면 그쪽이 이긴다. okhttp 는
// production 런타임, protobuf-javalite 는 androidTest 런타임에만 있고 나머지는 빌드 도구 경유라
// release APK 에 없다(releaseRuntimeClasspath 실측). build-logic 은 별도 빌드라
// build-logic/build.gradle.kts 가 같은 하한을 선언한다.
data class SecurityFloor(
    val module: String,
    val version: String,
    val because: String,
)

val securityFloors =
    listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").map { artifact ->
        SecurityFloor(
            module = "org.bouncycastle:$artifact",
            version = libs.versions.bouncycastle.get(),
            because = "GHSA-9pwp-9qqc-pr26·GHSA-qp49-qgx5-5m26 2건, 1.85 미만 취약 (#427)",
        )
    } +
        listOf(
            "netty-buffer",
            "netty-codec",
            "netty-codec-http",
            "netty-codec-http2",
            "netty-codec-socks",
            "netty-common",
            "netty-handler",
            "netty-handler-proxy",
            "netty-resolver",
            "netty-transport",
            "netty-transport-native-unix-common",
        ).map { artifact ->
            SecurityFloor(
                module = "io.netty:$artifact",
                version = libs.versions.netty.get(),
                because = "GHSA-558v-64gr-wgg4 등 33건 — AGP UTP 경유 4.1.110·4.1.93 잔존 — #1058",
            )
        } +
        listOf(
            SecurityFloor(
                module = "org.apache.commons:commons-lang3",
                version = libs.versions.commonsLang3.get(),
                because = "GHSA-j288-q9x7-2f5v — 3.18.0 미만 취약 — #981",
            ),
            SecurityFloor(
                module = "org.bitbucket.b_c:jose4j",
                version = libs.versions.jose4j.get(),
                because = "GHSA-3677-xxcr-wjqv — 0.9.6 미만 취약 — #982",
            ),
            SecurityFloor(
                module = "org.jdom:jdom2",
                version = libs.versions.jdom2.get(),
                because = "GHSA-2363-cqg2-863c — 2.0.6.1 미만 취약 — #985",
            ),
            SecurityFloor(
                module = "org.apache.httpcomponents:httpclient",
                version = libs.versions.httpclient.get(),
                because = "GHSA-7r82-7xv7-xcpj — 4.5.13 미만 취약 — #1072",
            ),
            SecurityFloor(
                module = "com.google.protobuf:protobuf-javalite",
                version = libs.versions.protobufJavalite.get(),
                because =
                    "GHSA-4gg5-vx3j-xwc7·GHSA-735f-pc8j-v9w8 — " +
                        "Accessibility Test Framework 4.1.1 경유 3.19.1 잔존 — #1262",
            ),
        ) +
        // logback-core 만 취약하지만 classic 은 core 와 같은 버전이라야 동작해 함께 올린다.
        listOf("logback-core", "logback-classic").map { artifact ->
            SecurityFloor(
                module = "ch.qos.logback:$artifact",
                version = libs.versions.logback.get(),
                because = "GHSA-jhq6-gfmj-v8fx 등 3건 — 1.5.34 미만 취약 — #1072",
            )
        } +
        // okio 2.8.0(GHSA-w33c-445m-f8w7)이 Kakao SDK 경유 okhttp 4.9.2 로 딸려 온다. okhttp 4.9.x 는
        // okio 2.x API 를 쓰므로 okio 만 3.x 로 올리면 런타임이 깨진다. 그래서 okio 가 아니라 okhttp 를
        // 카탈로그 버전으로 정렬해 okio 를 함께 끌어올린다 — :app 최종 조합이 이미 그 짝(okhttp 5.4.0 +
        // okio-jvm 3.17.0)이라, 카탈로그 okhttp 를 직접 의존하지 않는 feature 모듈의 컴파일 클래스패스만
        // 뒤처져 있던 것을 맞추는 셈이다. logging-interceptor 는 okhttp 와 같은 버전이라야 한다.
        listOf("okhttp", "logging-interceptor").map { artifact ->
            SecurityFloor(
                module = "com.squareup.okhttp3:$artifact",
                version = libs.versions.okhttp.get(),
                because = "GHSA-w33c-445m-f8w7 — okhttp 4.9.2 가 물고 오는 okio 2.8.0 취약 — #1072",
            )
        }

fun Project.securityFloorConstraintSet() =
    securityFloors.map { floor ->
        dependencies.constraints.create("${floor.module}:${floor.version}") {
            because(floor.because)
        }
    }

allprojects {
    // Gradle 9 는 declarable 이 아닌 configuration(compileClasspath 등)에 constraint 선언을 금지한다.
    // 전용 declarable configuration 에 담아 모든 resolvable configuration 이 extend 하게 한다.
    val floor =
        configurations.create("securityFloors") {
            isCanBeResolved = false
            isCanBeConsumed = false
            securityFloorConstraintSet().forEach { dependencyConstraints.add(it) }
        }
    configurations.configureEach {
        if (name != floor.name && isCanBeResolved) extendsFrom(floor)
    }
}
// buildscript 컨테이너에는 configuration 을 추가할 수 없어 declarable 인 classpath 에 constraint 를
// 직접 단다. 루트 자신의 classpath 는 최상단 buildscript 블록이 담당한다(여기 시점엔 리졸브 완료라 불가).
subprojects {
    buildscript.configurations.named("classpath") {
        securityFloorConstraintSet().forEach { dependencyConstraints.add(it) }
    }
}

// 커버리지 규약(kover 적용·리포트 필터·«ci» 변형)은 careercompass.kover convention plugin 이 모듈마다
// 스스로 건다. 루트에 남는 것은 «무엇을 합칠지» 뿐이다 — 예전에는 여기서 subprojects 를
// configuration time 에 열거해 남의 프로젝트의 pluginManager·extensions 를 직접 조작했고,
// 이 저장소에서 모듈 공통 설정이 convention plugin 밖에 있는 유일한 자리이자 Gradle
// project isolation 의 첫 걸림돌이었다(#918 — #864 승인 리뷰의 유예분).
//
// 모듈이 늘면 여기 한 줄과 그 모듈 plugins 블록의 careercompass.kover 가 함께 늘어야 한다. 둘이
// 어긋나면 커버리지에서 조용히 빠지므로 build-logic 의 KoverAggregationTest 가 일치를 검사한다.
dependencies {
    kover(project(":app"))
    kover(project(":core:common"))
    kover(project(":core:data"))
    kover(project(":core:datastore"))
    kover(project(":core:domain"))
    kover(project(":core:model"))
    kover(project(":core:network"))
    kover(project(":core:ui"))
    kover(project(":feature:onboarding:data"))
    kover(project(":feature:onboarding:domain"))
    kover(project(":feature:onboarding:presentation"))
    kover(project(":feature:feed:data"))
    kover(project(":feature:feed:domain"))
    kover(project(":feature:feed:presentation"))
    kover(project(":feature:editor:data"))
    kover(project(":feature:editor:domain"))
    kover(project(":feature:editor:presentation"))
    kover(project(":feature:profile:data"))
    kover(project(":feature:profile:domain"))
    kover(project(":feature:profile:presentation"))
    kover(project(":feature:foryou:data"))
    kover(project(":feature:foryou:domain"))
    kover(project(":feature:foryou:presentation"))
    kover(project(":feature:notification:data"))
    kover(project(":feature:notification:domain"))
    kover(project(":feature:notification:presentation"))
}
