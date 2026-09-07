package com.careercompass.feature.profile.presentation.experience

import android.content.res.Resources
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import com.careercompass.core.ui.mvi.ObserveFlag
import com.careercompass.core.ui.mvi.ObserveSignal
import com.careercompass.feature.profile.presentation.R
import com.careercompass.feature.profile.presentation.home.ProfileSessionEnd
import kotlinx.coroutines.launch

/**
 * 경험 카드 목록 진입점 — 마이 홈의 「경험 카드」가 연다(#178).
 *
 * 화면에 다시 들어올 때마다 목록을 읽는다. 편집·삭제(#179)를 하고 돌아오면 목록과 개수가 바뀌어 있어서다.
 *
 * @param onCardClick 카드 하나를 열었다. 편집 화면은 #179 가 붙인다.
 * @param onAddClick 카드를 추가한다.
 */
@Composable
public fun ExperienceListScreen(
    onBackClick: () -> Unit,
    onCardClick: (Long) -> Unit,
    onAddClick: () -> Unit,
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
        signal = state.pendingCardId,
        consumed = ExperienceListIntent.ConsumeCardNavigation,
        onIntent = viewModel::onIntent,
        onSignal = onCardClick,
    )
    ObserveFlag(
        raised = state.isAddRequested,
        consumed = ExperienceListIntent.ConsumeAddRequest,
        onIntent = viewModel::onIntent,
        onRaised = onAddClick,
    )
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
}

/** 상한 문구는 숫자를 문장에 박지 않고 `core:model` 의 상수를 실어 나른다. */
private fun ExperienceListMessage.text(resources: Resources): String =
    when (this) {
        ExperienceListMessage.LimitReached -> resources.getString(R.string.profile_experience_limit_reached, MAX_EXPERIENCE_CARDS)
        ExperienceListMessage.LoadMoreFailed -> resources.getString(R.string.profile_experience_load_more_failed)
    }
