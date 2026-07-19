package fr.jloc.shoppinglist.business.sync

import fr.jloc.shoppinglist.business.PadItemContent
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

interface RemotePad {

    data class ItemsAndTag(val items: Map<String, PadItemContent>, val tag: String)

    class ConcurrentChangeException : Exception("The remote store was updated concurrently.")

    /**
     * @return Null if the remote version still matches the cached tag. In particular, the
     * return value will be null if the pad does not exist on the remote and the
     * provided cachedTag is null.
     * **/
    suspend fun get(cachedTag: String?): ItemsAndTag?

    /**
     * Creates or update the remote pad.
     * @return The new tag to be cached.
     * @exception ConcurrentChangeException The remote store was updated concurrently.
     * **/
    suspend fun put(items: Map<String, PadItemContent>, baseTag: String?): String

    /** Delete the remote pad.
     */
    suspend fun delete()
}

class FakeRemotePad : RemotePad {

    private val mutex = Mutex()
    private var stored: RemotePad.ItemsAndTag? = null

    override suspend fun get(
        cachedTag: String?
    ): RemotePad.ItemsAndTag? = mutex.withLock {
        if (stored == null) return null
        if (cachedTag != null && stored!!.tag == cachedTag) return null
        stored
    }

    override suspend fun put(
        items: Map<String, PadItemContent>,
        baseTag: String?,
    ): String = mutex.withLock {
        if (stored?.tag != baseTag) throw RemotePad.ConcurrentChangeException()
        val newTag = items.hashCode().toString()
        stored = RemotePad.ItemsAndTag(items, newTag)
        newTag
    }

    override suspend fun delete() = mutex.withLock {
        stored = null
    }

    suspend fun putForTesting(items: Map<String, PadItemContent>) = mutex.withLock {
        val newTag = items.hashCode().toString()
        stored = RemotePad.ItemsAndTag(items, newTag)
    }
}
