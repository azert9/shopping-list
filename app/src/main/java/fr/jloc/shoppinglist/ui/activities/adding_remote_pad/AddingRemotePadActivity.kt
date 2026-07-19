package fr.jloc.shoppinglist.ui.activities.adding_remote_pad

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import fr.jloc.shoppinglist.ShoppingListApp
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.business.sync.SyncError
import fr.jloc.shoppinglist.ui.activities.main.MainActivity
import fr.jloc.shoppinglist.ui.syncErrorMessage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class AddingRemotePadActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Screen(
                intentURI = intent.data!!,
                app = application as ShoppingListApp,
                onContinue = {
                    val intent = Intent(
                        this,
                        MainActivity::class.java,
                    )
                    startActivity(intent)
                },
            )
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Screen(
    intentURI: Uri,
    app: ShoppingListApp,
    onContinue: () -> Unit,
) {

    var error by remember { mutableStateOf<String?>(null) }
    val res = LocalResources.current

    LaunchedEffect(intentURI) {

        if (error != null) {
            return@LaunchedEffect
        }

        withContext(Dispatchers.IO) {

            val uri = try {
                SharingURI.decode(intentURI.toString())
            } catch (_: IllegalArgumentException) {
                error = res.getString(R.string.error_invalid_sharing_uri)
                return@withContext
            }

            try {
                val padId = app.padsManager().createPad(uri.padName, uri.syncParams)

                app.padsManager().startPadSync(padId)

                app.setLastSelectedPad(padId)

                this.launch(Dispatchers.Main) {
                    onContinue()
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
        ErrorScreen(errorCached, onDismiss = { onContinue() })
    } else {
        Box(modifier = Modifier.fillMaxSize()) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        }
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ErrorScreenPreview() {
    ErrorScreen("Something went wrong!\nPlease try again later.")
}

@Composable
private fun ErrorScreen(
    message: String,
    onDismiss: (() -> Unit)? = null,
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
            onClick = { onDismiss?.invoke() },
            Modifier
                .align(Alignment.BottomEnd)
                .padding(42.dp),
        ) {
            Text(stringResource(R.string.continue_))
        }
    }
}
