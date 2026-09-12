package com.careercompass.feature.onboarding.presentation.shared.component

import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import com.careercompass.core.ui.theme.CareerCompassTheme

/**
 * 피커·시트 본문을 감싸는 모달 시트 — 온보딩 단계 피커와 지문 등록 제안이 함께 쓴다. 본문 컴포저블은 stateless 로 따로 테스트한다.
 *
 * @param isDismissEnabled false 면 스크림 탭·스와이프·뒤로가기로 시트가 숨겨지지 않는다. 제출 중인 시트가
 *   쓴다(#340): 숨겨 둔 채 응답을 기다리면 늦게 온 응답이 그 사이 연 다음 시트에 떨어진다.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OnboardingSheetHost(
    onDismissRequest: () -> Unit,
    isDismissEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    // 시트 상태는 한 번만 만들어지므로 확인 규칙이 지금 값을 읽게 한다.
    val canDismiss by rememberUpdatedState(isDismissEnabled)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState =
            rememberModalBottomSheetState(
                skipPartiallyExpanded = true,
                confirmValueChange = { value -> value != SheetValue.Hidden || canDismiss },
            ),
        containerColor = CareerCompassTheme.colors.surface,
        contentColor = CareerCompassTheme.colors.onSurface,
        content = content,
    )
}
