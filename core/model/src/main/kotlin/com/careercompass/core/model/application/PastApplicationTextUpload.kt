package com.careercompass.core.model.application

import com.careercompass.core.model.user.ProfileFieldViolation
import java.io.ByteArrayInputStream

/**
 * 지원서 라벨 규칙 — 기능 스펙 F1-4 「각 지원서에 사용자가 직접 라벨 부여」.
 *
 * 파일 업로드와 직접 입력이 같은 라벨을 받고, 그 둘을 **온보딩 Step 4 와 마이 탭이 각각** 받는다(#181).
 * 네 경로가 같은 규칙을 지나야 하므로 규칙은 라벨의 주인인 모델 옆에 한 벌만 둔다. 서버에 라벨 수정
 * 엔드포인트가 없어(API_SPEC §4 는 업로드 요청 필드로만 받는다) 모든 경로가 업로드 전에 여기서 거른다.
 */
public object PastApplicationLabelRules {
    public const val MAX_LENGTH: Int = 50

    /** 앞뒤 공백은 라벨의 일부가 아니다 — 검증도 업로드도 다듬은 값으로 한다. */
    public fun normalize(raw: String): String = raw.trim()

    public fun validate(raw: String): ProfileFieldViolation? {
        val label = normalize(raw)
        return when {
            label.isEmpty() -> ProfileFieldViolation.Required
            label.length > MAX_LENGTH -> ProfileFieldViolation.TooLong(MAX_LENGTH)
            else -> null
        }
    }

    /**
     * 파일 업로드 라벨의 기본값 — 확장자를 뺀 파일명.
     *
     * 기본값은 그대로 눌러도 통과해야 하므로 [MAX_LENGTH] 에서 자른다. 확장자만 있는 이름(`.pdf`)처럼
     * 앞부분이 비면 파일명을 통째로 쓴다.
     */
    public fun defaultLabelFor(fileName: String): String {
        val base = normalize(fileName.substringBeforeLast('.')).ifEmpty { normalize(fileName) }
        return base.take(MAX_LENGTH)
    }
}

/** 직접 쓴 지원서를 업로드로 옮기지 못한 이유. */
public sealed interface PastApplicationTextUploadFailure {
    /** 라벨이 규칙에 맞지 않다. */
    public data class InvalidLabel(
        val violation: ProfileFieldViolation,
    ) : PastApplicationTextUploadFailure

    /** 본문이 비었다. */
    public data object EmptyContent : PastApplicationTextUploadFailure

    /** 본문이 업로드 상한([MAX_PAST_APPLICATION_FILE_BYTES])을 넘었다. */
    public data object TooLarge : PastApplicationTextUploadFailure
}

/**
 * 직접 쓴 지원서를 업로드 파일로 옮긴다 — **텍스트를 TXT 파일로 만들어 같은 엔드포인트로 보낸다.**
 *
 * ### 왜 새 엔드포인트를 요구하지 않는가 (#181 의 계약 판단)
 * API_SPEC v0.1 §4 에는 `POST /past-applications/upload`(multipart) 만 있고 텍스트 본문을 받는 자리가 없다.
 * 길은 둘이었다 — 텍스트용 엔드포인트를 새로 정의하거나, 텍스트를 파일로 만들어 기존 것을 쓰거나.
 *
 * **후자를 골랐다.** 서버가 업로드 뒤에 하는 일(텍스트 추출 → AI 항목 분류 → 저장)이 두 경로에서 완전히
 * 같고, `txt` 는 §4 가 이미 받는 형식([PastApplicationFileFormat.Txt])이라 추출 단계마저 같기 때문이다.
 * 엔드포인트를 새로 만들면 서버는 같은 파이프라인을 두 입구로 다시 열어야 하고, 두 입구는 언젠가 갈린다 —
 * 한쪽만 상한이 바뀌거나 한쪽만 분류기가 업데이트되는 식이다. 이 판단은 온보딩 Step 4 가 이미 쓰던 것을
 * 명시적 계약으로 끌어올린 것이고, 두 화면이 이제 이 함수 하나를 지난다.
 *
 * 서버 저장소에 요구할 것이 없다는 것이 이 선택의 값이다.
 */
public fun pastApplicationTextUpload(
    label: String,
    content: String,
): Result<Pair<UploadFile, String>> {
    PastApplicationLabelRules.validate(label)?.let {
        return Result.failure(PastApplicationTextUploadException(PastApplicationTextUploadFailure.InvalidLabel(it)))
    }
    if (content.isBlank()) {
        return Result.failure(PastApplicationTextUploadException(PastApplicationTextUploadFailure.EmptyContent))
    }
    val normalizedLabel = PastApplicationLabelRules.normalize(label)
    val bytes = content.toByteArray(Charsets.UTF_8)
    if (bytes.size > MAX_PAST_APPLICATION_FILE_BYTES) {
        return Result.failure(PastApplicationTextUploadException(PastApplicationTextUploadFailure.TooLarge))
    }
    // 파일명은 라벨에서 만든다 — 경로 구분자만 걷어 낸다. 서버는 이름을 저장 키로 쓰지 않지만,
    // multipart 의 filename 이 경로처럼 보이면 중간 프록시가 다르게 다룰 수 있다.
    val file =
        UploadFile(
            fileName = "${normalizedLabel.replace('/', ' ')}.${PastApplicationFileFormat.Txt.extension}",
            sizeBytes = bytes.size.toLong(),
        ) { ByteArrayInputStream(bytes) }
    return Result.success(file to normalizedLabel)
}

/** [pastApplicationTextUpload] 가 실패를 `Result` 로 나르기 위한 껍데기. 화면은 [failure] 만 본다. */
public class PastApplicationTextUploadException(
    public val failure: PastApplicationTextUploadFailure,
) : Exception(failure.toString())
