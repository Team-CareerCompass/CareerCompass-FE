package com.careercompass.core.ui.component

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careercompass.core.model.experience.ExperienceType
import com.careercompass.core.model.experience.MAX_EXPERIENCE_TECH_TAGS
import com.careercompass.core.model.experience.MAX_EXPERIENCE_TECH_TAG_LENGTH
import com.careercompass.core.model.user.ProfileFieldViolation
import com.careercompass.core.ui.R
import com.careercompass.core.ui.failure.toMessage
import com.careercompass.core.ui.theme.CareerCompassTheme

/**
 * Step 3 「경험 추가·수정」 시트의 본문. 시트 컨테이너는 호스트가 감싼다.
 *
 * 유형 칩을 바꾸면 [ExperienceEditorRules] 에 따라 필드 라벨과 필수 표시가 바뀐다. 수정 중에는 유형을 잠근다 —
 * 유형마다 필드 의미가 달라, 바꾸면 이미 채운 값이 다른 뜻으로 저장된다.
 *
 * 유형별 상세 필드(F1-3)는 [ExperienceDetailSection] 이 「자세히」 안에 접어 둔다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
public fun ExperienceQuickAddSheet(
    state: ExperienceEditorState,
    onEvent: (ExperienceQuickAddEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val selectedState = stringResource(R.string.core_ui_experience_type_selected_state)
    val unselectedState = stringResource(R.string.core_ui_experience_type_unselected_state)

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = spacing.large, vertical = spacing.small),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text =
                stringResource(
                    if (state.isEditing) R.string.core_ui_experience_edit_title else R.string.core_ui_experience_add_title,
                ),
            modifier = Modifier.semantics { heading() },
            color = colors.onSurface,
            style = CareerCompassTheme.typography.headline4,
        )
        Text(
            text = stringResource(R.string.core_ui_experience_type_section),
            color = colors.onSurfaceVariant,
            style = CareerCompassTheme.typography.labelMedium,
        )
        if (state.isEditing) {
            // 잠긴 유형은 고를 수 없다는 것이 보이도록 선택된 칩 하나만 비활성으로 남긴다.
            CareerCompassTag(
                label = stringResource(state.type.labelResId()),
                selected = true,
                onClick = {},
                enabled = false,
                stateDescription = selectedState,
                role = Role.RadioButton,
            )
            Text(
                text = stringResource(R.string.core_ui_experience_type_locked),
                color = colors.mutedContent,
                style = CareerCompassTheme.typography.caption,
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth().selectableGroup(),
                horizontalArrangement = Arrangement.spacedBy(spacing.small),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                ExperienceType.entries.forEach { type ->
                    val selected = type == state.type
                    CareerCompassTag(
                        label = stringResource(type.labelResId()),
                        selected = selected,
                        onClick = { onEvent(ExperienceQuickAddEvent.TypeSelected(type)) },
                        enabled = state.isInputEnabled,
                        stateDescription = if (selected) selectedState else unselectedState,
                        role = Role.RadioButton,
                    )
                }
            }
        }
        CareerCompassTextField(
            value = state.title,
            onValueChange = { onEvent(ExperienceQuickAddEvent.TitleChanged(it)) },
            label = stringResource(R.string.core_ui_experience_title_label),
            placeholder = stringResource(R.string.core_ui_experience_title_placeholder),
            errorMessage = state.titleError?.let { it.toMessage() },
            isError = state.titleError != null,
            enabled = state.isInputEnabled,
            size = CareerCompassTextFieldSize.Large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(spacing.small)) {
            CareerCompassTextField(
                value = state.startDate,
                onValueChange = { onEvent(ExperienceQuickAddEvent.StartDateChanged(it)) },
                label = stringResource(startDateLabelResId(state.type)),
                modifier = Modifier.weight(1f),
                placeholder = stringResource(startDatePlaceholderResId(state.type)),
                errorMessage = state.startDateError?.let { it.toMessage() },
                isError = state.startDateError != null,
                enabled = state.isInputEnabled,
                size = CareerCompassTextFieldSize.Large,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            )
            if (ExperienceEditorRules.hasPeriod(state.type)) {
                CareerCompassTextField(
                    value = state.endDate,
                    onValueChange = { onEvent(ExperienceQuickAddEvent.EndDateChanged(it)) },
                    label = stringResource(R.string.core_ui_experience_end_label),
                    modifier = Modifier.weight(1f),
                    placeholder = stringResource(R.string.core_ui_experience_date_placeholder),
                    errorMessage = state.endDateError?.let { it.toMessage() },
                    isError = state.endDateError != null,
                    enabled = state.isInputEnabled,
                    size = CareerCompassTextFieldSize.Large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                )
            }
        }
        CareerCompassTextField(
            value = state.primary,
            onValueChange = { onEvent(ExperienceQuickAddEvent.PrimaryChanged(it)) },
            label = stringResource(primaryLabelResId(state.type)),
            errorMessage = state.primaryError?.let { it.toMessage() },
            isError = state.primaryError != null,
            enabled = state.isInputEnabled,
            size = CareerCompassTextFieldSize.Large,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )
        if (ExperienceEditorRules.hasSecondary(state.type)) {
            CareerCompassTextField(
                value = state.secondary,
                onValueChange = { onEvent(ExperienceQuickAddEvent.SecondaryChanged(it)) },
                label = stringResource(secondaryLabelResId(state.type)),
                errorMessage = state.secondaryError?.let { it.toMessage() },
                isError = state.secondaryError != null,
                enabled = state.isInputEnabled,
                size = CareerCompassTextFieldSize.Large,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            )
        }
        if (ExperienceEditorRules.hasDetailSection(state.type)) {
            ExperienceDetailSection(state = state, onEvent = onEvent)
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = spacing.small),
            horizontalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            CareerCompassButton(
                text = stringResource(R.string.core_ui_sheet_cancel),
                onClick = { onEvent(ExperienceQuickAddEvent.Dismissed) },
                modifier = Modifier.weight(1f),
                variant = CareerCompassButtonVariant.Secondary,
                size = CareerCompassButtonSize.Large,
                enabled = state.isInputEnabled,
            )
            CareerCompassButton(
                text = stringResource(submitLabelResId(isEditing = state.isEditing, isSubmitting = state.isSubmitting)),
                onClick = { onEvent(ExperienceQuickAddEvent.Submitted) },
                modifier = Modifier.weight(1f),
                size = CareerCompassButtonSize.Large,
                enabled = state.isSubmitEnabled,
            )
        }
    }
}

/**
 * 유형별 상세 입력(F1-3) — 기본은 접힌 채로 둔다.
 *
 * Step 3 는 선택 단계라 시트가 길어지는 것 자체가 이탈 비용이다. 그래서 필수 규칙이 걸린 공통 필드만 항상
 * 보이고, 추천 정확도를 올리는 값은 「자세히」 안에 둔다. 이미 값이 있는 카드를 고칠 때는
 * [ExperienceEditorState.isDetailExpanded] 가 펼친 채로 열려 사용자가 값이 지워졌다고 오해하지 않는다.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperienceDetailSection(
    state: ExperienceEditorState,
    onEvent: (ExperienceQuickAddEvent) -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val expandedState = stringResource(R.string.core_ui_experience_detail_expanded_state)
    val collapsedState = stringResource(R.string.core_ui_experience_detail_collapsed_state)

    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = state.isInputEnabled,
                        role = Role.Button,
                        onClick = { onEvent(ExperienceQuickAddEvent.DetailSectionToggled) },
                    ).semantics { stateDescription = if (state.isDetailExpanded) expandedState else collapsedState }
                    .padding(vertical = spacing.xSmall),
            horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text =
                    stringResource(
                        if (state.isDetailExpanded) {
                            R.string.core_ui_experience_detail_collapse
                        } else {
                            R.string.core_ui_experience_detail_expand
                        },
                    ),
                color = if (state.isInputEnabled) colors.onSurface else colors.disabledContent,
                style = CareerCompassTheme.typography.labelMedium,
            )
            // 접힌 채로도 값이 있다는 사실은 보여 준다 — 없는 줄 알고 다시 채우는 일을 막는다.
            if (!state.isDetailExpanded && state.hasDetailValues) {
                Surface(
                    shape = CareerCompassTheme.shapes.pill,
                    color = colors.successContainer,
                    contentColor = colors.onSuccessContainer,
                ) {
                    Text(
                        text = stringResource(R.string.core_ui_experience_detail_filled),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        style =
                            CareerCompassTheme.typography.caption.copy(
                                fontSize = 11.sp,
                                lineHeight = 16.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                }
            }
        }
        if (state.isDetailExpanded) {
            Text(
                text = stringResource(R.string.core_ui_experience_detail_hint),
                color = colors.mutedContent,
                style = CareerCompassTheme.typography.caption,
            )
            if (ExperienceEditorRules.hasTechTags(state.type)) {
                TechTagField(state = state, onEvent = onEvent)
            }
            if (ExperienceEditorRules.hasLink(state.type)) {
                CareerCompassTextField(
                    value = state.link,
                    onValueChange = { onEvent(ExperienceQuickAddEvent.LinkChanged(it)) },
                    label = stringResource(R.string.core_ui_experience_link_label),
                    placeholder = stringResource(R.string.core_ui_experience_link_placeholder),
                    errorMessage = state.linkError?.linkMessage(),
                    isError = state.linkError != null,
                    enabled = state.isInputEnabled,
                    size = CareerCompassTextFieldSize.Large,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                )
            }
            if (ExperienceEditorRules.hasDetail(state.type)) {
                CareerCompassTextField(
                    value = state.detail,
                    onValueChange = { onEvent(ExperienceQuickAddEvent.DetailChanged(it)) },
                    label = stringResource(detailLabelResId(state.type)),
                    errorMessage = state.detailError?.let { it.toMessage() },
                    isError = state.detailError != null,
                    enabled = state.isInputEnabled,
                    size = CareerCompassTextFieldSize.Large,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                )
            }
        }
    }
}

/** 기술 태그 입력칸과 확정된 칩들. 입력칸의 글자는 완료를 눌러야 태그가 된다. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechTagField(
    state: ExperienceEditorState,
    onEvent: (ExperienceQuickAddEvent) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing

    CareerCompassTextField(
        value = state.techInput,
        onValueChange = { onEvent(ExperienceQuickAddEvent.TechInputChanged(it)) },
        label = stringResource(R.string.core_ui_experience_tech_label),
        placeholder = stringResource(R.string.core_ui_experience_tech_placeholder),
        supportingText =
            stringResource(
                R.string.core_ui_experience_tech_support,
                MAX_EXPERIENCE_TECH_TAGS,
                MAX_EXPERIENCE_TECH_TAG_LENGTH,
            ),
        errorMessage = state.techInputError?.techMessage(),
        isError = state.techInputError != null,
        enabled = state.isInputEnabled,
        size = CareerCompassTextFieldSize.Large,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions =
            KeyboardActions(
                onDone = {
                    if (state.isInputEnabled && state.techInput.isNotBlank()) {
                        onEvent(ExperienceQuickAddEvent.TechTagSubmitted)
                    }
                },
            ),
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(spacing.xSmall),
        verticalArrangement = Arrangement.spacedBy(spacing.xSmall),
    ) {
        state.techs.forEach { tag ->
            RemovableTechTag(
                tag = tag,
                enabled = state.isInputEnabled,
                onClick = { onEvent(ExperienceQuickAddEvent.TechTagRemoved(tag)) },
            )
        }
    }
}

@Composable
private fun RemovableTechTag(
    tag: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val removeDescription = stringResource(R.string.core_ui_experience_tech_remove, tag)
    val colors = CareerCompassTheme.colors

    Surface(
        modifier =
            Modifier
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics { contentDescription = removeDescription },
        shape = CareerCompassTheme.shapes.pill,
        color = if (enabled) colors.successContainer else colors.disabledContainer,
        contentColor = if (enabled) colors.onSuccessContainer else colors.disabledContent,
    ) {
        Text(
            text = stringResource(R.string.core_ui_experience_tech_chip, tag),
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            maxLines = 1,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    }
}

/** 태그 개수 초과는 공통 「허용 범위를 벗어났어요」로는 무엇을 고쳐야 하는지 알 수 없어 상한을 문구에 넣는다. */
@Composable
private fun ProfileFieldViolation.techMessage(): String =
    when (this) {
        ProfileFieldViolation.OutOfRange -> stringResource(R.string.core_ui_experience_tech_limit, MAX_EXPERIENCE_TECH_TAGS)
        else -> toMessage()
    }

