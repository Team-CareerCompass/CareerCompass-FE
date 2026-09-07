package com.careercompass.feature.profile.presentation.basicinfo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.careercompass.core.model.user.JobOptionCatalog
import com.careercompass.core.model.user.MAX_JOB_INTERESTS
import com.careercompass.core.model.user.MAX_PROFILE_TAGS
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassFailureState
import com.careercompass.core.ui.component.CareerCompassTag
import com.careercompass.core.ui.component.CareerCompassTextField
import com.careercompass.core.ui.component.CareerCompassTextFieldSize
import com.careercompass.core.ui.component.CareerCompassTopAppBar
import com.careercompass.core.ui.failure.FailureSurface
import com.careercompass.core.ui.failure.display
import com.careercompass.core.ui.failure.toMessage
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R

/**
 * 눌리는 칩의 최소 높이 — WCAG 2.5.5 · ATF `TouchTargetSizeCheck` 가 48dp 를 요구한다.
 *
 * `CareerCompassTag` 자체는 43dp 로 그려진다. 부품 쪽을 키우는 것이 옳지만 그 칩은 `core:ui` 의 골든
 * 매트릭스에 들어 있어 baseline 재생성이 따라붙는다 — 이 화면은 호출부에서 하한을 걸고, 부품 수정은
 * core 담당 이슈로 남긴다.
 */
private val TAG_MIN_TOUCH_HEIGHT = 48.dp

/**
 * 프로필 편집(Figma 05 · 02) — 상태 없는 본문. 프리뷰 · Robolectric 의 진입점이다.
 *
 * ### 이 화면의 엣지 상태 (`docs/spec/edge-states.md` §6)
 * 1. **오프라인·네트워크 실패** — 프리필할 값이 없을 때만 화면을 덮는다(실패 표 #204). 저장 실패는 입력을
 *    그대로 둔 채 스낵바 한 줄이다 — 사용자가 친 글자를 실패 화면으로 덮으면 다시 쳐야 한다.
 * 2. **로딩** — 첫 조회에만 진행 표시. 캐시가 있으면 칸이 이미 차 있으므로 그리지 않는다.
 * 3. **빈 결과** — 없다. 프로필은 로그인한 사용자에게 언제나 하나 있다.
 * 4. **권한 거부** — 없다.
 * 5. **서버 점검(503)** — 1번과 같은 길.
 * 6. **세션 만료(401)** — 그리지 않는다. 셸이 로그인으로 보낸다.
 * 7. **되돌릴 길** — 실패 화면에도 상단 바의 뒤로 가기가 남는다.
 */
