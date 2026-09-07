package com.careercompass.feature.profile.presentation.basicinfo

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.ui.component.GraduationDatePickerEvent
import com.careercompass.core.ui.component.GraduationDatePickerSheet
import com.careercompass.core.ui.component.SchoolPickerEvent
import com.careercompass.core.ui.component.SchoolPickerSheet
import com.careercompass.core.ui.mvi.ObserveFlag
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.launch

/**
 * 프로필 편집 진입점 — 마이 홈의 「프로필 편집」이 연다(#176).
 *
 * 학교·졸업 예정 시트는 `core:ui` 의 것을 그대로 쓴다. 온보딩 Step 1 과 같은 시트이고, 규칙도
 * `core:model` 의 한 벌을 함께 본다 — 두 화면이 같은 다섯 칸을 받으므로 그렇지 않으면 반드시 어긋난다.
 *
 * @param onBackClick 뒤로 — 저장 여부와 무관하게 마이 홈으로 돌아간다.
 * @param onSaved 저장이 끝났다. 셸이 뒤로 보낸다 — 저장한 값은 마이 홈이 캐시로 이미 보고 있다.
 * @param onSessionEnded 401 로 세션이 끝났다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
public fun ProfileEditScreen(
    onBackClick: () -> Unit,
    onSaved: () -> Unit,
    onSessionEnded: (ProfileSessionEnd) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ProfileEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()

    ObserveFlag(
        raised = state.isSaved,
        consumed = ProfileEditIntent.ConsumeSaved,
        onIntent = viewModel::onIntent,
        onRaised = onSaved,
    )
    ObserveSignal(
        signal = state.sessionEnd,
        consumed = ProfileEditIntent.ConsumeSessionEnded,
        onIntent = viewModel::onIntent,
        onSignal = onSessionEnded,
    )
    ObserveSignal(
        signal = state.message,
        consumed = ProfileEditIntent.ConsumeMessage,
        onIntent = viewModel::onIntent,
    ) { message ->
        snackbarScope.launch { snackbarHostState.showSnackbar(resources.getString(message.messageRes())) }
    }

    Box(modifier = modifier.fillMaxSize()) {
        ProfileEditContent(
            state = state,
            onEvent = { event ->
                if (event == ProfileEditEvent.BackClicked) onBackClick() else viewModel.onIntent(ProfileEditIntent.Screen(event))
            },
        )
        SnackbarHost(hostState = snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    state.schoolPicker?.let { picker ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(ProfileEditIntent.SchoolPicker(SchoolPickerEvent.Dismissed)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            SchoolPickerSheet(state = picker, onEvent = { viewModel.onIntent(ProfileEditIntent.SchoolPicker(it)) })
        }
    }
    state.graduationPicker?.let { picker ->
        ModalBottomSheet(
            onDismissRequest = { viewModel.onIntent(ProfileEditIntent.GraduationPicker(GraduationDatePickerEvent.Dismissed)) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = CareerCompassTheme.colors.surface,
        ) {
            GraduationDatePickerSheet(state = picker, onEvent = { viewModel.onIntent(ProfileEditIntent.GraduationPicker(it)) })
        }
    }
}

private fun ProfileEditMessage.messageRes(): Int =
    when (this) {
        ProfileEditMessage.SaveFailed -> R.string.profile_edit_save_failed
        ProfileEditMessage.SaveRejected -> R.string.profile_edit_save_rejected
    }
