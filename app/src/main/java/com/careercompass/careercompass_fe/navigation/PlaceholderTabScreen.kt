package com.careercompass.careercompass_fe.navigation

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.careercompass.careercompass_fe.R
import com.careercompass.core.ui.component.CareerCompassEmptyState
import com.careercompass.core.ui.component.CareerCompassTopAppBar

/**
 * 다른 담당 모듈(foryou·editor)이 진입점을 제공하기 전까지 탭이 비어 보이지 않게 하는 자리표시자.
 * 모듈이 붙으면 이 컴포저블과 [Route] 의 해당 항목을 지운다.
 *
 * 남은 자리표시자 탭은 분석·지원서 둘뿐이다 — 피드는 피드 그래프가, 마이는 profile 모듈의 마이 홈이 그린다(#175).
 * 탭 enum 으로 제목을 고르던 분기는 그래서 닿지 않는 가지가 됐고, 지금은 제목을 그대로 받는다.
 */
@Composable
internal fun PlaceholderTabScreen(
    title: String,
    modifier: Modifier = Modifier,
) {
    CareerCompassEmptyState(
        title = title,
        description = stringResource(R.string.placeholder_description),
        actionText = null,
        onActionClick = null,
        modifier = modifier.fillMaxSize(),
    )
}

/** 탭이 아닌 화면(알림)의 자리표시자 — 상단 바로 돌아갈 수 있다. */
@Composable
internal fun PlaceholderScreen(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize()) {
        CareerCompassTopAppBar(title = title, onBackClick = onBackClick)
        CareerCompassEmptyState(
            title = title,
            description = stringResource(R.string.placeholder_description),
            actionText = null,
            onActionClick = null,
            modifier = Modifier.weight(1f),
        )
    }
}
