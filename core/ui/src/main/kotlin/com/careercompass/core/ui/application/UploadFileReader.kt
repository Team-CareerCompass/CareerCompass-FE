package com.careercompass.core.ui.application

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.careercompass.core.model.application.MAX_PAST_APPLICATION_FILE_BYTES
import com.careercompass.core.model.application.PastApplicationFileFormat
import com.careercompass.core.model.application.UploadFile
import java.io.IOException

public enum class UploadFileSelectionReason {
    Unknown,
    UnsupportedFile,
    FileTooLarge,
}

/** 파일 선택 결과를 [UploadFile] 로 만들지 못한 이유를 화면 사유와 함께 담는다. */
public class UploadFileSelectionException(
    val reason: UploadFileSelectionReason,
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

/**
 * SAF 로 고른 문서의 이름·크기를 읽어 [UploadFile] 을 만든다.
 *
 * 스트림은 호출마다 새로 연다 — 재시도가 같은 Uri 를 다시 읽는다. 크기를 알 수 없으면 파일 디스크립터의
 * `statSize` 로 한 번 더 시도한다.
 */
public fun readUploadFile(
    resolver: ContentResolver,
    uri: Uri,
): Result<UploadFile> {
    val metadata =
        try {
            queryMetadata(resolver, uri)
        } catch (e: SecurityException) {
            return Result.failure(UploadFileSelectionException(UploadFileSelectionReason.Unknown, "cannot read document metadata", e))
        }
    val fileName = metadata.displayName ?: uri.lastPathSegment ?: DEFAULT_FILE_NAME
    if (PastApplicationFileFormat.fromFileName(fileName) == null) {
        return Result.failure(UploadFileSelectionException(UploadFileSelectionReason.UnsupportedFile, "unsupported document format"))
    }
    val size = metadata.sizeBytes?.takeIf { it > 0 } ?: readStatSize(resolver, uri)
    return when {
        size == null || size <= 0 -> {
            Result.failure(UploadFileSelectionException(UploadFileSelectionReason.Unknown, "unknown document size"))
        }

        size > MAX_PAST_APPLICATION_FILE_BYTES -> {
            Result.failure(UploadFileSelectionException(UploadFileSelectionReason.FileTooLarge, "document exceeds size limit"))
        }

        else -> {
            Result.success(
                UploadFile(fileName = fileName, sizeBytes = size) {
                    resolver.openInputStream(uri) ?: throw IOException("cannot open document")
                },
            )
        }
    }
}

private data class DocumentMetadata(
    val displayName: String?,
    val sizeBytes: Long?,
)

private fun queryMetadata(
    resolver: ContentResolver,
    uri: Uri,
): DocumentMetadata {
    val projection = arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)
    resolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (!cursor.moveToFirst()) return DocumentMetadata(null, null)
        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
        val name = if (nameIndex >= 0 && !cursor.isNull(nameIndex)) cursor.getString(nameIndex) else null
        val size = if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) cursor.getLong(sizeIndex) else null
        return DocumentMetadata(name?.takeIf(String::isNotBlank), size)
    }
    return DocumentMetadata(null, null)
}

private fun readStatSize(
    resolver: ContentResolver,
    uri: Uri,
): Long? =
    try {
        resolver.openFileDescriptor(uri, "r")?.use { it.statSize }
    } catch (e: IOException) {
        null
    } catch (e: SecurityException) {
        null
    }

private const val DEFAULT_FILE_NAME = "document"
