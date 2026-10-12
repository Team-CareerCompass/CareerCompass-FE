plugins {
    `kotlin-dsl`
    `kotlin-dsl-precompiled-script-plugins`
    alias(libs.plugins.ktlint)
}

ktlint {
    version.set(libs.versions.ktlint)
    filter {
        exclude { it.file.path.contains("build/") }
    }
}

dependencies {
    implementation(libs.android.gradlePlugin)
    implementation(libs.kotlin.gradlePlugin)
    implementation(libs.ktlint.gradle)
    implementation(libs.kover.gradlePlugin)
    compileOnly(libs.compose.compiler.gradle.plugin)
    compileOnly("org.jetbrains.kotlin:kotlin-serialization:${libs.versions.kotlin.get()}")
    testImplementation(libs.junit)
    testImplementation(gradleTestKit())

    // 루트 build.gradle.kts 의 보안 하한은 별도 빌드인 여기까지 미치지 않는다 — 같은 근거(#921·#981·
    // #982·#985). AGP 9.3.2 이 이 클래스패스에도 같은 취약 버전을 끌어온다(netty 는 여기 없다).
    constraints {
        listOf("bcprov-jdk18on", "bcpkix-jdk18on", "bcutil-jdk18on").forEach { artifact ->
            implementation("org.bouncycastle:$artifact:${libs.versions.bouncycastle.get()}") {
                because("GHSA-9pwp-9qqc-pr26·GHSA-qp49-qgx5-5m26 2건, 1.85 미만 취약 (#427)")
            }
        }
        implementation("org.apache.commons:commons-lang3:${libs.versions.commonsLang3.get()}") {
            because("GHSA-j288-q9x7-2f5v — 3.18.0 미만 취약 — #981")
        }
        implementation("org.bitbucket.b_c:jose4j:${libs.versions.jose4j.get()}") {
            because("GHSA-3677-xxcr-wjqv — 0.9.6 미만 취약 — #982")
        }
        implementation("org.jdom:jdom2:${libs.versions.jdom2.get()}") {
            because("GHSA-2363-cqg2-863c — 2.0.6.1 미만 취약 — #985")
        }
    }
}

tasks.withType<Test>().configureEach {
    // TestKit 스텁 프로젝트가 buildscript classpath 로 주입할 가드 클래스 위치 (ReleaseKeyGuardTest 참고).
    systemProperty(
        "guardClasspath",
        sourceSets.main
            .get()
            .output.classesDirs.asPath,
    )
}

gradlePlugin {
    plugins {
        register("androidLibrary") {
            id = "careercompass.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidLibraryCompose") {
            id = "careercompass.android.library.compose"
            implementationClass = "AndroidLibraryComposeConventionPlugin"
        }
        register("androidHilt") {
            id = "careercompass.android.hilt"
            implementationClass = "AndroidHiltConventionPlugin"
        }
        register("androidRetrofit") {
            id = "careercompass.android.retrofit"
            implementationClass = "AndroidRetrofitConventionPlugin"
        }
        register("androidNavigation") {
            id = "careercompass.android.navigation"
            implementationClass = "AndroidNavigationConventionPlugin"
        }
        register("androidFeature") {
            id = "careercompass.android.feature"
            implementationClass = "AndroidFeatureConventionPlugin"
        }
        register("androidData") {
            id = "careercompass.android.data"
            implementationClass = "AndroidDataConventionPlugin"
        }
        register("androidDomain") {
            id = "careercompass.android.domain"
            implementationClass = "AndroidDomainConventionPlugin"
        }
        register("jvmLibrary") {
            id = "careercompass.jvm.library"
            implementationClass = "JvmLibraryConventionPlugin"
        }
        register("jvmDomain") {
            id = "careercompass.jvm.domain"
            implementationClass = "JvmDomainConventionPlugin"
        }
        register("androidApplication") {
            id = "careercompass.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("androidLint") {
            id = "careercompass.android.lint"
            implementationClass = "AndroidLintConventionPlugin"
        }
        register("jvmLint") {
            id = "careercompass.jvm.lint"
            implementationClass = "JvmLintConventionPlugin"
        }
        register("androidDatastore") {
            id = "careercompass.android.datastore"
            implementationClass = "AndroidDatastoreConventionPlugin"
        }
        register("kover") {
            id = "careercompass.kover"
            implementationClass = "CareerCompassKoverConventionPlugin"
        }
    }
}
