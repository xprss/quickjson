package io.github.xprss.quickjson

import android.provider.DocumentsContract
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.xprss.quickjson.data.FileGateway
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FileGatewayInstrumentedTest {
    private val uri = DocumentsContract.buildDocumentUri(FakeJsonDocumentsProvider.AUTHORITY, "test")
    private val gateway by lazy { FileGateway(ApplicationProvider.getApplicationContext()) }

    @Before
    fun resetProvider() {
        FakeJsonDocumentsProvider.content = "{\"imported\":true}".encodeToByteArray()
        FakeJsonDocumentsProvider.modifiedAt = 1
        FakeJsonDocumentsProvider.allowAccess = true
    }

    @Test
    fun importsUtf8AndExportsToSameDocument() {
        assertEquals("{\"imported\":true}", gateway.read(uri).getOrThrow().content)
        gateway.write(uri, "[1,2,3]").getOrThrow()
        assertEquals("[1,2,3]", gateway.read(uri).getOrThrow().content)
    }

    @Test
    fun reportsRevokedPermissionAndInvalidUtf8() {
        FakeJsonDocumentsProvider.allowAccess = false
        assertTrue(gateway.read(uri).isFailure)
        FakeJsonDocumentsProvider.allowAccess = true
        FakeJsonDocumentsProvider.content = byteArrayOf(0xC3.toByte(), 0x28)
        assertTrue(gateway.read(uri).isFailure)
    }

    @Test
    fun enforcesFiveMibLimit() {
        FakeJsonDocumentsProvider.content = ByteArray((FileGateway.MAX_BYTES + 1).toInt())
        assertTrue(gateway.read(uri).isFailure)
    }
}
