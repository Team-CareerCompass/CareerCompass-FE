package com.careercompass.feature.profile.presentation.experience

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.experience.MAX_EXPERIENCE_CARDS
import com.careercompass.core.ui.component.ExperienceDeleteDialog
import com.careercompass.core.ui.component.ExperienceDeleteEvent
import com.careercompass.core.ui.component.ExperienceQuickAddEvent
import com.careercompass.core.ui.component.ExperienceQuickAddSheet
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.launch

/**
 * 경험 카드 목록 진입점 — 마이 홈의 「경험 카드」가 연다(#178).
 *
 * 화면에 다시 들어올 때마다 목록을 읽는다. 편집·삭제(#179)를 하고 돌아오면 목록과 개수가 바뀌어 있어서다.
 *
 * 등록·수정은 목록 위에 뜨는 시트에서 끝난다 — 온보딩 Step 3 와 같은 시트이고, 유형별 필수 규칙과 날짜
 * 정밀도 규칙도 `core:ui` 의 같은 전이를 쓴다(#179). 삭제는 확인 다이얼로그를 거친다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ExperienceListScreen(
    onBackClick: () -> Unit,
    onSessionEnded: (ProfileSessionEnd) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExperienceListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    LifecycleResumeEffect(Unit) {
        viewModel.onIntent(ExperienceListIntent.Refresh)
        onPauseOrDispose { }
    }

    ObserveSignal(
        signal = state.sessionEnd,
        consumed = ExperienceListIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
        onSignal = onSessionEnded,
    )
    ObserveSignal(
        signal = state.message,
        consumed = ExperienceListIntent.ConsumeMessage,
        onIntent = viewModel::onIntent,
    ) { message ->
        snackbarScope.launch { snackbarHostState.showSnackbar(message.text(resources)) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ExperienceListContent(
            state = state,
            onEvent = { event ->
                if (event == ExperienceListEvent.BackClicked) {
                    onBackClick()
                } else {
                    viewModel.onIntent(ExperienceListIntent.Screen(event))
                }
            },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.editor?.let { editor ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(ExperienceListIntent.Editor(ExperienceQuickAddEvent.Dismissed)) },
            // 저장 중에는 스와이프·스크림으로 시트가 숨겨지지 않게 한다 — 숨긴 뒤 닫기를 무시하면 빈 창만 남는다.
            sheetState =
                rememberModalBottomSheetState(
                    skipPartiallyExpanded = true,
                    confirmValueChange = { value -> value != SheetValue.Hidden || !state.isSavingCard },
                ),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            ExperienceQuickAddSheet(
                state = editor.copy(isSubmitting = state.isSavingCard),
                onEvent = { viewModel.onIntent(ExperienceListIntent.Editor(it)) },
            )
        }
    }

    state.pendingDeletion?.let { target ->
        ExperienceDeleteDialog(
            state = target,
            onEvent = { event ->
                when (event) {
                    ExperienceDeleteEvent.Confirmed -> viewModel.onIntent(ExperienceListIntent.ConfirmDelete)
                    ExperienceDeleteEvent.Dismissed -> viewModel.onIntent(ExperienceListIntent.DismissDelete)
                }
            },
        )
    }
}

/** 상한 문구는 숫자를 문장에 박지 않고 `core:model` 의 상수를 실어 나른다. */
private fun ExperienceListMessage.text(resources: Resources): String =
    when (this) {
        ExperienceListMessage.LimitReached,
        ExperienceListMessage.SaveLimitExceeded,
        -> resources.getString(R.string.profile_experience_limit_reached, MAX_EXPERIENCE_CARDS)

        ExperienceListMessage.LoadMoreFailed -> resources.getString(R.string.profile_experience_load_more_failed)

        ExperienceListMessage.Saved -> resources.getString(R.string.profile_experience_saved)

        ExperienceListMessage.SaveFailed -> resources.getString(R.string.profile_experience_save_failed)

        ExperienceListMessage.Deleted -> resources.getString(R.string.profile_experience_deleted)

        ExperienceListMessage.DeleteFailed -> resources.getString(R.string.profile_experience_delete_failed)
    }
