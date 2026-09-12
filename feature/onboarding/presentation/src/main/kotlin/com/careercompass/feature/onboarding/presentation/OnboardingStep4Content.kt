package com.careercompass.feature.onboarding.presentation

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.icon.CareerCompassIcons
import com.careercompass.core.ui.theme.CareerCompassTheme

/** Stateless past-application step from the onboarding flow. */
@Composable
public fun OnboardingStep4Content(
    state: OnboardingStep4UiState,
    onEvent: (OnboardingStep4Event) -> Unit,
    modifier: Modifier = Modifier,
) {
    OnboardingStepScaffold(
        currentStep = state.currentStep,
        totalSteps = state.totalSteps,
        title = stringResource(R.string.onboarding_step4_title),
        // 기능 스펙 F1-2 Step 4 는 「업로드하지 않아도 지원서 생성은 가능하나 품질이 낮을 수 있음을 안내」를
        // 요구한다(#142). 안내는 여기 한 줄로 끝낸다 — 건너뛰기에 확인 대화상자를 세우면 「선택 입력」이 사실상
        // 반강제가 되고, 푸터에 문구를 더하면 1 : 2.2 로 이미 좁은 그 자리가 큰 글꼴에서 화면을 잡아먹는다(#131).
        // 「나중에 마이 탭에서 추가할 수 있다」는 넣지 않았다 — 그 경로가 아직 앱에 없다. 없는 길은 가리키지 않는다.
        description = stringResource(R.string.onboarding_step4_description),
        onBackClick = { onEvent(OnboardingStep4Event.BackClicked) },
        modifier = modifier,
        footerContent = {
            OnboardingStep4Footer(
                enabled = state.isInputEnabled,
                completeEnabled = state.isCompleteEnabled,
                onSkipClick = { onEvent(OnboardingStep4Event.SkipClicked) },
                onCompleteClick = { onEvent(OnboardingStep4Event.CompleteClicked) },
            )
        },
    ) {
        OnboardingStep4Body(state = state, onEvent = onEvent)
    }
}

@Composable
private fun OnboardingStep4Body(
    state: OnboardingStep4UiState,
    onEvent: (OnboardingStep4Event) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.xxLarge)) {
        UploadTarget(
            enabled = state.isUploadEnabled,
            onClick = { onEvent(OnboardingStep4Event.UploadClicked) },
        )
        OrDivider()
        DirectInputAction(
            enabled = state.isDirectInputEnabled,
            onClick = { onEvent(OnboardingStep4Event.DirectInputClicked) },
        )
        if (state.uploadedDocuments.isNotEmpty()) {
            UploadedDocuments(
                documents = state.uploadedDocuments,
                expandedDocumentId = state.expandedDocumentId,
                enabled = state.isInputEnabled,
                onMenuClick = { documentId ->
                    onEvent(OnboardingStep4Event.DocumentMenuClicked(documentId))
                },
                onRetryClick = { documentId ->
                    onEvent(OnboardingStep4Event.DocumentRetryClicked(documentId))
                },
                onExpandClick = { documentId ->
                    onEvent(OnboardingStep4Event.DocumentExpandToggled(documentId))
                },
                onItemClick = { documentId, itemId ->
                    onEvent(OnboardingStep4Event.ItemCategoryClicked(documentId, itemId))
                },
            )
        }
    }
}

