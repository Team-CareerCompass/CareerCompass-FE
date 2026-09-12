plugins {
    id("careercompass.android.datastore")
    id("careercompass.kover")
}

android {
    namespace = "com.careercompass.core.datastore"
    testOptions.unitTests.isReturnDefaultValues = true
}

dependencies {
    // 저장소 손상은 조용히 지워지면 재발을 셀 수 없다 — ErrorReporter 계약만 쓰고 구현은 app 이 준다.
    implementation(projects.core.common)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.coroutines.core)

    testImplementation(libs.coroutines.test)
    testImplementation(libs.robolectric)
}
