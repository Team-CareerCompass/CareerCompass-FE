package com.careercompass.feature.profile.presentation.pastapplication

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.application.MAX_PAST_APPLICATIONS
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassButtonSize
import com.careercompass.core.ui.component.CareerCompassButtonVariant
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
 * @param onAddClick 지원서를 새로 올린다. 앱 안에서 직접 쓰는 길은 #181 몫이다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun PastApplicationListScreen(
    onBackClick: () -> Unit,
    onAddClick: () -> Unit,
    onSessionEnded: (ProfileSessionEnd) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PastApplicationListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(PastApplicationListIntent.Refresh)
        onPauseOrDispose { }
    }

    ObserveFlag(
        raised = state.isAddRequested,
        consumed = PastApplicationListIntent.ConsumeAddRequest,
        onIntent = viewModel::onIntent,
        onRaised = onAddClick,
    )
    ObserveSignal(
        signal = state.sessionEnd,
        consumed = PastApplicationListIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
        onSignal = onSessionEnded,
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
                    onBackClick()
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
    }