@Composable
private fun UploadTarget(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val uploadDescription = stringResource(R.string.onboarding_step4_upload_content_description)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .clip(CareerCompassTheme.shapes.largeControl)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    contentDescription = uploadDescription
                    role = Role.Button
                    if (!enabled) disabled()
                },
        shape = CareerCompassTheme.shapes.largeControl,
        color = if (enabled) colors.surface else colors.disabledContainer,
        contentColor = if (enabled) colors.onSurface else colors.disabledContent,
        border = BorderStroke(1.5.dp, colors.subtleOutline),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.large, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(spacing.small),
        ) {
            Box(
                modifier =
                    Modifier
                        .size(44.dp)
                        .testTag(UPLOAD_ICON_SLOT_TAG),
                contentAlignment = Alignment.TopStart,
            ) {
                Image(
                    painter = painterResource(R.drawable.onboarding_ic_upload_document),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .wrapContentSize(
                                align = Alignment.TopStart,
                                unbounded = true,
                            ).requiredSize(
                                width = 62.05.dp,
                                height = 63.05.dp,
                            ).testTag(UPLOAD_ICON_ART_TAG),
                )
            }
            Text(
                text = stringResource(R.string.onboarding_step4_upload_title),
                color = if (enabled) colors.onSurface else colors.disabledContent,
                style =
                    CareerCompassTheme.typography.labelMedium.copy(
                        fontWeight = FontWeight.SemiBold,
                    ),
            )
            Text(
                text =
                    stringResource(
                        R.string.onboarding_step4_upload_requirements,
                        ONBOARDING_MAX_APPLICATION_FILE_SIZE_MEGABYTES,
                    ),
                color = if (enabled) colors.mutedContent else colors.disabledContent,
                style =
                    CareerCompassTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontWeight = FontWeight.Normal,
                    ),
            )
        }
    }
}

@Composable
private fun OrDivider() {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Row(
        modifier = Modifier.width(100.dp),
        horizontalArrangement = Arrangement.spacedBy(spacing.medium),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.subtleOutline),
        )
        Text(
            text = stringResource(R.string.onboarding_step4_or),
            color = colors.mutedContent,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
        )
        Spacer(
            modifier =
                Modifier
                    .weight(1f)
                    .height(1.dp)
                    .background(colors.subtleOutline),
        )
    }
}

