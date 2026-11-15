package com.careercompass.feature.onboarding.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careercompass.core.ui.icon.CareerCompassIcons
import com.careercompass.core.ui.theme.CareerCompassTheme

/** Stateless experience-list step from the onboarding flow. */
@Composable
public fun OnboardingStep3Content(
    state: OnboardingStep3UiState,
    onEvent: (OnboardingStep3Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        title = stringResource(R.string.onboarding_step3_title),
        description = stringResource(R.string.onboarding_step3_description),
        onBackClick = { onEvent(OnboardingStep3Event.BackClicked) },
        modifier = modifier,
        footerContent = {
            OnboardingPrimaryActionFooter(
                text = stringResource(R.string.onboarding_step3_next),
                enabled = state.isNextEnabled,
                onClick = { onEvent(OnboardingStep3Event.NextClicked) },
            )
        },
    ) {
        OnboardingExperienceContent(state = state, onEvent = onEvent)
    }
}

@Composable
private fun OnboardingExperienceContent(
    state: OnboardingStep3UiState,
    onEvent: (OnboardingStep3Event) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxLarge)) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing.medium)) {
            OptionalExperienceHint()
            ExperienceTypeFilters(state = state, onEvent = onEvent)
        }
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            state.visibleExperiences.forEach { experience ->
                ExperienceCard(
                    experience = experience,
                    enabled = state.isInputEnabled,
                    onClick = {
                        onEvent(OnboardingStep3Event.ExperienceSelected(experience.id))
                    },
                    onDeleteClick = {
                        onEvent(OnboardingStep3Event.ExperienceDeleteClicked(experience.id))
                    },
                )
            }
            AddExperienceButton(
                enabled = state.isAddEnabled,
                onClick = { onEvent(OnboardingStep3Event.AddExperienceClicked) },
            )
        }
    }
}

