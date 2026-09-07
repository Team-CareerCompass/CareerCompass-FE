plugins {
    id("careercompass.jvm.domain")
    id("careercompass.kover")
}

dependencies {
    implementation(projects.core.model)
    implementation(projects.core.domain)
    implementation(libs.coroutines.core)

    testFixturesImplementation(projects.core.model)
    testFixturesImplementation(libs.coroutines.core)

    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    testImplementation(testFixtures(projects.core.domain))
}
