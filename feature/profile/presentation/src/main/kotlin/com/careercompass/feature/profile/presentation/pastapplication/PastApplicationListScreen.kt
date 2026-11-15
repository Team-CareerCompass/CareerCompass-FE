package com.careercompass.feature.profile.presentation.pastapplication

import android.content.res.Resources
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.application.MAX_PAST_APPLICATIONS
import com.careercompass.core.model.application.PastApplicationFileFormat
import com.careercompass.core.ui.application.UploadFileSelectionException
import com.careercompass.core.ui.application.UploadFileSelectionReason
import com.careercompass.core.ui.application.readUploadFile
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
import com.careercompass.core.ui.component.DirectInputEvent
import com.careercompass.core.ui.component.DirectInputSheet
import com.careercompass.core.ui.component.PastApplicationItemCategoryEvent
import com.careercompass.core.ui.component.PastApplicationItemCategorySheet
import com.careercompass.core.ui.mvi.ObserveFlag
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.launch

/**
 * 과거 지원서 목록 진입점 — 마이 홈의 「과거 지원서」가 연다(#180).
 *
 * 분류 시트는 온보딩 Step 4 와 같은 것을 쓴다(`core:ui`) — 분류 여섯 갈래와 그 문구가 두 벌이 되면
 * 한쪽에서 고친 값이 다른 쪽에서 다른 이름으로 보인다.
 *
 * 「지원서 추가」에서 파일 업로드와 직접 쓰기 중 하나를 고른다. 두 경로는 같은 서버 상한을 따른다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PastApplicationListScreen(
    onBackClick: () -> Unit,
    onSessionEnded: (ProfileSessionEnd) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PastApplicationListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    val resolver = LocalContext.current.contentResolver
    var pendingUploadUri by rememberSaveable { mutableStateOf<String?>(null) }
    val onFileEvent = { event: PastApplicationFileUploadIntent ->
        viewModel.onIntent(PastApplicationListIntent.FileUpload(event))
    }
    val picker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                readUploadFile(resolver, uri)
                    .onSuccess {
                        pendingUploadUri = uri.toString()
                        onFileEvent(PastApplicationFileUploadIntent.Selected(it))
                    }.onFailure {
                        onFileEvent(
                            PastApplicationFileUploadIntent.SelectionFailed(
                                (it as? UploadFileSelectionException)?.reason ?: UploadFileSelectionReason.Unknown,
                            ),
                        )
                    }
            }
        }
    // 한 번만 복원한다. 성공·취소로 시트가 닫혔을 때 같은 URI를 다시 여는 요청은 만들지 않는다.
    LaunchedEffect(Unit) {
        val uri = pendingUploadUri
        if (uri != null && state.fileUpload.file == null && !state.fileUpload.clearUriRequested) {
            readUploadFile(resolver, Uri.parse(uri))
                .onSuccess { onFileEvent(PastApplicationFileUploadIntent.Selected(it, restored = true)) }
                .onFailure {
                    pendingUploadUri = null
                    onFileEvent(PastApplicationFileUploadIntent.Dismiss)
                }
        }
    }
    ObserveFlag(
        raised = state.filePickerRequested,
        consumed = PastApplicationListIntent.ConsumeFilePicker,
        onIntent = viewModel::onIntent,
    ) { picker.launch(PastApplicationFileFormat.entries.map { it.mimeType }.toTypedArray()) }
    ObserveFlag(
        raised = state.fileUpload.clearUriRequested,
        consumed = PastApplicationListIntent.FileUpload(PastApplicationFileUploadIntent.ConsumeClearUri),
        onIntent = viewModel::onIntent,
    ) { pendingUploadUri = null }
    ObserveSignal(
        signal = state.fileUpload.selectionFailure,
        consumed = PastApplicationListIntent.FileUpload(PastApplicationFileUploadIntent.ConsumeSelectionFailure),
        onIntent = viewModel::onIntent,
    ) { reason ->
        val message =
            resources.getString(
                when (reason) {
                    UploadFileSelectionReason.UnsupportedFile -> com.careercompass.core.ui.R.string.core_ui_upload_unsupported_file
                    UploadFileSelectionReason.FileTooLarge -> com.careercompass.core.ui.R.string.core_ui_upload_file_too_large
                    UploadFileSelectionReason.Unknown -> com.careercompass.core.ui.R.string.core_ui_upload_unavailable
                },
            )
        snackbarScope.launch { snackbarHostState.showSnackbar(message) }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(PastApplicationListIntent.Refresh)
        onPauseOrDispose { }
    }

    ObserveSignal(
        signal = state.sessionEnd,
        consumed = PastApplicationListIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
        onSignal = {
            pendingUploadUri = null
            onSessionEnded(it)
        },
    )
    ObserveSignal(
        signal = state.message,
        consumed = PastApplicationListIntent.ConsumeMessage,
        onIntent = viewModel::onIntent,
    ) { message ->
        snackbarScope.launch { snackbarHostState.showSnackbar(message.text(resources)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        PastApplicationListContent(
            state = state,
            onEvent = { event ->
                if (event == PastApplicationListEvent.BackClicked) {
                    if (!state.fileUpload.isSubmitting && state.directInput?.isSubmitting != true) onBackClick()
                } else {
                    viewModel.onIntent(PastApplicationListIntent.Screen(event))
                }
            },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.categoryEditor?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(PastApplicationListIntent.DismissCategoryEditor) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            PastApplicationItemCategorySheet(
                state = editor,
                onEvent = { event ->
                    when (event) {
                        is PastApplicationItemCategoryEvent.CategorySelected -> {
                            viewModel.onIntent(PastApplicationListIntent.CategorySelected(event.category))
                        }

                        PastApplicationItemCategoryEvent.Dismissed -> {
                            viewModel.onIntent(PastApplicationListIntent.DismissCategoryEditor)
                        }
                    }
                },
            )
        }
    }

    state.directInput?.let { input ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(PastApplicationListIntent.DirectInput(DirectInputEvent.Dismissed)) },
            // 올리는 중에는 스와이프·스크림으로 시트가 숨겨지지 않게 한다 — 쓰던 글을 잃지 않게.
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { value -> value != SheetValue.Hidden || !input.isSubmitting },
                ),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            DirectInputSheet(
                state = input,
                onEvent = { viewModel.onIntent(PastApplicationListIntent.DirectInput(it)) },
            )
        }
    }

    if (state.fileUpload.file != null) {
        ModalBottomSheet(
            onDismissRequest = { onFileEvent(PastApplicationFileUploadIntent.Dismiss) },
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { it != SheetValue.Hidden || !state.fileUpload.isSubmitting },
                ),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            PastApplicationFileUploadContent(
                state = state.fileUpload,
                onEvent = onFileEvent,
                canSubmit = state.hasLoadedApplications,
            )
        }
    }
    BackHandler(enabled = state.fileUpload.isSubmitting || state.directInput?.isSubmitting == true) { }

    state.pendingDeletion?.let { target ->
        AlertDialog(
            onDismissRequest = { viewModel.onIntent(PastApplicationListIntent.DismissDelete) },
            confirmButton = {
                CareerCompassButton(
                    text = stringResource(R.string.profile_past_application_delete_dialog_confirm),
                    onClick = { viewModel.onIntent(PastApplicationListIntent.ConfirmDelete) },
                    variant = CareerCompassButtonVariant.Danger,
                    size = CareerCompassButtonSize.Small,
                    enabled = !state.isDeleting,
                )
            },
            dismissButton = {
                CareerCompassButton(
                    text = stringResource(R.string.profile_past_application_delete_dialog_cancel),
                    onClick = { viewModel.onIntent(PastApplicationListIntent.DismissDelete) },
                    variant = CareerCompassButtonVariant.Ghost,
                    size = CareerCompassButtonSize.Small,
                )
            },
            title = { Text(text = stringResource(R.string.profile_past_application_delete_dialog_title)) },
            // 서버가 S3 원본까지 지운다는 사실을 확인 자리에서 말한다 — 되돌릴 수 없는 조작이다.
            text = { Text(text = stringResource(R.string.profile_past_application_delete_dialog_message, target.label)) },
            containerColor = CareerCompassTheme.colors.surface,
            titleContentColor = CareerCompassTheme.colors.onSurface,
            textContentColor = CareerCompassTheme.colors.onSurfaceVariant,
        )
    }
}

private fun PastApplicationListMessage.text(resources: Resources): String =
    when (this) {
        PastApplicationListMessage.LimitReached -> {
            resources.getString(R.string.profile_past_application_limit_reached, MAX_PAST_APPLICATIONS)
        }

        PastApplicationListMessage.CategoryUpdateFailed -> {
            resources.getString(R.string.profile_past_application_category_failed)
        }

        PastApplicationListMessage.Deleted -> {
            resources.getString(R.string.profile_past_application_deleted)
        }

        PastApplicationListMessage.DeleteFailed -> {
            resources.getString(R.string.profile_past_application_delete_failed)
        }

        PastApplicationListMessage.Uploaded -> {
            resources.getString(R.string.profile_past_application_uploaded)
        }

        PastApplicationListMessage.UploadFailed -> {
            resources.getString(R.string.profile_past_application_upload_failed)
        }

        PastApplicationListMessage.UploadTooLarge -> {
            resources.getString(R.string.profile_past_application_upload_too_large)
        }
    }
