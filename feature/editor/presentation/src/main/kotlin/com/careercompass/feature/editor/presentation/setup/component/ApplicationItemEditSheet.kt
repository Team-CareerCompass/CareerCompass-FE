package com.careercompass.feature.editor.presentation.setup.component

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import com.careercompass.core.ui.component.CareerCompassButton
import com.careercompass.core.ui.component.CareerCompassTextField
import com.careercompass.core.ui.failure.toMessage
import com.careercompass.core.ui.theme.CareerCompassTheme
import com.careercompass.feature.editor.domain.model.ApplicationItemRules
import com.careercompass.feature.editor.presentation.R
import com.careercompass.feature.editor.presentation.setup.ApplicationItemEditorEvent
import com.careercompass.feature.editor.presentation.setup.ApplicationItemEditorState

/**
 * 문항 하나를 넣거나 고치는 시트 — 추가와 수정이 같은 화면이다.
 *
 * 둘을 나누지 않은 이유는 입력이 같기 때문이다(질문·글자 수 제한). 나누면 검증과 문구가 두 벌이 되고, 한쪽만
 * 고쳐지는 날이 온다. 제목만 갈아 끼운다.
 *
 * **글자 수 제한은 필수가 아니다.** 공고가 글자 수를 안 적는 일은 흔하고, 그때는 서버가 400~600자로
 * 만든다(F4-2). 자리표시자가 그 사실을 미리 말한다 — 비워 두면 무슨 일이 일어나는지 모른 채 비우게 하지 않는다.
 */
@Composable
public fun ApplicationItemEditSheet(
    state: ApplicationItemEditorState,
    onEvent: (ApplicationItemEditorEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spacing = CareerCompassTheme.spacing

    Column(
        modifier =
            modifier
                .fillMaxWidth()
                .padding(horizontal = spacing.large)
                .padding(bottom = spacing.large),
        verticalArrangement = Arrangement.spacedBy(spacing.medium),
    ) {
        Text(
            text =
                stringResource(
                    if (state.isNew) R.string.editor_item_editor_add_title else R.string.editor_item_editor_edit_title,
                ),
            style = CareerCompassTheme.typography.headline4,
            color = CareerCompassTheme.colors.onSurface,
        )
        CareerCompassTextField(
            value = state.question,
            onValueChange = { onEvent(ApplicationItemEditorEvent.QuestionChanged(it)) },
            label = stringResource(R.string.editor_item_editor_question_label),
            placeholder = stringResource(R.string.editor_item_editor_question_placeholder),
            errorMessage = state.questionError?.toMessage(),
            isError = state.questionError != null,
            modifier = Modifier.fillMaxWidth(),
        )
        CareerCompassTextField(
            value = state.maxChars,
            onValueChange = { onEvent(ApplicationItemEditorEvent.MaxCharsChanged(it)) },
            label = stringResource(R.string.editor_item_editor_max_chars_label),
            placeholder =
                stringResource(
                    R.string.editor_item_editor_max_chars_placeholder,
                    ApplicationItemRules.DEFAULT_MIN_CHARS,
                    ApplicationItemRules.DEFAULT_MAX_CHARS,
                ),
            errorMessage = state.maxCharsError?.toMessage(),
            isError = state.maxCharsError != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )
        CareerCompassButton(
            text = stringResource(R.string.editor_item_editor_submit),
            onClick = { onEvent(ApplicationItemEditorEvent.Submitted) },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
