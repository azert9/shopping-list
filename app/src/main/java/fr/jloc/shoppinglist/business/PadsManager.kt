package fr.jloc.shoppinglist.business

import android.util.Log
import fr.jloc.shoppinglist.RemotePadImpl
import fr.jloc.shoppinglist.business.sync.PadKey
import fr.jloc.shoppinglist.business.sync.SyncError
import fr.jloc.shoppinglist.business.sync.sync
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.UUID

class PadsManager private constructor(
    private val db: AppRepository,
    private val coroutineScope: CoroutineScope,
) {
    private val padManagers = HashMap<String, PadManager>()

    var pads: List<Pad> = listOf()
        private set

    /** Not thread safe! */
    interface PadHandle : AutoCloseable {

        /** ID of the pad. */
        val id: String

        val name: String

        val syncParams: PadSyncParams?

        fun setupSync()

        val isSynchronizing: Boolean

        /** Trigger a synchronization, if another is not already running.
         * @return True iif a synchronization was started.
         */
        fun startSync(): Boolean

        /** Trigger a synchronization that will delete all data from the remote, and if successful,
         * de-configure sync locally. Should not be called while a synchronization is running.
         */
        fun unSetupSync(keepOnRemote: Boolean)

        /** If a synchronization is running, wait for its completion. Otherwise, do nothing. */
        suspend fun waitEndOfSync(): SyncError?

        /** Fetch the items of the pad. No mutation should be attempted on these items until
         * the fetch is complete.
         */
        suspend fun getItems(): List<PadItem>

        /** Delete the pad. No other operation should be attempted after this (unless documented otherwise),
         * except from closing the handle.
         */
        fun delete()

        fun rename(newName: String)

        fun deleteItem(name: String) {
            deleteItems(listOf(name))
        }

        fun deleteItems(names: List<String>)

        fun addItem(name: String, note: String)

        fun updateItem(
            oldName: String, name: String? = null, note: String? = null, checked: Boolean? = null
        )
    }

    private class PadHandleImpl(private val pad: PadManager) : PadHandle {

        private var closed = false
        private var isFetching = false
        private var isDeleting = false

        override fun close() {
            pad.held = false
            closed = true
        }

        private fun assertNotClosed() {
            assert(!closed) { "Using a closed pad handle." }
        }

        private fun assertUsable(
            ignoreSync: Boolean = false,
            ignoreFetch: Boolean = false,
            ignoreDelete: Boolean = false,
        ) {
            assertNotClosed()
            if (!ignoreSync) assert(!isSynchronizing) { "Operation not permitted while synchronizing." }
            if (!ignoreFetch) assert(!isFetching) { "Operation not permitted while fetching." }
            if (!ignoreDelete) assert(!isDeleting) { "Operation not permitted while deleting." }
        }

        override val id: String
            get() = pad.id

        override val name: String
            get() = pad.name

        override val syncParams: PadSyncParams?
            get() = pad.syncParams

        override fun setupSync() {
            assertUsable(ignoreFetch = true)
            pad.setupSync()
        }

        override val isSynchronizing: Boolean
            get() = run {
                assertNotClosed()
                pad.isSynchronizing
            }

        override fun startSync(): Boolean = run {
            assertUsable(ignoreSync = true)
            pad.startSync()
        }

        override fun unSetupSync(keepOnRemote: Boolean) {
            assertUsable(ignoreFetch = true)
            pad.unSetupSync(keepOnRemote)
        }

        override suspend fun waitEndOfSync(): SyncError? = run {
            assertNotClosed()
            pad.waitForEndOfSync()
        }

        override suspend fun getItems(): List<PadItem> {

            assertUsable(ignoreFetch = true)

            if (pad.isSynchronizing) {
                // The result could become outdated because of the synchronization.
                // In practice because of the queue, it could not, but this is an implementation detail.
                throw RuntimeException("Cannot fetch items from a pad which is synchronizing.")
            }

            val result = CompletableDeferred<List<PadItem>>()
            pad.enqueueDbOperation {
                result.complete(pad.db.getPadItems(pad.id))
            }
            return result.await()
        }

        override fun delete() {
            assertUsable()
            pad.delete()
        }

        override fun rename(newName: String) {
            assertUsable(ignoreSync = true, ignoreFetch = true)
            pad.rename(newName)
        }

        override fun deleteItems(names: List<String>) {
            assertUsable()
            pad.enqueueDbOperation {
                // TODO(perf): single query
                for (name in names) {
                    pad.db.deletePadItem(id, name)
                }
            }
        }

        override fun addItem(name: String, note: String) {
            assertUsable()
            pad.enqueueDbOperation {
                pad.db.addPadItem(id, name, note)
            }
        }

        override fun updateItem(oldName: String, name: String?, note: String?, checked: Boolean?) {
            assertUsable()
            pad.enqueueDbOperation {
                pad.db.updatePadItem(
                    padId = pad.id,
                    oldName = oldName,
                    name = name,
                    note = note,
                    checked = checked,
                )
            }
        }
    }

    /** Not thread safe! */
    private inner class PadManager(
        val id: String,
        name: String,
        val db: AppRepository,
        syncParams: PadSyncParams?,
        coroutineScope: CoroutineScope,
    ) {

        var held: Boolean = false

        private val operationsQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

        init {
            coroutineScope.launch(Dispatchers.IO) {
                for (op in operationsQueue) {
                    op()
                }
            }
        }

        var name: String = name
            private set

        var syncParams: PadSyncParams? = syncParams
            private set

        @Volatile
        private var syncDoneSignal: Deferred<SyncError?>? = null

        private var isDeleting = false

        fun setupSync() {

            checkNotDeleting()

            if (syncParams != null) {
                throw RuntimeException("Synchronization already configured for this pad.")
            }

            syncParams = PadSyncParams(
                url = "https://shopping-list.jloc.fr/api",
                key = PadKey.generate(),
            )

            doEnqueueDbOperation {
                db.setupPadSync(id, syncParams!!)
            }
        }

        val isSynchronizing: Boolean
            get() = syncDoneSignal != null

        /** Trigger a synchronization, if another is not already running.
         * @return True iif a synchronization was started.
         */
        fun startSync(): Boolean {

            checkNotDeleting()

            if (syncDoneSignal != null) {
                return false
            }

            val remote = RemotePadImpl(syncParams!!.url, syncParams!!.key)

            val signal = CompletableDeferred<SyncError?>()
            syncDoneSignal = signal

            doEnqueueDbOperation {
                var exception: SyncError? = null
                try {
                    sync(db, remote, id)
                } catch (e: SyncError) {
                    exception = e
                    Log.e("sync", "synchronization failed", e)
                } catch (e: Throwable) {
                    exception = SyncError.newUnexpectedError("Unexpected exception.", e)
                    Log.e("sync", "synchronization failed", e)
                } finally {
                    syncDoneSignal = null
                    signal.complete(exception)
                }
            }

            return true
        }

        fun unSetupSync(keepOnRemote: Boolean) {

            checkNotDeleting()

            if (syncDoneSignal != null) {
                throw Exception("Cannot delete synchronized data from remote while a synchronization is running.")
            }

            val remote = RemotePadImpl(syncParams!!.url, syncParams!!.key)

            val signal = CompletableDeferred<SyncError?>()
            syncDoneSignal = signal

            doEnqueueDbOperation {
                var exception: SyncError? = null
                try {
                    if (!keepOnRemote) {
                        remote.delete()
                    }
                    syncParams = null
                    db.unSetupPadSync(id)
                } catch (e: SyncError) {
                    exception = e
                    Log.e("sync", "sync data deletion failed", e)
                } catch (e: Throwable) {
                    exception = SyncError.newUnexpectedError("Unexpected exception.", e)
                    Log.e("sync", "sync data deletion failed", e)
                } finally {
                    syncDoneSignal = null
                    signal.complete(exception)
                }
            }
        }

        suspend fun waitForEndOfSync(): SyncError? = syncDoneSignal?.await()

        fun enqueueDbOperation(op: suspend () -> Unit) {
            checkNotDeleting()
            if (isSynchronizing) {
                throw Exception("Cannot enqueue new operations for a pad which is synchronizing.")
            }
            doEnqueueDbOperation(op)
        }

        fun delete() {

            // TODO(perf): cancel pending operations

            isDeleting = true

            synchronized(padManagers) {
                padManagers.remove(id)
                pads = pads.filter { it.id != id }
            }

            doEnqueueDbOperation {
                db.deletePad(id)
            }
        }

        fun rename(newName: String) {
            enqueueDbOperation { db.renamePad(id, newName) }
            name = newName
            synchronized(padManagers) {
                pads = pads.map {
                    if (it.id == id) {
                        it.copy(name = newName)
                    } else {
                        it
                    }
                }
            }
        }

        private fun doEnqueueDbOperation(op: suspend () -> Unit) {
            operationsQueue.trySend(op).exceptionOrNull()?.let { throw it }
        }

        private fun checkNotDeleting() {
            if (isDeleting) {
                throw RuntimeException("The pad is being deleted.")
            }
        }
    }

    companion object {

        suspend fun load(db: AppRepository, coroutineScope: CoroutineScope): PadsManager {

            // TODO(perf): use a single db request

            val repo = PadsManager(
                db = db,
                coroutineScope = coroutineScope,
            )

            repo.pads = db.getPads()

            for (pad in repo.pads) {
                val syncParams = db.getPadSyncParams(pad.id)
                repo.padManagers[pad.id] = repo.PadManager(
                    id = pad.id,
                    db = db,
                    name = pad.name,
                    syncParams = syncParams,
                    coroutineScope = coroutineScope,
                )
            }

            return repo
        }
    }

    fun createPad(
        name: String,
        syncParams: PadSyncParams? = null,
    ): String = synchronized(padManagers) {

        val pad = PadManager(
            id = UUID.randomUUID().toString(),
            db = db,
            name = name,
            syncParams = syncParams,
            coroutineScope = coroutineScope,
        )

        val newList = pads.toMutableList()
        newList.add(
            Pad(
                id = pad.id,
                name = pad.name,
            )
        )
        pads = newList

        padManagers[pad.id] = pad

        pad.enqueueDbOperation {
            db.addPad(id = pad.id, name = name, syncParams = syncParams)
        }

        pad.id
    }

    fun openPad(padId: String): PadHandle = synchronized(padManagers) {
        val pad = padManagers[padId]!!
        if (pad.held) {
            throw Exception("Pad already locked.")
        }
        pad.held = true
        PadHandleImpl(pad)
    }
}
