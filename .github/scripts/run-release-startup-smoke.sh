#!/usr/bin/env bash

# 릴리스 산출물 기동 스모크.
#
# R8 이 만든 런타임 결함은 빌드·서명·크기 검증을 전부 통과한다 — 이름을 «문자열로» 찾는 코드
# (navigation 의 enum 인자, 직렬화 serialName, 리플렉션)는 컴파일도 되고 패키징도 되지만 기동에서
# 죽는다. #1753 이 그랬다: nav enum keep 이 없어 배포본이 스플래시도 못 넘겼는데 릴리스 PR 의
# 체크는 전부 초록이었다. 최소 조건은 하나다 — «배포될 그 APK 가 실제로 켜지는가».
#
# 대상은 preflight 가 이미 만든 universal APK 다. 여기서 새로 빌드하지 않으므로 검증 대상과
# 배포 대상이 갈라지지 않는다.

set -euo pipefail

: "${RELEASE_SMOKE_APK_PATH:?RELEASE_SMOKE_APK_PATH is required}"
: "${ANDROID_HOME:?ANDROID_HOME is required}"

# 기기에서 앱을 찾는 이름은 namespace 가 아니라 applicationId 다. 이 저장소는 둘이 다르고,
# namespace 만 옮겨졌을 때(#220) 이 값이 조용히 스테일해져 preflight 가 런처 액티비티를 못 찾았다.
# release-aab-preflight.test.mjs 가 이 줄과 app/build.gradle.kts 의 applicationId 를 대조한다.
package_name="com.cambridge.careercompass_fe"
# 기기 관련 값은 로컬에서 이 스크립트를 그대로 돌려 볼 수 있도록 덮어쓸 수 있게 둔다.
avd_name="${RELEASE_SMOKE_AVD:-careercompass-release-smoke}"
system_image="${RELEASE_SMOKE_SYSTEM_IMAGE:-system-images;android-34;default;x86_64}"
emulator_port="${RELEASE_SMOKE_PORT:-5554}"
serial="emulator-${emulator_port}"
boot_timeout_seconds=420
# 기동 «직후» 가 아니라 잠시 살아 있는지를 본다 — NavHost 조립 크래시는 첫 프레임 언저리에 난다.
settle_seconds=20

adb="${ANDROID_HOME}/platform-tools/adb"
sdkmanager="${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager"
avdmanager="${ANDROID_HOME}/cmdline-tools/latest/bin/avdmanager"
logcat_dump="${RUNNER_TEMP:-/tmp}/release-startup-smoke-logcat.txt"
emulator_log="${RUNNER_TEMP:-/tmp}/release-startup-smoke-emulator.txt"

[[ -s "${RELEASE_SMOKE_APK_PATH}" ]] || {
    echo "::error::스모크 대상 APK 가 없습니다: ${RELEASE_SMOKE_APK_PATH}"
    exit 1
}