/** 링크는 「형식이 올바르지 않아요」만으로 http/https 요구를 알 수 없어 필드 고유 문구를 쓴다. */
@Composable
private fun ProfileFieldViolation.linkMessage(): String =
    when (this) {
        ProfileFieldViolation.InvalidFormat -> stringResource(R.string.core_ui_experience_link_invalid)
        else -> toMessage()
    }

/** 경험 유형의 화면 라벨 — Step 3 필터와 시트가 같은 문구를 쓴다. */
public fun ExperienceType.labelResId(): Int =
    when (this) {
        ExperienceType.Project -> R.string.core_ui_experience_type_project
        ExperienceType.Award -> R.string.core_ui_experience_type_award
        ExperienceType.Intern -> R.string.core_ui_experience_type_intern
        ExperienceType.Activity -> R.string.core_ui_experience_type_activity
        ExperienceType.Certificate -> R.string.core_ui_experience_type_certificate
    }

private fun submitLabelResId(
    isEditing: Boolean,
    isSubmitting: Boolean,
): Int =
    when {
        isEditing && isSubmitting -> R.string.core_ui_experience_saving
        isEditing -> R.string.core_ui_experience_save
        isSubmitting -> R.string.core_ui_experience_submitting
        else -> R.string.core_ui_experience_submit
    }