@Composable
public fun ProfileEditContent(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(colors.subtleSurface),
    ) {
        CareerCompassTopAppBar(
            title = stringResource(R.string.profile_edit_title),
            onBackClick = { onEvent(ProfileEditEvent.BackClicked) },
        )
        when {
            state.isFailureVisible -> {
                val display = requireNotNull(state.loadFailure).display(surface = FailureSurface.Unspecified)
                CareerCompassFailureState(
                    display = display,
                    onActionClick = if (display.isRetryable) ({ onEvent(ProfileEditEvent.RetryClicked) }) else null,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            state.isLoading -> {
                ProfileEditLoading()
            }

            else -> {
                Column(
                    modifier =
                        Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(spacing.large),
                    verticalArrangement = Arrangement.spacedBy(spacing.medium),
                ) {
                    ProfileEditSectionTitle(stringResource(R.string.profile_edit_section_basic))
                    ProfileEditFields(state = state, onEvent = onEvent)
                    ProfileEditSectionTitle(stringResource(R.string.profile_edit_section_interests))
                    ProfileEditInterests(state = state, onEvent = onEvent)
                    CareerCompassButton(
                        text =
                            stringResource(
                                if (state.isSaving) R.string.profile_edit_saving else R.string.profile_edit_save,
                            ),
                        onClick = { onEvent(ProfileEditEvent.SaveClicked) },
                        modifier = Modifier.fillMaxWidth(),
                        size = CareerCompassButtonSize.Large,
                        enabled = state.isSaveEnabled,
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileEditFields(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    CareerCompassTextField(
        value = state.name,
        onValueChange = { onEvent(ProfileEditEvent.NameChanged(it)) },
        label = stringResource(R.string.profile_edit_name_label),
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.profile_edit_name_placeholder),
        errorMessage = state.errorMessageFor(ProfileEditField.Name, state.nameError),
        isError = state.hasErrorOn(ProfileEditField.Name, state.nameError),
        enabled = !state.isSaving,
        size = CareerCompassTextFieldSize.Large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    CareerCompassTextField(
        value = state.school,
        onValueChange = {},
        label = stringResource(R.string.profile_edit_school_label),
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.profile_edit_school_placeholder),
        errorMessage = state.errorMessageFor(ProfileEditField.School, state.schoolError),
        isError = state.hasErrorOn(ProfileEditField.School, state.schoolError),
        enabled = !state.isSaving,
        readOnly = true,
        size = CareerCompassTextFieldSize.Large,
        onClick = { onEvent(ProfileEditEvent.SchoolPickerClicked) },
    )
    CareerCompassTextField(
        value = state.department,
        onValueChange = { onEvent(ProfileEditEvent.DepartmentChanged(it)) },
        label = stringResource(R.string.profile_edit_department_label),
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.profile_edit_department_placeholder),
        errorMessage = state.errorMessageFor(ProfileEditField.Department, state.departmentError),
        isError = state.hasErrorOn(ProfileEditField.Department, state.departmentError),
        enabled = !state.isSaving,
        size = CareerCompassTextFieldSize.Large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
    )
    CareerCompassTextField(
        value = state.gradePointAverage,
        onValueChange = { onEvent(ProfileEditEvent.GradePointAverageChanged(it)) },
        label = stringResource(R.string.profile_edit_gpa_label),
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.profile_edit_gpa_placeholder),
        supportingText = stringResource(R.string.profile_edit_gpa_support),
        errorMessage = state.errorMessageFor(ProfileEditField.GradePointAverage, state.gradePointAverageError),
        isError = state.hasErrorOn(ProfileEditField.GradePointAverage, state.gradePointAverageError),
        enabled = !state.isSaving,
        size = CareerCompassTextFieldSize.Large,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
    )
    CareerCompassTextField(
        value = state.graduationDate,
        onValueChange = {},
        label = stringResource(R.string.profile_edit_graduation_label),
        modifier = Modifier.fillMaxWidth(),
        placeholder = stringResource(R.string.profile_edit_graduation_placeholder),
        supportingText = stringResource(R.string.profile_edit_graduation_support),
        errorMessage = state.errorMessageFor(ProfileEditField.GraduationYear, state.graduationDateError),
        isError = state.hasErrorOn(ProfileEditField.GraduationYear, state.graduationDateError),
        enabled = !state.isSaving,
        readOnly = true,
        size = CareerCompassTextFieldSize.Large,
        onClick = { onEvent(ProfileEditEvent.GraduationPickerClicked) },
    )
}

@Composable
private fun ProfileEditSectionTitle(text: String) {
    Text(
        text = text,
        style = CareerCompassTheme.typography.labelMedium,
        color = CareerCompassTheme.colors.onSurface,
        modifier = Modifier.semantics { heading() },
    )
}

/**
 * 관심 분야 & 희망 직무(Figma 05 · 02 의 아래 절반) — 둘 다 **전체 교체**라 「고른 전부」가 곧 저장 값이다.
 *
 * 상한(직무 3 · 태그 5)을 라벨에 `현재/상한` 으로 늘 보인다. 닿았을 때만 알리면 사용자는 왜 더 못 고르는지
 * 누르고 나서야 안다 — 넘치는 순간의 스낵바는 그 위에 덧붙는 안내지 유일한 안내가 아니다.
 */
@Composable
private fun ProfileEditInterests(
    state: ProfileEditUiState,
    onEvent: (ProfileEditEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Text(
        text = stringResource(R.string.profile_edit_job_label, state.selectedJobCodes.size, MAX_JOB_INTERESTS),
        style = CareerCompassTheme.typography.caption,
        color = colors.onSurfaceVariant,
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        JobOptionCatalog.options.forEach { option ->
            val selected = option.code in state.selectedJobCodes
            CareerCompassTag(
                label = option.label,
                selected = selected,
                onClick = { onEvent(ProfileEditEvent.JobToggled(option.code)) },
                modifier = Modifier.heightIn(min = TAG_MIN_TOUCH_HEIGHT),
                // 상한에 닿아도 이미 고른 것은 눌러서 뺄 수 있어야 한다 — 잠그면 되돌릴 길이 없다.
                enabled = !state.isSaving && (selected || !state.isJobLimitReached),
            )
        }
    }
    if (state.selectedJobCodes.isEmpty()) {
        Text(
            text = stringResource(R.string.profile_edit_job_required),
            style = CareerCompassTheme.typography.caption,
            color = colors.error,
        )
    }

    Spacer(modifier = Modifier.height(spacing.small))
    Text(
        text = stringResource(R.string.profile_edit_tag_label, state.interestTags.size, MAX_PROFILE_TAGS),
        style = CareerCompassTheme.typography.caption,
        color = colors.onSurfaceVariant,
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CareerCompassTextField(
            value = state.interestInput,
            onValueChange = { onEvent(ProfileEditEvent.InterestInputChanged(it)) },
            label = stringResource(R.string.profile_edit_tag_label, state.interestTags.size, MAX_PROFILE_TAGS),
            modifier = Modifier.weight(1f),
            placeholder = stringResource(R.string.profile_edit_tag_placeholder),
            enabled = !state.isSaving && !state.isTagLimitReached,
            size = CareerCompassTextFieldSize.Large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onEvent(ProfileEditEvent.InterestTagAdded) }),
        )
        CareerCompassButton(
            text = stringResource(R.string.profile_edit_tag_add),
            onClick = { onEvent(ProfileEditEvent.InterestTagAdded) },
            size = CareerCompassButtonSize.Large,
            enabled = !state.isSaving && state.isTagAddEnabled,
        )
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.small),
    ) {
        state.interestTags.forEach { tag ->
            CareerCompassTag(
                label = stringResource(R.string.profile_edit_tag_display, tag),
                selected = true,
                onClick = { onEvent(ProfileEditEvent.InterestTagRemoved(tag)) },
                modifier = Modifier.heightIn(min = TAG_MIN_TOUCH_HEIGHT),
                enabled = !state.isSaving,
                stateDescription = stringResource(R.string.profile_edit_tag_remove, tag),
            )
        }
    }
    if (state.interestTags.isEmpty()) {
        Text(
            text = stringResource(R.string.profile_edit_tag_required),
            style = CareerCompassTheme.typography.caption,
            color = colors.error,
        )
    }
}

@Composable
private fun ProfileEditLoading() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(CareerCompassTheme.spacing.medium),
        ) {
            CircularProgressIndicator(color = CareerCompassTheme.colors.primaryEmphasis)
            Text(
                text = stringResource(R.string.profile_edit_loading),
                style = CareerCompassTheme.typography.bodyMedium,
                color = CareerCompassTheme.colors.onSurfaceVariant,
            )
        }
    }
}

/** 로컬 검증 오류가 먼저다 — 사용자가 지금 고칠 수 있는 것을 앞에 둔다. 서버 판정은 그 다음이다. */
private fun ProfileEditUiState.hasErrorOn(
    field: ProfileEditField,
    violation: ProfileFieldViolation?,
): Boolean = violation != null || serverRejectedField == field

@Composable
private fun ProfileEditUiState.errorMessageFor(
    field: ProfileEditField,
    violation: ProfileFieldViolation?,
): String? =
    when {
        violation != null -> violation.toMessage()
        serverRejectedField == field -> stringResource(R.string.profile_edit_field_rejected)
        else -> null
    }
