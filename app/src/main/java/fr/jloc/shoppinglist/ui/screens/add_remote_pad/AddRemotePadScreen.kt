package fr.jloc.shoppinglist.ui.screens.add_remote_pad

import android.util.Log
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.business.Pad
import fr.jloc.shoppinglist.business.PadsManager
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.business.sync.SyncError
import fr.jloc.shoppinglist.ui.syncErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun AddRemotePadScreen(
    sharingURI: String,
    padsManager: PadsManager,
    onContinue: ((pad: Pad?) -> Unit),
) {

    var error by remember { mutableStateOf<String?>(null) }
    val res = LocalResources.current

    LaunchedEffect(error, sharingURI) {

        if (error != null) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {

            val uri = try {
                SharingURI.decode(sharingURI)
            } catch (_: IllegalArgumentException) {
                error = res.getString(R.string.error_invalid_sharing_uri)
                return@withContext
            }

            // TODO(ux): don't add the pad if we already have it

            try {
                val padId = padsManager.createPad(uri.padName, uri.syncParams)

                padsManager.openPad(padId).use { pad ->
                    pad.startSync()
                    pad.waitEndOfSync()?.let { throw it }
                }

                this.launch(Dispatchers.Main) {
                    onContinue(Pad(id = padId, name = uri.padName))
                }
            } catch (e: SyncError) {
                Log.e(null, "failed to add remote pad", e)
                error = syncErrorMessage(e, res)
            } catch (e: Exception) {
                Log.e(null, "failed to add remote pad", e)
                error = res.getString(R.string.error_failed_to_add_remote_pad)
            }
        }
    }

    val errorCached = error
    if (errorCached != null) {
        ErrorScreen(errorCached, onDismiss = { onContinue(null) })
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ErrorScreenPreview() {
    ErrorScreen(
        "Something went wrong!\nPlease try again later.",
        onDismiss = {},
    )
}

@Composable
private fun ErrorScreen(
    message: String,
    onDismiss: (() -> Unit),
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.align(Alignment.Center)) {
            Icon(
                painter = painterResource(R.drawable.ic_error),
                contentDescription = message,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .size(32.dp),
            )
            Spacer(Modifier.height(32.dp))
            Text(
                text = message,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLargeEmphasized
            )
        }
        Button(
            onClick = onDismiss,
            Modifier
                .align(Alignment.BottomEnd)
                .padding(42.dp),
        ) {
            Text(stringResource(R.string.continue_))
        }
    }
}