@Composable
private fun DirectInputAction(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val useFigmaCompactSize = LocalDensity.current.fontScale <= 1f
    val sizeModifier =
        if (useFigmaCompactSize) {
            Modifier.width(103.dp)
        } else {
            Modifier.fillMaxWidth()
        }

    Surface(
        modifier =
            Modifier
                .then(sizeModifier)
                .heightIn(min = 51.dp)
                .clip(CareerCompassTheme.shapes.largeControl)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (!enabled) disabled()
                },
        shape = CareerCompassTheme.shapes.largeControl,
        color = if (enabled) colors.surface else colors.disabledContainer,
        contentColor = if (enabled) colors.onSurface else colors.disabledContent,
        border = BorderStroke(1.dp, colors.subtleOutline),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            horizontalArrangement =
                Arrangement.spacedBy(
                    space = if (useFigmaCompactSize) spacing.xSmall else 0.dp,
                    alignment = Alignment.CenterHorizontally,
                ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = CareerCompassIcons.Edit,
                contentDescription = null,
                modifier = Modifier.size(if (useFigmaCompactSize) 16.dp else 14.dp),
            )
            Text(
                text = stringResource(R.string.onboarding_step4_direct_input),
                modifier =
                    Modifier.weight(
                        weight = 1f,
                        fill = !useFigmaCompactSize,
                    ),
                fontWeight = FontWeight.SemiBold,
                maxLines = if (useFigmaCompactSize) 1 else Int.MAX_VALUE,
                softWrap = !useFigmaCompactSize,
                style = CareerCompassTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
private fun UploadedDocuments(
    documents: List<OnboardingApplicationDocument>,
    expandedDocumentId: String?,
    enabled: Boolean,
    onMenuClick: (String) -> Unit,
    onRetryClick: (String) -> Unit,
    onExpandClick: (String) -> Unit,
    onItemClick: (String, Long) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing

    Column(verticalArrangement = Arrangement.spacedBy(spacing.small)) {
        Text(
            text =
                stringResource(
                    R.string.onboarding_step4_uploaded_count,
                    documents.size,
                    ONBOARDING_MAX_APPLICATION_UPLOAD_COUNT,
                ),
            modifier = Modifier.semantics { heading() },
            color = CareerCompassTheme.colors.onSurfaceVariant,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                ),
        )
        documents.forEach { document ->
            val expanded = document.isExpandable && document.id == expandedDocumentId
            UploadedDocumentItem(
                document = document,
                expanded = expanded,
                enabled = enabled,
                onMenuClick = { onMenuClick(document.id) },
                onRetryClick = { onRetryClick(document.id) },
                onExpandClick = { onExpandClick(document.id) },
            )
            if (expanded) {
                ClassifiedItems(
                    items = document.items,
                    enabled = enabled,
                    onItemClick = { itemId -> onItemClick(document.id, itemId) },
                )
            }
        }
    }
}

@Composable
private fun UploadedDocumentItem(
    document: OnboardingApplicationDocument,
    expanded: Boolean,
    enabled: Boolean,
    onMenuClick: () -> Unit,
    onRetryClick: () -> Unit,
    onExpandClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val fontScale = LocalDensity.current.fontScale
    val useFigmaCompactSize = fontScale <= 1f
    val menuTouchTargetSize = 48.dp
    val cardWidthModifier =
        if (useFigmaCompactSize) {
            Modifier.width(232.dp)
        } else {
            Modifier.fillMaxWidth()
        }
    val cardHeightModifier =
        if (useFigmaCompactSize) {
            Modifier.height(63.dp)
        } else {
            Modifier.heightIn(min = 63.dp)
        }
    val menuDescription =
        stringResource(
            R.string.onboarding_step4_document_menu,
            document.fileName,
        )
    val retryDescription =
        stringResource(
            R.string.onboarding_step4_document_retry_description,
            document.fileName,
        )
    val expandDescription =
        stringResource(
            if (expanded) R.string.onboarding_step4_document_collapse else R.string.onboarding_step4_document_expand,
            document.fileName,
        )

    /**
     * 카드 본문의 두 번째 손잡이. 실패 문서는 재시도, 분류가 끝난 문서는 항목 목록 펼침/접기를 맡는다 —
     * 우측 48dp 메뉴 영역 밖(#57)이라 터치 영역이 겹치지 않는다.
     */
    val textColumnModifier =
        when {
            document.status is OnboardingApplicationDocumentStatus.Failed -> {
                Modifier
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onRetryClick,
                    ).semantics(mergeDescendants = true) {
                        contentDescription = retryDescription
                        liveRegion = LiveRegionMode.Polite
                        role = Role.Button
                        if (!enabled) disabled()
                    }
            }

            document.isExpandable -> {
                Modifier
                    .clickable(
                        enabled = enabled,
                        role = Role.Button,
                        onClick = onExpandClick,
                    ).semantics(mergeDescendants = true) {
                        contentDescription = expandDescription
                        role = Role.Button
                        if (!enabled) disabled()
                    }
            }

            else -> {
                Modifier
            }
        }

    Surface(
        modifier =
            Modifier
                .then(cardWidthModifier)
                .then(cardHeightModifier)
                .testTag(documentCardTag(document.id)),
        shape = CareerCompassTheme.shapes.largeControl,
        color = colors.surface,
        contentColor = colors.onSurface,
        border = BorderStroke(1.dp, colors.surfaceVariant),
    ) {
        Box(
            modifier =
                Modifier
                    .fillMaxWidth(),
        ) {
            Row(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(
                            start = spacing.medium,
                            top = 7.5.dp,
                            end = menuTouchTargetSize,
                            bottom = 7.5.dp,
                        ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocumentFormatBadge(
                    documentId = document.id,
                    formatLabel = document.format.label,
                    fontScale = fontScale,
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(
                    modifier =
                        Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag(documentTextTag(document.id))
                            .then(textColumnModifier),
                    verticalArrangement =
                        Arrangement.spacedBy(
                            space = 2.dp,
                            alignment = Alignment.CenterVertically,
                        ),
                ) {
                    Text(
                        text = document.fileName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = colors.onSurface,
                        style =
                            CareerCompassTheme.typography.labelMedium.copy(
                                fontSize = 13.sp,
                                lineHeight = 19.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            ),
                    )
                    DocumentStatus(
                        status = document.status,
                        enabled = enabled,
                        expanded = if (document.isExpandable) expanded else null,
                    )
                }
            }
            Box(
                modifier =
                    Modifier
                        .size(menuTouchTargetSize)
                        .align(Alignment.CenterEnd)
                        .clickable(
                            enabled = enabled,
                            role = Role.Button,
                            onClick = onMenuClick,
                        ).semantics {
                            contentDescription = menuDescription
                            role = Role.Button
                            if (!enabled) disabled()
                        },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Icon(
                    imageVector = CareerCompassIcons.MoreHorizontal,
                    contentDescription = null,
                    modifier =
                        Modifier
                            .padding(end = spacing.medium)
                            .size(20.dp),
                    tint = if (enabled) colors.mutedContent else colors.disabledContent,
                )
            }
        }
    }
}

/** [expanded] 가 null 이면 펼칠 항목이 없는 문서라 펼침 표시를 그리지 않는다. */
@Composable
private fun DocumentStatus(
    status: OnboardingApplicationDocumentStatus,
    enabled: Boolean,
    expanded: Boolean?,
) {
    val colors = CareerCompassTheme.colors
    val liveRegionModifier =
        Modifier.semantics(mergeDescendants = true) {
            liveRegion = LiveRegionMode.Polite
        }

    when (status) {
        OnboardingApplicationDocumentStatus.Processing -> {
            Row(
                modifier = liveRegionModifier,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(
                    modifier =
                        Modifier
                            .size(12.dp)
                            .clearAndSetSemantics {},
                    color = if (enabled) colors.actionPrimary else colors.disabledContent,
                    strokeWidth = 1.5.dp,
                )
                DocumentStatusText(
                    text = stringResource(R.string.onboarding_step4_document_processing),
                    color = if (enabled) colors.mutedContent else colors.disabledContent,
                )
            }
        }

        is OnboardingApplicationDocumentStatus.Completed -> {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                DocumentStatusText(
                    text =
                        stringResource(
                            R.string.onboarding_step4_document_completed,
                            status.classifiedItemCount,
                        ),
                    color = if (enabled) colors.mutedContent else colors.disabledContent,
                    modifier = liveRegionModifier.weight(weight = 1f, fill = false),
                )
                if (expanded != null) {
                    Icon(
                        imageVector =
                            if (expanded) CareerCompassIcons.ExpandLess else CareerCompassIcons.ExpandMore,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (enabled) colors.mutedContent else colors.disabledContent,
                    )
                }
            }
        }

        is OnboardingApplicationDocumentStatus.Failed -> {
            DocumentStatusText(
                text =
                    stringResource(
                        R.string.onboarding_step4_document_failed_retry,
                        status.message,
                    ),
                color = if (enabled) colors.actionDanger else colors.disabledContent,
                modifier = liveRegionModifier,
            )
        }
    }
}

@Composable
private fun DocumentStatusText(
    text: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    val useFigmaCompactSize = LocalDensity.current.fontScale <= 1f
    val widthModifier =
        if (useFigmaCompactSize) {
            Modifier
        } else {
            Modifier.fillMaxWidth()
        }

    Text(
        text = text,
        modifier = modifier.then(widthModifier),
        maxLines = if (useFigmaCompactSize) 1 else Int.MAX_VALUE,
        softWrap = !useFigmaCompactSize,
        overflow = if (useFigmaCompactSize) TextOverflow.Ellipsis else TextOverflow.Clip,
        color = color,
        style = CareerCompassTheme.typography.caption,
    )
}

@Composable
private fun DocumentFormatBadge(
    documentId: String,
    formatLabel: String,
    fontScale: Float,
) {
    val colors = CareerCompassTheme.colors
    val badgeScale = fontScale.coerceAtLeast(1f)

    Box(
        modifier =
            Modifier
                .width(20.dp * badgeScale)
                .height(15.dp * badgeScale)
                .background(
                    color = colors.successContainer,
                    shape = CareerCompassTheme.shapes.control,
                ).testTag(documentFormatBadgeTag(documentId)),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = formatLabel,
            modifier = Modifier.clearAndSetSemantics {},
            color = colors.onSuccessContainer,
            maxLines = 1,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 10.sp,
                    lineHeight = 15.sp,
                    fontWeight = FontWeight.Bold,
                ),
        )
    }
}

