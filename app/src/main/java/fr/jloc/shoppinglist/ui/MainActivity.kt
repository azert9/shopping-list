package fr.jloc.shoppinglist.ui

import android.os.Bundle
import android.provider.DocumentsContract
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.NavDisplay
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.AndroidApp
import fr.jloc.shoppinglist.ui.theme.ShoppingListTheme
import kotlinx.coroutines.launch
import kotlin.system.exitProcess
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import fr.jloc.shoppinglist.business.App
import fr.jloc.shoppinglist.business.Pad
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.ui.screens.about.AboutScreen
import fr.jloc.shoppinglist.ui.screens.add_remote_pad.AddRemotePadScreen
import fr.jloc.shoppinglist.ui.screens.pad.PadScreen
import fr.jloc.shoppinglist.ui.screens.share_pad.SharePadScreen
import kotlinx.serialization.json.Json
import kotlinx.serialization.serializer

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        val loadingApp = (application as AndroidApp).app

        // the system splash screen will stay displayed until our application is loaded

        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition {
            !loadingApp.isCompleted
        }

        // decode intent uri if we have one

        var sharingURI: SharingURI? = null
        intent.data?.let { intentURI ->
            try {
                sharingURI = SharingURI.decode(intentURI.toString())
            } catch (e: IllegalArgumentException) {
                Log.e(null, "invalid intent uri: ${Json.encodeToString(intentURI.toString())}", e)
            }
        }

        // creating the view

        enableEdgeToEdge()

        setContent {

            var app: App? by remember { mutableStateOf(null) }

            LaunchedEffect(Unit) {
                launch {
                    app = loadingApp.await()
                }
            }

            ShoppingListTheme {
                if (app != null) {
                    RootComponent(
                        app = app!!,
                        sharingURI = sharingURI,
                    )
                }
            }
        }
    }
}

