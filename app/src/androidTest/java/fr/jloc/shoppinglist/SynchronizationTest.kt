package fr.jloc.shoppinglist

import androidx.test.platform.app.InstrumentationRegistry
import fr.jloc.shoppinglist.business.AppRepository
import fr.jloc.shoppinglist.business.PadItem
import fr.jloc.shoppinglist.business.PadItemContent
import fr.jloc.shoppinglist.business.PadSyncParams
import fr.jloc.shoppinglist.business.sync.FakeRemotePad
import fr.jloc.shoppinglist.business.sync.RemotePad
import fr.jloc.shoppinglist.business.sync.PadKey
import fr.jloc.shoppinglist.business.sync.sync
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.util.UUID

class SynchronizationTest {

    @Test
    fun emptyTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
    }

    @Test
    fun syncWithExistingLocalDataTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )

        local.addPadItem(shoppingListId, "item 1", "note 1")
        local.addPadItem(shoppingListId, "item 2", "note 2")
        local.setPadItemChecked(shoppingListId, "item 2", true)

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
    }

    @Test
    fun createItemsLocallyTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )
        sync(local, remote, shoppingListId)

        local.addPadItem(shoppingListId, "item 1", "note 1")
        local.addPadItem(shoppingListId, "item 2", "note 2")
        local.setPadItemChecked(shoppingListId, "item 2", true)

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
    }


    @Test
    fun createItemsRemotelyTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )
        sync(local, remote, shoppingListId)

        remote.putForTesting(
            mapOf(
                "item 1" to PadItemContent(note = "note 1", checked = false),
                "item 2" to PadItemContent(note = "note 2", checked = true),
            )
        )

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
    }

    @Test
    fun updateItemsLocallyTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )

        local.addPadItem(shoppingListId, "item 1", "note 1")
        local.addPadItem(shoppingListId, "item 2", "note 2")
        local.setPadItemChecked(shoppingListId, "item 2", true)
        local.addPadItem(shoppingListId, "item 3", "note 3")

        sync(local, remote, shoppingListId)

        local.updatePadItem(shoppingListId, "item 1", "item 1", "note 1.2", false)
        local.updatePadItem(shoppingListId, "item 2", "item 2", "note 2", true)
        local.updatePadItem(shoppingListId, "item 3", "item 3.2", "note 3", false)

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
    }

    @Test
    fun updateItemsRemotelyTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )

        local.addPadItem(shoppingListId, "item 1", "note 1")
        local.addPadItem(shoppingListId, "item 2", "note 2")
        local.setPadItemChecked(shoppingListId, "item 2", true)
        local.addPadItem(shoppingListId, "item 3", "note 3")

        sync(local, remote, shoppingListId)

        remote.putForTesting(
            mapOf(
                "item 1" to PadItemContent(false, "note 1.2"),
                "item 2" to PadItemContent(false, "note 2"),
                "item 3.2" to PadItemContent(false, "note 3"),
            )
        )

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
    }

    @Test
    fun conflictBothCreatedTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )
        sync(local, remote, shoppingListId)

        local.addPadItem(shoppingListId, "item 1", "note 1")

        remote.putForTesting(
            mapOf(
                "item 1" to PadItemContent(false, "note 1.2"),
            )
        )

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
        assertEquals(
            listOf(PadItem(name = "item 1", note = "note 1", checked = false)),
            local.getPadItems(shoppingListId)
        )
    }

    @Test
    fun conflictDeletedLocallyAndUpdatedRemotelyTest() = runTest {

        val local =
            SqliteAppRepository.openInMemory(InstrumentationRegistry.getInstrumentation().targetContext)
        val remote = FakeRemotePad()
        val shoppingListId = UUID.randomUUID().toString()
        local.addPad(shoppingListId, "Test")
        local.setupPadSync(
            shoppingListId, PadSyncParams(url = "", key = PadKey.generate())
        )

        local.addPadItem(shoppingListId, "item 1", "note 1")

        sync(local, remote, shoppingListId)

        local.deletePadItem(shoppingListId, "item 1")

        remote.putForTesting(
            mapOf(
                "item 1" to PadItemContent(false, "note 1.2"),
            )
        )

        sync(local, remote, shoppingListId)

        compare(local, remote, shoppingListId)
        assertEquals(
            listOf(PadItem(name = "item 1", note = "note 1.2", checked = false)),
            local.getPadItems(shoppingListId)
        )
    }
}

private suspend fun compare(
    local: AppRepository,
    remote: RemotePad,
    shoppingListId: String,
) {

    val fetched = remote.get(null)!!
    assertEquals(local.getPadSynchronizedTag(shoppingListId), fetched.tag)
    val remoteItems = fetched.items

    val localItems =
        local.getPadItems(shoppingListId).associate { item -> item.name to item.content }

    assertEquals(localItems, remoteItems)
}
