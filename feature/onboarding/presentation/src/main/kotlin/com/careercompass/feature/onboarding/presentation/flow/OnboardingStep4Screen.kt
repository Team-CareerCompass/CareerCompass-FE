package com.careercompass.feature.onboarding.presentation.flow

import android.content.ContentResolver
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.careercompass.core.model.application.PastApplicationFileFormat
import com.careercompass.core.model.application.PastApplicationItem
import com.careercompass.core.model.application.UploadFile
import com.careercompass.core.ui.component.PastApplicationItemCategoryEvent
import com.careercompass.core.ui.component.PastApplicationItemCategorySheet
import com.careercompass.core.ui.component.labelResId
import com.careercompass.feature.onboarding.presentation.OnboardingApplicationDocument
import com.careercompass.feature.onboarding.presentation.OnboardingApplicationDocumentFormat
import com.careercompass.feature.onboarding.presentation.OnboardingApplicationDocumentStatus
import com.careercompass.feature.onboarding.presentation.OnboardingApplicationItem
import com.careercompass.feature.onboarding.presentation.OnboardingStep4Content
import com.careercompass.feature.onboarding.presentation.OnboardingStep4Event
import com.careercompass.feature.onboarding.presentation.OnboardingStep4UiState
import com.careercompass.feature.onboarding.presentation.flow.component.OnboardingFlowFailureHost
import com.careercompass.feature.onboarding.presentation.flow.util.UploadFileSelectionException
import com.careercompass.feature.onboarding.presentation.flow.util.readUploadFile
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.DirectInputSheet
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelEvent
import com.careercompass.feature.onboarding.presentation.pastapplication.UploadLabelSheet
import com.careercompass.feature.onboarding.presentation.shared.component.OnboardingSheetHost

/**
 * Step 4(과거 지원서) 화면의 상태 배선. 파일 선택(SAF)은 여기서 열고, 읽은 [com.careercompass.core.model.application.UploadFile]
 * 만 [viewModel] 에 넘긴다. [viewModel] 은 그래프 스코프 [OnboardingViewModel] 이어야 한다.
 *
 * 라벨 시트가 열린 채 프로세스가 죽으면 고른 파일도 함께 사라진다 — `UploadFile` 은 스트림을 여는 람다라 어떤
 * 저장소에도 담기지 않는다(#133). 그래서 **`Uri` 만** 여기서 `rememberSaveable` 로 들고 있다가, 살아난 뒤 같은
 * 문서를 다시 읽어 시트를 세운다. 이 값이 [OnboardingViewModel] 로 가지 않는 이유는 두 가지다 — 플랫폼 타입이라
 * presentation 로직에 들이지 않고, SAF 권한이 이 화면(태스크)에 매인 값이라 화면과 수명이 같다. 라벨 글자 쪽은
 * 반대로 ViewModel 이 소유한 상태라 [OnboardingInputDraft] 가 `SavedStateHandle` 에 남긴다.
 *
 * @param onSessionEnded 401 로 세션이 끝났다 — 앱 셸이 사유를 만료로 갈라 로그인 화면으로 보낸다(#211).
 */
@Composable
public fun OnboardingStep4Screen(
    viewModel: OnboardingViewModel,
    onNavigate: (OnboardingDestination) -> Unit,
    onBack: () -> Unit,
    onSessionEnded: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val contentResolver = LocalContext.current.contentResolver
    var pendingUploadUri by rememberSaveable { mutableStateOf<String?>(null) }
    ConsumePendingNavigation(
        destination = state.pendingNavigation,
        onNavigate = onNavigate,
        onConsumed = { viewModel.onIntent(OnboardingIntent.ConsumeNavigation) },
    )
    ConsumeSessionEnd(
        sessionEnded = state.sessionEnded,
        onSessionEnded = onSessionEnded,
        onConsumed = { viewModel.onIntent(OnboardingIntent.ConsumeSessionEnded) },
    )

    val documentPicker =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            readUploadFile(contentResolver, uri)
                .onSuccess { file ->
                    pendingUploadUri = uri.toString()
                    viewModel.onIntent(OnboardingIntent.FileSelected(file))
                }.onFailure { throwable ->
                    val reason = (throwable as? UploadFileSelectionException)?.reason ?: OnboardingFailureReason.Unknown
                    viewModel.onIntent(OnboardingIntent.FileSelectionFailed(reason, throwable))
                }
        }

    RestorePendingUpload(
        uri = pendingUploadUri.takeIf { state.uploadLabel == null && !state.isResolvingEntry },
        contentResolver = contentResolver,
        onRestored = { viewModel.onIntent(OnboardingIntent.FileSelected(it)) },
        onUnavailable = {
            // 권한이 끊겼거나 문서가 사라졌다 — 조용히 버린다. 사용자가 방금 한 일이 아니라 경고할 사건이 아니고,
            // 취소와 같은 처리라 ViewModel 이 든 라벨 초안도 함께 비운다.
            pendingUploadUri = null
            viewModel.onIntent(OnboardingIntent.UploadLabel(UploadLabelEvent.Dismissed))
        },
    )

    OnboardingFlowFailureHost(
        failure = state.failure,
        onDismiss = { viewModel.onIntent(OnboardingIntent.ConsumeFailure) },
        modifier = modifier,
    ) {
        OnboardingStep4Content(
            state = state.step4.toUiState(isInputEnabled = state.isInputEnabled),
            onEvent = { event ->
                when (event) {
                    OnboardingStep4Event.BackClicked -> onBack()
                    OnboardingStep4Event.UploadClicked -> documentPicker.launch(SUPPORTED_MIME_TYPES)
                    else -> viewModel.onIntent(OnboardingIntent.Step4(event))
                }
            },
        )
    }

    state.directInput?.let { input ->
        OnboardingSheetHost(onDismissRequest = { viewModel.onIntent(OnboardingIntent.DirectInput(DirectInputEvent.Dismissed)) }) {
            DirectInputSheet(state = input, onEvent = { viewModel.onIntent(OnboardingIntent.DirectInput(it)) })
        }
    }

    state.uploadLabel?.let { sheet ->
        // 시트가 닫혔으면(취소·업로드 시작) 보관하던 Uri 도 버린다 — 다음에 고를 파일과 섞이지 않게.
        // 라벨 오류로 시트가 그대로 남았을 때는 지킨다: 아직 그 파일에 이름을 붙이는 중이다.
        val onEvent = { event: UploadLabelEvent ->
            viewModel.onIntent(OnboardingIntent.UploadLabel(event))
            if (viewModel.uiState.value.uploadLabel == null) pendingUploadUri = null
        }
        OnboardingSheetHost(onDismissRequest = { onEvent(UploadLabelEvent.Dismissed) }) {
            UploadLabelSheet(state = sheet, onEvent = onEvent)
        }
    }

    state.itemCategoryPicker?.let { picker ->
        OnboardingSheetHost(
            onDismissRequest = { viewModel.onIntent(OnboardingIntent.ItemCategoryPicker(PastApplicationItemCategoryEvent.Dismissed)) },
        ) {
            PastApplicationItemCategorySheet(state = picker, onEvent = { viewModel.onIntent(OnboardingIntent.ItemCategoryPicker(it)) })
        }
    }
}