/**
 * 펼친 문서의 분류 항목 목록 — 기능 스펙 F1-4 의 「분류 확인·수동 조정」이 여기서 일어난다.
 *
 * 카드 아래에 한 단 들여 붙여 어느 문서의 항목인지 드러낸다.
 */
@Composable
private fun ClassifiedItems(
    items: List<OnboardingApplicationItem>,
    enabled: Boolean,
    onItemClick: (Long) -> Unit,
) {
    val spacing = CareerCompassTheme.spacing
    val useFigmaCompactSize = LocalDensity.current.fontScale <= 1f
    val widthModifier = if (useFigmaCompactSize) Modifier.width(232.dp) else Modifier.fillMaxWidth()

    Column(
        modifier =
            Modifier
                .then(widthModifier)
                .padding(start = spacing.medium),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        items.forEach { item ->
            ClassifiedItemRow(
                item = item,
                enabled = enabled,
                onClick = { onItemClick(item.id) },
            )
        }
    }
}

@Composable
private fun ClassifiedItemRow(
    item: OnboardingApplicationItem,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing
    val shape = CareerCompassTheme.shapes.largeControl
    val description = stringResource(R.string.onboarding_step4_item_change_category, item.categoryLabel)

    Surface(
        modifier =
            Modifier
                .fillMaxWidth()
                .heightIn(min = ITEM_ROW_MIN_HEIGHT)
                .clip(shape)
                .clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    contentDescription = description
                    role = Role.Button
                    if (!enabled) disabled()
                }.testTag(itemRowTag(item.id)),
        shape = shape,
        color = colors.subtleSurface,
        contentColor = colors.onSurface,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = spacing.medium, vertical = spacing.small),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ItemBadge(
                    label = item.categoryLabel,
                    container = if (enabled) colors.surfaceVariant else colors.disabledContainer,
                    content = if (enabled) colors.onSurfaceVariant else colors.disabledContent,
                )
                if (item.needsReview) {
                    ItemBadge(
                        label = stringResource(R.string.onboarding_step4_item_needs_review),
                        container = if (enabled) colors.warningContainer else colors.disabledContainer,
                        content = if (enabled) colors.onWarningContainer else colors.disabledContent,
                    )
                }
            }
            Text(
                text = item.contentPreview,
                maxLines = ITEM_PREVIEW_MAX_LINES,
                overflow = TextOverflow.Ellipsis,
                color = if (enabled) colors.mutedContent else colors.disabledContent,
                style =
                    CareerCompassTheme.typography.caption.copy(
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                    ),
            )
        }
    }
}

