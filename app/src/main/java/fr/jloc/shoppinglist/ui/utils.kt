package fr.jloc.shoppinglist.ui

import android.content.res.Resources
import androidx.annotation.MainThread
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.saveable.rememberSaveable
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

private var isFirstCompositionSerial: Long = 0;

/** `LaunchedEffect(Unit) { ... }` has the disadvantage of being called again after activity recreation.
 * This function leverages `rememberSaveable()` to work around this limitation. */
@Composable
@MainThread
fun isFirstComposition(): Boolean {
    val a = isFirstCompositionSerial++
    val b = rememberSaveable { a }
    return a == b
}
