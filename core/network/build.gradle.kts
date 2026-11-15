plugins {
    id("careercompass.android.library")
    id("careercompass.android.retrofit")
    id("careercompass.android.hilt")
    id("careercompass.kover")
}

// 주소는 저장소 밖(local.properties·환경변수)에서 온다. 값이 없으면 자리표시자로 폴백하므로
// 백엔드 실주소(BE #7)가 도착하면 고칠 코드는 없고 키에 값만 넣으면 된다.
// 이 호출이 debug 가드까지 배선한다 — debug 가 운영 호스트를 가리키면 preDebugBuild 에서 끊는다.
val apiBaseUrls = apiBaseUrls()

android {
    namespace = "com.careercompass.core.network"
    buildFeatures {
        buildConfig = true
    }
    // 어느 빌드가 어느 주소를 쓰는지는 docs/api-base-url.md 가 정본이다.
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "BASE_URL", "\"${apiBaseUrls.debug}\"")
        }
        getByName("release") {
            buildConfigField("String", "BASE_URL", "\"${apiBaseUrls.release}\"")
        }
    }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(projects.core.common)
    implementation(projects.core.domain)
    implementation(projects.core.model)
    implementation(libs.coroutines.core)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.testcontainers.mockserver)
    testImplementation(testFixtures(projects.core.domain))
}