@Composable
private fun ItemBadge(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = CareerCompassTheme.shapes.pill,
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            maxLines = 1,
            style =
                CareerCompassTheme.typography.caption.copy(
                    fontSize = 11.sp,
                    lineHeight = 16.5.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    }
}

private fun documentCardTag(documentId: String): String = "onboarding_step4_document_$documentId"

private fun itemRowTag(itemId: Long): String = "onboarding_step4_item_$itemId"

private fun documentTextTag(documentId: String): String = "onboarding_step4_document_text_$documentId"

private fun documentFormatBadgeTag(documentId: String): String = "onboarding_step4_document_format_$documentId"

private val ITEM_ROW_MIN_HEIGHT = 48.dp

private const val ITEM_PREVIEW_MAX_LINES = 2

private const val UPLOAD_ICON_SLOT_TAG: String = "onboarding_step4_upload_icon_slot"

private const val UPLOAD_ICON_ART_TAG: String = "onboarding_step4_upload_icon_art"

@Composable
private fun OnboardingStep4Footer(
    enabled: Boolean,
    completeEnabled: Boolean,
    onSkipClick: () -> Unit,
    onCompleteClick: () -> Unit,
) {
    val colors = CareerCompassTheme.colors
    val spacing = CareerCompassTheme.spacing

    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .background(colors.subtleSurface)
                .navigationBarsPadding()
                .padding(
                    start = spacing.large,
                    top = spacing.medium,
                    end = spacing.large,
                    bottom = spacing.large,
                ),
        horizontalArrangement = Arrangement.spacedBy(spacing.small),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OnboardingStep4FooterButton(
            text = stringResource(R.string.onboarding_step4_skip),
            onClick = onSkipClick,
            modifier = Modifier.weight(1f),
            isPrimary = false,
            enabled = enabled,
        )
        OnboardingStep4FooterButton(
            text = stringResource(R.string.onboarding_step4_complete),
            onClick = onCompleteClick,
            modifier = Modifier.weight(2.2f),
            isPrimary = true,
            enabled = completeEnabled,
        )
    }
}