/**
 * 보관해 둔 [uri] 의 문서를 다시 읽어 업로드 라벨 시트를 세운다 — 프로세스가 죽어 파일 참조를 잃은 뒤에만 할 일이 있다.
 *
 * 호출부가 「지금 복원해야 하는가」를 [uri] 의 null 여부로 넘긴다. 진입 판정이 끝나기를 기다리는 것도 그중
 * 하나다 — [OnboardingIntent.FileSelected] 는 입력이 잠긴 동안 시트를 열지 않아, 판정 전에 부르면 복원이
 * 조용히 실패한다.
 */
@Composable
private fun RestorePendingUpload(
    uri: String?,
    contentResolver: ContentResolver,
    onRestored: (UploadFile) -> Unit,
    onUnavailable: () -> Unit,
) {
    LaunchedEffect(uri) {
        if (uri == null) return@LaunchedEffect
        readUploadFile(contentResolver, Uri.parse(uri))
            .onSuccess(onRestored)
            .onFailure { onUnavailable() }
    }
}

@Composable
private fun OnboardingStep4FormState.toUiState(isInputEnabled: Boolean): OnboardingStep4UiState =
    OnboardingStep4UiState(
        uploadedDocuments = documents.map { it.toUiModel() },
        expandedDocumentId = expandedDocumentId,
        isInputEnabled = isInputEnabled,
        isListLoading = isLoading,
    )

/**
 * 라벨은 사용자가 정한 이름이라 형식을 담고 있지 않다. 형식 배지는 올린 파일의 실제 이름에서 읽고, 파일이 없는
 * 서버 목록의 문서는 라벨에서 읽는다(API_SPEC §4 는 라벨만 돌려준다 — 확장자가 없으면 직접 입력 문서라 TXT).
 * 카드 제목은 「라벨.확장자」다: 기본 라벨을 그대로 둔 업로드는 파일명을 라벨로 쓰던 이전과 같은 문구가 된다.
 * 크기는 화면에 그리지 않는 값이라 계약 하한으로 채운다.
 */
@Composable
private fun OnboardingUploadDocument.toUiModel(): OnboardingApplicationDocument {
    val format = PastApplicationFileFormat.fromFileName(file?.fileName ?: label) ?: PastApplicationFileFormat.Txt
    val extension = format.extension
    val fileName = if (label.endsWith(".$extension", ignoreCase = true)) label else "$label.$extension"
    val status = status
    return OnboardingApplicationDocument(
        id = id,
        fileName = fileName,
        format = format.toUiFormat(),
        fileSizeBytes = sizeBytes ?: UNKNOWN_SIZE_PLACEHOLDER_BYTES,
        status =
            when (status) {
                OnboardingUploadStatus.Processing -> OnboardingApplicationDocumentStatus.Processing
                is OnboardingUploadStatus.Completed -> OnboardingApplicationDocumentStatus.Completed(status.classifiedItemCount)
                is OnboardingUploadStatus.Failed -> OnboardingApplicationDocumentStatus.Failed(status.reason.toShortMessage())
            },
        items = (status as? OnboardingUploadStatus.Completed)?.items.orEmpty().map { it.toUiModel() },
    )
}

/** 본문은 카드 아래 미리보기라 원문을 그대로 넘기고, 줄 수 제한은 화면이 한다. */
@Composable
private fun PastApplicationItem.toUiModel(): OnboardingApplicationItem =
    OnboardingApplicationItem(
        id = id,
        categoryLabel = stringResource(category.labelResId()),
        contentPreview = content,
        needsReview = !confident,
    )

private fun PastApplicationFileFormat.toUiFormat(): OnboardingApplicationDocumentFormat =
    when (this) {
        PastApplicationFileFormat.Pdf -> OnboardingApplicationDocumentFormat.PDF
        PastApplicationFileFormat.Docx -> OnboardingApplicationDocumentFormat.DOCX
        PastApplicationFileFormat.Txt -> OnboardingApplicationDocumentFormat.TXT
    }

private val SUPPORTED_MIME_TYPES: Array<String> = PastApplicationFileFormat.entries.map { it.mimeType }.toTypedArray()

private const val UNKNOWN_SIZE_PLACEHOLDER_BYTES = 1L
