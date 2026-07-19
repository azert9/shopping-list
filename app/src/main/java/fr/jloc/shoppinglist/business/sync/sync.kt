package fr.jloc.shoppinglist.business.sync

import fr.jloc.shoppinglist.business.AppRepository
import fr.jloc.shoppinglist.business.PadItemContent
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds

suspend fun sync(local: AppRepository, remote: RemotePad, padId: String) {

    while (true) {
        try {
            doTrySync(local, remote, padId)
            break
        } catch (_: RemotePad.ConcurrentChangeException) {
            delay(500.milliseconds)
            // retry
        }
    }
}

private suspend fun doTrySync(
    local: AppRepository,
    remote: RemotePad,
    padId: String,
) {

    val oldSyncTag = local.getPadSynchronizedTag(padId)

    val remoteVersionAndTag = remote.get(oldSyncTag) ?: if (oldSyncTag != null) {
        // no change since cached version
        RemotePad.ItemsAndTag(
            local.getPadSynchronizedVersion(padId),
            oldSyncTag,
        )
    } else {
        // this is the first upload
        val syncVersion = local.getPadItems(padId).associate { entry ->
            entry.name to PadItemContent(
                note = entry.note, checked = entry.checked
            )
        }
        val tag = remote.put(syncVersion, null)
        RemotePad.ItemsAndTag(
            syncVersion,
            tag,
        )
    }

    val newSyncVersion = local.synchronizePad(padId, remoteVersionAndTag.items)

    val newSyncTag = if (newSyncVersion == remoteVersionAndTag.items) {
        // no local change, the synchronized version is the one we fetched
        remoteVersionAndTag.tag
    } else {
        // pushing the updated version
        remote.put(newSyncVersion, remoteVersionAndTag.tag)
    }

    if (newSyncTag != oldSyncTag) {
        local.commitPadSynchronization(
            padId,
            newSyncVersion,
            newSyncTag,
        )
    }
}