private fun startDateLabelResId(type: ExperienceType): Int =
    when {
        // 수상의 시점은 연 단위다 — 칸이 `YYYY.MM` 을 요구하면 사용자는 없는 월을 지어내 채운다(#166).
        type == ExperienceType.Award -> R.string.core_ui_experience_award_year_label

        type == ExperienceType.Certificate -> R.string.core_ui_experience_acquired_label

        ExperienceEditorRules.isStartDateRequired(type) -> R.string.core_ui_experience_start_label_required

        else -> R.string.core_ui_experience_start_label
    }

private fun startDatePlaceholderResId(type: ExperienceType): Int =
    if (type == ExperienceType.Award) R.string.core_ui_experience_year_placeholder else R.string.core_ui_experience_date_placeholder

private fun primaryLabelResId(type: ExperienceType): Int =
    when (type) {
        ExperienceType.Project -> R.string.core_ui_experience_project_role_label
        ExperienceType.Award -> R.string.core_ui_experience_award_rank_label
        ExperienceType.Intern -> R.string.core_ui_experience_intern_company_label
        ExperienceType.Activity -> R.string.core_ui_experience_activity_organization_label
        ExperienceType.Certificate -> R.string.core_ui_experience_certificate_issuer_label
    }

private fun detailLabelResId(type: ExperienceType): Int =
    when (type) {
        ExperienceType.Activity -> R.string.core_ui_experience_activity_role_label
        else -> R.string.core_ui_experience_intern_summary_label
    }

private fun secondaryLabelResId(type: ExperienceType): Int =
    when (type) {
        ExperienceType.Project -> R.string.core_ui_experience_project_summary_label
        ExperienceType.Award -> R.string.core_ui_experience_award_organizer_label
        ExperienceType.Intern -> R.string.core_ui_experience_intern_role_label
        ExperienceType.Activity -> R.string.core_ui_experience_activity_summary_label
        ExperienceType.Certificate -> R.string.core_ui_experience_activity_summary_label
    }
