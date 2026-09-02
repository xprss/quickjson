package io.github.xprss.quickjson.data

import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class FileGatewayTest {
    @Test
    fun detectsHashOrTimestampConflict() {
        assertFalse(FileGateway.externalChanged("same", 10, "same", 10))
        assertTrue(FileGateway.externalChanged("old", 10, "new", 10))
        assertTrue(FileGateway.externalChanged("same", 10, "same", 11))
        assertFalse(FileGateway.externalChanged(null, null, "new", 11))
    }

    @Test
    fun sha256IsStable() {
        assertTrue(FileGateway.sha256("json".encodeToByteArray()).matches(Regex("[0-9a-f]{64}")))
    }
}