/**
 * Step 4 푸터 전용 버튼.
 *
 * [CareerCompassButton] 대신 두는 이유는 «폭» 이다. 이 푸터는 시안대로 건너뛰기 : 완료를 1 : 2.2 로
 * 나눠 건너뛰기 칸이 약 101dp 인데, 디자인 시스템 버튼의 좌우 여백 22dp 씩을 빼면 글자에 약 57dp 만
 * 남는다. 「건너뛰기」는 16sp 기준 약 71dp 라 기본 배율에서도 마지막 한 자가 다음 줄로 밀렸다(#131).
 * 여백을 8dp 로 줄이면 85dp 가 남아 한 줄에 들어간다.
 *
 * 여백만 줄이고 높이·글꼴·색은 `Large` 와 같게 맞춘다 — 이 자리만 다른 버튼처럼 보이지 않게. 디자인
 * 시스템의 여백 자체를 건드리지 않는 이유는 그 값이 모든 화면의 버튼을 함께 움직이기 때문이다.
 *
 * 큰 글꼴에서는 줄 수 제한 없이 접어 문구를 통째로 남긴다(#122).
 */
@Composable
private fun OnboardingStep4FooterButton(
    text: String,
    onClick: () -> Unit,
    isPrimary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = CareerCompassTheme.colors
    val shape = CareerCompassTheme.shapes.control
    val containerColor =
        when {
            !enabled -> colors.disabledContainer
            isPrimary -> colors.actionPrimary
            else -> colors.surface
        }
    val contentColor =
        when {
            !enabled -> colors.disabledContent
            isPrimary -> colors.onAction
            else -> colors.onSurface
        }

    Row(
        modifier =
            modifier
                .heightIn(min = 52.dp)
                .clip(shape)
                .background(containerColor)
                .then(
                    if (!isPrimary) {
                        Modifier.border(BorderStroke(1.dp, colors.interactiveOutline), shape)
                    } else {
                        Modifier
                    },
                ).clickable(
                    enabled = enabled,
                    role = Role.Button,
                    onClick = onClick,
                ).semantics(mergeDescendants = true) {
                    role = Role.Button
                    if (!enabled) disabled()
                }.padding(horizontal = 8.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = text,
            modifier = Modifier.fillMaxWidth(),
            color = contentColor,
            textAlign = TextAlign.Center,
            softWrap = true,
            style =
                CareerCompassTheme.typography.labelMedium.copy(
                    fontSize = 16.sp,
                    lineHeight = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                ),
        )
    }
}
