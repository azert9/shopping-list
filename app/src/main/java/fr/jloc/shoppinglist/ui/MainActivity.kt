package fr.jloc.shoppinglist.ui

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import fr.jloc.shoppinglist.AndroidApp
import fr.jloc.shoppinglist.ui.theme.ShoppingListTheme
import kotlinx.coroutines.launch
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import fr.jloc.shoppinglist.business.App
import fr.jloc.shoppinglist.business.sync.SharingURI
import kotlinx.serialization.json.Json

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        enableEdgeToEdge()

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

