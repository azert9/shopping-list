package fr.jloc.shoppinglist.ui

import android.content.res.Resources
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.platform.LocalResources
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.business.sync.SyncError

fun syncErrorMessage(error: SyncError, res: Resources): String = run {
    val stringId = when (error.code) {
        SyncError.Code.SERVER_ERROR -> R.string.error_server
        SyncError.Code.NETWORK_ERROR -> R.string.error_network
        SyncError.Code.APP_NEEDS_UPDATE -> R.string.error_internal
        SyncError.Code.UNEXPECTED_ERROR -> R.string.error_internal
    }
    res.getString(stringId)
}

@Composable
@ReadOnlyComposable
fun syncErrorMessage(error: SyncError): String = syncErrorMessage(error, LocalResources.current)