/** Experiences are optional; this caption explains why adding one is still worthwhile. */
@Composable
private fun OptionalExperienceHint() {
    Text(
        text = stringResource(R.string.onboarding_step3_optional_hint),
        color = CareerCompassTheme.colors.mutedContent,
        style =
            CareerCompassTheme.typography.caption.copy(
                fontSize = 12.sp,
                lineHeight = 18.sp,
                fontWeight = FontWeight.Normal,
            ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperienceTypeFilters(
    state: OnboardingStep3UiState,
    onEvent: (OnboardingStep3Event) -> Unit,
) {
    val selectedState = stringResource(R.string.onboarding_step3_filter_selected)
    val unselectedState = stringResource(R.string.onboarding_step3_filter_unselected)

    FlowRow(
        modifier = Modifier.fillMaxWidth().selectableGroup(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        state.experienceTypes.forEach { type ->
            val selected = type.id == state.selectedExperienceTypeId
            CompactExperienceTypePill(
                label = type.label,
                selected = selected,
                onClick = {
                    onEvent(OnboardingStep3Event.ExperienceTypeSelected(type.id))
                },
                enabled = state.isInputEnabled,
                stateDescription = if (selected) selectedState else unselectedState,
            )
        }
    }
}

@Composable
private fun CompactExperienceTypePill(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    stateDescription: String,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val containerColor =
        when {
            !enabled -> colors.disabledContainer
            selected -> colors.inverseSurface
            else -> colors.surface
        }
    val contentColor =
        when {
            !enabled -> colors.disabledContent
            selected -> colors.inverseOnSurface
            else -> colors.onSurface
        }
    val borderColor =
        when {
            !enabled -> colors.subtleOutline
            selected -> colors.inverseSurface
            else -> colors.subtleOutline
        }

    Surface(
        modifier =
            Modifier
                .semantics { this.stateDescription = stateDescription }
                .selectable(
                    selected = selected,
                    enabled = enabled,
                    role = Role.RadioButton,
                    onClick = onClick,
                ),
        shape = CareerCompassTheme.shapes.pill,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.dp, borderColor),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            maxLines = 1,
            style =
                CareerCompassTheme.typography.labelMedium.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
        )
    }
}

/**
 * 경험 카드. 본문을 누르면 그 카드를 수정하고, 우측에 삭제 영역을 따로 둔다(F1-3).
 *
 * 삭제 영역은 48dp 를 차지하고 본문은 그만큼 오른쪽 여백을 비워, 두 손잡이의 터치 영역이 겹치지 않는다
 * (#57 에서 Step 4 문서 카드에 정한 규칙과 같다).
 *
 * 본문에는 `contentDescription` 을 얹지 않는다. 병합 노드에 설명을 얹으면 스크린 리더가 그 한 줄만 읽고
 * 기간·역할·기술 태그가 사라진다. 행동은 `onClickLabel` 로만 붙인다. 삭제 손잡이는 아이콘뿐이라 읽을
 * 본문이 없으므로 거기에는 설명을 그대로 둔다(#346).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExperienceCard(
    experience: OnboardingExperience,
    enabled: Boolean,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val shape = RoundedCornerShape(14.dp)
    val editActionLabel = stringResource(R.string.onboarding_step3_experience_edit, experience.title)
    val deleteDescription = stringResource(R.string.onboarding_step3_experience_delete, experience.title)

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = shape,
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.surfaceVariant),
    ) {
        Box(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        // 삭제 영역을 클릭 범위 밖에 두려고 여백을 클릭 수식어보다 먼저 준다.
                        .padding(end = DELETE_TOUCH_TARGET_SIZE)
                        .clickable(
                            enabled = enabled,
                            onClickLabel = editActionLabel,
                            role = Role.Button,
                            onClick = onClick,
                        ).padding(start = 14.dp, top = 14.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.spacedBy(spacing.small),
            ) {
                Text(
                    text = experience.title,
                    color = colors.onSurface,
                    style =
                        CareerCompassTheme.typography.labelMedium.copy(
                            fontSize = 15.sp,
                            lineHeight = 22.5.sp,
                            letterSpacing = (-0.1).sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                )
                Text(
                    text =
                        stringResource(
                            R.string.onboarding_step3_experience_metadata,
                            experience.period,
                            experience.role,
                        ),
                    color = colors.mutedContent,
                    style =
                        CareerCompassTheme.typography.caption.copy(
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            fontWeight = FontWeight.Normal,
                        ),
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    experience.tags.forEach { tag ->
                        ExperienceSkillTag(label = tag)
                    }
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(DELETE_TOUCH_TARGET_SIZE)
                        .align(Alignment.TopEnd)
                        .clip(shape)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onDeleteClick,
                        ).semantics {
                            contentDescription = deleteDescription
                            role = Role.Button
                            if (!enabled) disabled()
                        },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = CareerCompassIcons.Close,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = if (enabled) colors.mutedContent else colors.disabledContent,
                )
            }
        }
    }
}

private val DELETE_TOUCH_TARGET_SIZE = 48.dp

@Composable
private fun ExperienceSkillTag(label: String) {
    val colors = CareerCompassTheme.colors

    Surface(
        shape = CareerCompassTheme.shapes.pill,
        color = colors.surfaceVariant,
        contentColor = colors.onSurfaceVariant,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.5.sp,
                ),
        )
    }
}

@Composable
private fun AddExperienceButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val shape = RoundedCornerShape(14.dp)
    val contentColor = if (enabled) colors.onSurface else colors.disabledContent
    val containerColor = if (enabled) colors.surface else colors.disabledContainer

    Surface(
        modifier =
            Modifier
                .widthIn(min = 98.dp)
                .heightIn(min = 52.dp)
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (!enabled) disabled()
                },
        shape = shape,
        color = containerColor,
        contentColor = contentColor,
        border = BorderStroke(1.5.dp, colors.subtleOutline),
    ) {
        Row(
            modifier = Modifier.heightIn(min = 52.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CareerCompassIcons.Add,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_step3_add_experience),
                maxLines = 1,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
