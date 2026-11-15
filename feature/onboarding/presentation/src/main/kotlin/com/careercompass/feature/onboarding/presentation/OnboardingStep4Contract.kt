package com.careercompass.feature.onboarding.presentation

import androidx.compose.runtime.Immutable

public const val ONBOARDING_MAX_APPLICATION_UPLOAD_COUNT: Int = 10
public const val ONBOARDING_MAX_APPLICATION_FILE_SIZE_MEGABYTES: Int = 10
public const val ONBOARDING_MAX_APPLICATION_FILE_SIZE_BYTES: Long =
    ONBOARDING_MAX_APPLICATION_FILE_SIZE_MEGABYTES * 1024L * 1024L

/** File formats accepted by the past-application uploader. */
public enum class OnboardingApplicationDocumentFormat(
    public val extension: String,
    public val label: String,
) {
    PDF(extension = "pdf", label = "PDF"),
    DOCX(extension = "docx", label = "DOCX"),
    TXT(extension = "txt", label = "TXT"),
    ;

    public companion object {
        /** Resolves a supported format from a file name and rejects every other extension. */
        public fun fromFileName(fileName: String): OnboardingApplicationDocumentFormat {
            val extension = fileName.substringAfterLast(delimiter = '.', missingDelimiterValue = "")
            return entries.firstOrNull { format ->
                format.extension.equals(extension, ignoreCase = true)
            } ?: throw IllegalArgumentException("Unsupported application document format")
        }
    }
}

/** Classification lifecycle for an uploaded application document. */
@Immutable
public sealed interface OnboardingApplicationDocumentStatus {
    /** The document is still being uploaded or classified. */
    public data object Processing : OnboardingApplicationDocumentStatus

    /** Classification completed and produced [classifiedItemCount] reusable entries. */
    @Immutable
    public data class Completed(
        public val classifiedItemCount: Int,
    ) : OnboardingApplicationDocumentStatus {
        init {
            require(classifiedItemCount >= 0) { "classifiedItemCount must not be negative" }
        }
    }

    /** Classification failed with a user-facing [message]. */
    @Immutable
    public data class Failed(
        public val message: String,
    ) : OnboardingApplicationDocumentStatus {
        init {
            require(message.isNotBlank()) { "Failure message must not be blank" }
        }
    }
}

/**
 * 분류가 끝난 지원서의 항목 하나 — 기능 스펙 F1-4.
 *
 * @property id 서버 항목 id. 분류 조정 요청이 이 값을 그대로 쓴다.
 * @property needsReview 서버 분류가 불확실해(`confident=false`) 사용자 확인이 필요하다.
 */
@Immutable
public data class OnboardingApplicationItem(
    public val id: Long,
    public val categoryLabel: String,
    public val contentPreview: String,
    public val needsReview: Boolean,
) {
    init {
        require(categoryLabel.isNotBlank()) { "Application item categoryLabel must not be blank" }
        require(contentPreview.isNotBlank()) { "Application item contentPreview must not be blank" }
    }
}

/** A previously submitted application that can be reused during onboarding. */
@Immutable
public data class OnboardingApplicationDocument(
    public val id: String,
    public val fileName: String,
    public val format: OnboardingApplicationDocumentFormat,
    public val fileSizeBytes: Long,
    public val status: OnboardingApplicationDocumentStatus,
    public val items: List<OnboardingApplicationItem> = emptyList(),
) {
    init {
        require(id.isNotBlank()) { "Application document id must not be blank" }
        require(fileName.isNotBlank()) { "Application document fileName must not be blank" }
        require(OnboardingApplicationDocumentFormat.fromFileName(fileName) == format) {
            "Application document extension must match its format"
        }
        require(fileSizeBytes in 1..ONBOARDING_MAX_APPLICATION_FILE_SIZE_BYTES) {
            "Application document fileSizeBytes must be within the upload limit"
        }
        require(items.map(OnboardingApplicationItem::id).distinct().size == items.size) {
            "Application item ids must be unique"
        }
    }

    /** 펼칠 것이 있어야 펼침 토글을 그린다 — 분류 항목이 0개인 문서는 카드만 남는다. */
    public val isExpandable: Boolean
        get() = status is OnboardingApplicationDocumentStatus.Completed && items.isNotEmpty()
}

/** Immutable rendering state for the fourth onboarding step. */
@Immutable
public data class OnboardingStep4UiState(
    public val uploadedDocuments: List<OnboardingApplicationDocument> = emptyList(),
    public val expandedDocumentId: String? = null,
    public val isInputEnabled: Boolean = true,
    public val isListLoading: Boolean = false,
    public val currentStep: Int = 4,
    public val totalSteps: Int = 4,
) {
    init {
        require(
            expandedDocumentId == null ||
                uploadedDocuments.any { it.id == expandedDocumentId },
        ) {
            "expandedDocumentId must refer to an uploaded document"
        }
        require(totalSteps > 0) { "totalSteps must be positive" }
        require(currentStep in 1..totalSteps) { "currentStep must be within 1..totalSteps" }
        require(uploadedDocuments.size <= ONBOARDING_MAX_APPLICATION_UPLOAD_COUNT) {
            "uploaded document count must not exceed the upload limit"
        }
        require(
            uploadedDocuments
                .map(OnboardingApplicationDocument::id)
                .distinct()
                .size == uploadedDocuments.size,
        ) {
            "Application document ids must be unique"
        }
    }

    /**
     * Whether another document can be added without exceeding the fixed upload limit.
     *
     * 목록을 읽는 중에도 막는다 — 아직 받지 못한 목록으로 상한을 판정하지 않기 위해서다(#356).
     */
    public val isUploadEnabled: Boolean
        get() =
            isInputEnabled &&
                !isListLoading &&
                uploadedDocuments.size < ONBOARDING_MAX_APPLICATION_UPLOAD_COUNT

    /** 직접 입력도 문서를 하나 만든다 — 목록을 읽는 중에는 업로드와 같이 잠근다(#356). */
    public val isDirectInputEnabled: Boolean
        get() = isInputEnabled && !isListLoading

    /** Every uploaded document must finish classification before onboarding can complete. */
    public val isCompleteEnabled: Boolean
        get() =
            isInputEnabled &&
                uploadedDocuments.isNotEmpty() &&
                uploadedDocuments.all { document ->
                    document.status is OnboardingApplicationDocumentStatus.Completed
                }
}

/** User intentions emitted by [OnboardingStep4Content]. */
public sealed interface OnboardingStep4Event {
    public data object UploadClicked : OnboardingStep4Event

    public data object DirectInputClicked : OnboardingStep4Event

    /** Requests the document action menu, including removal. */
    public data class DocumentMenuClicked(
        public val documentId: String,
    ) : OnboardingStep4Event

    /** 분류 항목 목록을 펼치거나 접는다(F1-4 분류 확인). */
    public data class DocumentExpandToggled(
        public val documentId: String,
    ) : OnboardingStep4Event

    /** 항목의 분류를 바꾸려고 선택 시트를 연다. */
    public data class ItemCategoryClicked(
        public val documentId: String,
        public val itemId: Long,
    ) : OnboardingStep4Event

    /** Requests classification retry for a failed document. */
    public data class DocumentRetryClicked(
        public val documentId: String,
    ) : OnboardingStep4Event

    public data object BackClicked : OnboardingStep4Event

    public data object SkipClicked : OnboardingStep4Event

    public data object CompleteClicked : OnboardingStep4Event
}
