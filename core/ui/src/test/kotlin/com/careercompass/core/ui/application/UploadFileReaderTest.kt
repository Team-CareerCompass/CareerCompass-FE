package com.careercompass.core.ui.application

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.ProviderInfo
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.careercompass.core.model.application.MAX_PAST_APPLICATION_FILE_BYTES
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowContentResolver
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class UploadFileReaderTest {
    @get:Rule
    val temporary = TemporaryFolder()

    private val provider = TestDocumentProvider()
    private val uri = Uri.parse("content://application-upload-test/document/1")
    private val resolver get() = RuntimeEnvironment.getApplication().contentResolver

    @Before
    fun setUp() {
        provider.file = temporary.newFile("body.txt").apply { writeText("body") }
        provider.attachInfo(
            RuntimeEnvironment.getApplication(),
            ProviderInfo().apply { authority = "application-upload-test" },
        )
        ShadowContentResolver.registerProviderInternal("application-upload-test", provider)
    }

    @Test
    fun `지원 형식은 동일한 검사와 새 스트림으로 읽는다`() {
        for (name in listOf("지원서.pdf", "지원서.DOCX", "지원서.txt")) {
            provider.name = name
            val file = readUploadFile(resolver, uri).getOrThrow()
            assertEquals(name, file.fileName)
            assertEquals(4L, file.sizeBytes)
            repeat(2) { assertEquals("body", file.openStream().bufferedReader().use { it.readText() }) }
        }
    }

    @Test
    fun `형식과 크기를 업로드 전에 거부한다`() {
        provider.name = "photo.jpg"
        assertEquals(UploadFileSelectionReason.UnsupportedFile, failure())
        provider.name = "지원서.pdf"
        provider.size = MAX_PAST_APPLICATION_FILE_BYTES + 1
        assertEquals(UploadFileSelectionReason.FileTooLarge, failure())
        provider.size = MAX_PAST_APPLICATION_FILE_BYTES
        assertTrue(readUploadFile(resolver, uri).isSuccess)
    }

    @Test
    fun `크기가 없으면 descriptor를 읽고 권한을 잃으면 실패를 반환한다`() {
        provider.size = null
        assertEquals(4L, readUploadFile(resolver, uri).getOrThrow().sizeBytes)
        provider.denied = true
        assertEquals(UploadFileSelectionReason.Unknown, failure())
    }

    private fun failure() = (readUploadFile(resolver, uri).exceptionOrNull() as UploadFileSelectionException).reason

    private class TestDocumentProvider : ContentProvider() {
        var name = "지원서.pdf"
        var size: Long? = 4L
        var denied = false
        lateinit var file: File

        override fun onCreate() = true

        override fun query(
            uri: Uri,
            projection: Array<out String>?,
            selection: String?,
            selectionArgs: Array<out String>?,
            sortOrder: String?,
        ): Cursor {
            if (denied) throw SecurityException("document permission expired")
            return MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE)).apply { addRow(arrayOf<Any?>(name, size)) }
        }

        override fun openFile(
            uri: Uri,
            mode: String,
        ): ParcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

        override fun getType(uri: Uri): String = "application/pdf"

        override fun insert(
            uri: Uri,
            values: ContentValues?,
        ): Uri? = null

        override fun delete(
            uri: Uri,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0

        override fun update(
            uri: Uri,
            values: ContentValues?,
            selection: String?,
            selectionArgs: Array<out String>?,
        ): Int = 0
    }
}
