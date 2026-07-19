package fr.jloc.shoppinglist.business

import fr.jloc.shoppinglist.business.sync.PadKey

data class Pad(
    val id: String,
    val name: String,
)

data class PadSyncParams(
    val url: String,
    val key: PadKey,
)

data class PadItem(
    val checked: Boolean,
    val name: String,
    val note: String,
) {
    val content
        get(): PadItemContent = PadItemContent(
            checked = checked,
            note = note,
        )
}

data class PadItemContent(
    val checked: Boolean,
    val note: String,
)

interface AppRepository {
    suspend fun getPads(): List<Pad>

    suspend fun addPad(id: String, name: String, syncParams: PadSyncParams? = null): Pad

    suspend fun deletePad(padId: String)

    suspend fun renamePad(padId: String, name: String)

    suspend fun setupPadSync(padId: String, syncParams: PadSyncParams)

    suspend fun unSetupPadSync(padId: String)

    suspend fun getPadSyncParams(padId: String): PadSyncParams?

    suspend fun getPadItems(padId: String): List<PadItem>

    suspend fun addPadItem(padId: String, name: String, note: String): PadItem

    suspend fun updatePadItem(
        padId: String,
        oldName: String,
        name: String? = null,
        note: String? = null,
        checked: Boolean? = null,
    )

    suspend fun setPadItemChecked(padId: String, name: String, checked: Boolean)

    suspend fun deletePadItem(padId: String, name: String)

    // synchronization

    suspend fun getPadSynchronizedTag(padId: String): String?

    suspend fun getPadSynchronizedVersion(padId: String): Map<String, PadItemContent>

    /**
     * Perform a three-way sync, returning the changes that should be applied to the remote version.
     * @return The new local version.
     **/
    suspend fun synchronizePad(
        padId: String,
        remoteVersion: Map<String, PadItemContent>,
    ): Map<String, PadItemContent>

    suspend fun commitPadSynchronization(
        padId: String,
        syncVersion: Map<String, PadItemContent>,
        tag: String,
    )
}
