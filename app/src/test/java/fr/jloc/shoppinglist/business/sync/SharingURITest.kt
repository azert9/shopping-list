package fr.jloc.shoppinglist.business.sync

import fr.jloc.shoppinglist.business.PadSyncParams

import org.junit.Assert.*
import org.junit.Test

class SharingURITest {

    @Test
    fun encodeDecode() {
        val testCases = listOf(
            SharingURI(
                padName = "abc",
                syncParams = PadSyncParams(url = "def", key = PadKey.generate()),
            ),
            SharingURI(
                padName = "abcABC012 -._~!$&'",
                syncParams = PadSyncParams(url = "def", key = PadKey.generate()),
            ),
            SharingURI(
                padName = "()+,;=:@/?*ééèè\n",
                syncParams = PadSyncParams(url = "def", key = PadKey.generate()),
            ),
            SharingURI(
                padName = "abc",
                syncParams = PadSyncParams(url = "", key = PadKey.generate()),
            ),
            SharingURI(
                padName = "abc",
                syncParams = PadSyncParams(url = "abcABC012 -._~!$&'", key = PadKey.generate()),
            ),
            SharingURI(
                padName = "abc",
                syncParams = PadSyncParams(url = "()+,;=:@/?*ééèè\n", key = PadKey.generate()),
            ),
            SharingURI(
                padName = "",
                syncParams = PadSyncParams(url = "def", key = PadKey.generate()),
            ),
        )
        for (testCase in testCases) {
            val decoded = SharingURI.decode(testCase.encode())
            assertEquals(testCase.padName, decoded.padName)
            assertEquals(testCase.syncParams.url, decoded.syncParams.url)
            assertEquals(
                testCase.syncParams.key.toBytes().toHexString(),
                decoded.syncParams.key.toBytes().toHexString(),
            )
        }
    }
}