emulator_pid=""
cleanup() {
    "${adb}" -s "${serial}" emu kill >/dev/null 2>&1 || true
    if [[ -n "${emulator_pid}" ]]; then
        kill "${emulator_pid}" >/dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

fail() {
    local message="$1"
    echo "::error::${message}"
    if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
        {
            echo "### Release startup smoke: FAIL"
            echo "- ${message}"
            if [[ -s "${logcat_dump}" ]]; then
                echo ""
                echo '```'
                grep -E "FATAL EXCEPTION|AndroidRuntime|${package_name}" "${logcat_dump}" | tail -n 60
                echo '```'
            fi
        } >> "${GITHUB_STEP_SUMMARY}"
    fi
    exit 1
}

# avdmanager 와 emulator 가 서로 다른 AVD 홈을 보면 «만들었는데 없다» 가 된다 — 둘이 같은 곳을
# 보도록 여기서 못 박는다(emulator 는 $ANDROID_SDK_HOME/avd 를, avdmanager 는 $ANDROID_SDK_HOME/.android/avd
# 를 쓰는 조합이 있다).
export ANDROID_AVD_HOME="${ANDROID_AVD_HOME:-${HOME}/.android/avd}"
mkdir -p "${ANDROID_AVD_HOME}"

list_avds() {
    "${ANDROID_HOME}/emulator/emulator" -list-avds 2>/dev/null || true
}

# 준비는 멱등하게 — 재실행이나 이미 있는 기기를 파괴하지 않는다.
if ! list_avds | grep -qx "${avd_name}"; then
    "${sdkmanager}" --install "platform-tools" "emulator" "${system_image}"
    echo no | "${avdmanager}" create avd \
        --name "${avd_name}" \
        --package "${system_image}"
fi

# 생성이 조용히 어긋나면 다음 줄의 에뮬레이터가 «Unknown AVD name» 으로 죽는다. 원인을 여기서 말한다.
list_avds | grep -qx "${avd_name}" || {
    echo "사용 가능한 AVD: $(list_avds | tr '\n' ' ')"
    fail "AVD '${avd_name}' 를 만들지 못했습니다 (ANDROID_AVD_HOME=${ANDROID_AVD_HOME})."
}

"${ANDROID_HOME}/emulator/emulator" -avd "${avd_name}" -port "${emulator_port}" \
    -no-window -no-audio -no-boot-anim -no-snapshot \
    -gpu swiftshader_indirect \
    > "${emulator_log}" 2>&1 &
emulator_pid=$!

# 조기 중단은 «부팅이 실제로 불가능한» 표시에만 건다. 에뮬레이터는 정상 부팅 중에도 무해한
# ERROR 줄을 남기므로 ERROR 전체를 실패로 읽으면 앱을 설치하기도 전에 죽는다 (#1769).
# 그 밖의 이상은 부팅 데드라인이 잡는다 — 늦게 잡되 틀리지 않는 쪽이 낫다.
fatal_boot_line() {
    grep -m1 -E '^(PANIC:|ERROR +\| Unknown AVD name|ERROR +\| x86_64 emulation currently requires)' \
        "${emulator_log}" || true
}

boot_deadline=$(( $(date +%s) + boot_timeout_seconds ))
until [[ "$("${adb}" -s "${serial}" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    fatal_line="$(fatal_boot_line)"
    if [[ -n "${fatal_line}" ]]; then
        # 걸린 줄을 먼저 남긴다 — 꼬리 30줄에는 원인이 안 들어 있는 경우가 많다.
        echo "부팅 중단 표시: ${fatal_line}"
        tail -n 30 "${emulator_log}"
        fail "에뮬레이터가 기동하지 못했습니다: ${fatal_line}"
    fi
    if (( $(date +%s) > boot_deadline )); then
        tail -n 60 "${emulator_log}"
        fail "에뮬레이터 부팅이 ${boot_timeout_seconds}초 안에 끝나지 않았습니다."
    fi
    sleep 5
done

"${adb}" -s "${serial}" install -r "${RELEASE_SMOKE_APK_PATH}"

launcher_activity="$(
    "${adb}" -s "${serial}" shell cmd package resolve-activity --brief \
        -a android.intent.action.MAIN -c android.intent.category.LAUNCHER "${package_name}" |
        tr -d '\r' | tail -n 1
)"
[[ "${launcher_activity}" == "${package_name}/"* ]] ||
    fail "런처 액티비티를 찾지 못했습니다: ${launcher_activity}"

"${adb}" -s "${serial}" logcat -c
"${adb}" -s "${serial}" shell am start -W -n "${launcher_activity}"
sleep "${settle_seconds}"
"${adb}" -s "${serial}" logcat -d > "${logcat_dump}"

# 두 축을 모두 본다 — 크래시 뒤 시스템이 프로세스를 되살리면 pid 만으로는 통과해 버리고(#1753
# 실측에서 실제로 그랬다), 반대로 조용히 죽는 경우엔 logcat 에 FATAL 이 남지 않는다.
if grep -q "FATAL EXCEPTION" "${logcat_dump}"; then
    fail "기동 중 처리되지 않은 예외가 발생했습니다."
fi
if [[ -z "$("${adb}" -s "${serial}" shell pidof "${package_name}" | tr -d '\r')" ]]; then
    fail "기동 후 앱 프로세스가 살아 있지 않습니다."
fi

if [[ -n "${GITHUB_STEP_SUMMARY:-}" ]]; then
    {
        echo "### Release startup smoke: PASS"
        echo "- artifact: \`$(basename -- "${RELEASE_SMOKE_APK_PATH}")\`"
        echo "- device: \`${avd_name}\` (\`${system_image}\`)"
        echo "- launched: \`${launcher_activity}\`"
    } >> "${GITHUB_STEP_SUMMARY}"
fi
