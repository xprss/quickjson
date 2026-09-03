package io.github.xprss.quickjson

import android.provider.DocumentsContract
import android.os.Bundle
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
        setProviderState(content = "{\"imported\":true}".encodeToByteArray(), modifiedAt = 1, allowAccess = true)
    }

    @Test
    fun importsUtf8AndExportsToSameDocument() {
        assertEquals("{\"imported\":true}", gateway.read(uri).getOrThrow().content)
        gateway.write(uri, "[1,2,3]").getOrThrow()
        assertEquals("[1,2,3]", gateway.read(uri).getOrThrow().content)
    }

    @Test
    fun reportsRevokedPermissionAndInvalidUtf8() {
        setProviderState(allowAccess = false)
        assertTrue(gateway.read(uri).isFailure)
        setProviderState(content = byteArrayOf(0xC3.toByte(), 0x28), allowAccess = true)
        assertTrue(gateway.read(uri).isFailure)
    }

    @Test
    fun enforcesFiveMibLimit() {
        setProviderState(declaredSize = FileGateway.MAX_BYTES + 1)
        assertTrue(gateway.read(uri).isFailure)
    }

    private fun setProviderState(
        content: ByteArray? = null,
        modifiedAt: Long? = null,
        allowAccess: Boolean? = null,
        declaredSize: Long = -1,
    ) {
        ApplicationProvider.getApplicationContext<android.content.Context>().contentResolver.call(
            uri,
            "set-state",
            null,
            Bundle().apply {
                content?.let { putByteArray("content", it) }
                modifiedAt?.let { putLong("modifiedAt", it) }
                allowAccess?.let { putBoolean("allowAccess", it) }
                putLong("declaredSize", declaredSize)
            },
        )
    }
}
