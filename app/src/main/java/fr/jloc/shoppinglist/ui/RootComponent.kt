package fr.jloc.shoppinglist.ui

import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSerializable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.entryProvider
import androidx.navigation3.ui.LocalNavAnimatedContentScope
import androidx.navigation3.ui.NavDisplay
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.business.App
import fr.jloc.shoppinglist.business.Pad
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.ui.screens.about.AboutScreen
import fr.jloc.shoppinglist.ui.screens.add_remote_pad.AddRemotePadScreen
import fr.jloc.shoppinglist.ui.screens.pad.PadScreen
import fr.jloc.shoppinglist.ui.screens.share_pad.SharePadScreen
import kotlinx.coroutines.launch
import kotlinx.serialization.serializer
import kotlin.collections.plus
import kotlin.system.exitProcess

@Composable
fun RootComponent(app: App, sharingURI: SharingURI?) {
    SharedTransitionLayout {
        RootComponentInner(
            app = app,
            sharingURI = sharingURI,
            sharedTransitionScope = this,
        )
    }
}

@Composable
private fun RootComponentInner(
    app: App,
    sharingURI: SharingURI?,
    sharedTransitionScope: SharedTransitionScope,
) {
    // back stack

    val backStack = rememberSerializable(serializer = serializer()) {
        val initialRoute = sharingURI?.let {
            Route.AddRemotePad(uri = sharingURI.toString())
        } ?: run {
            Route.Pad(selectedPadId = app.lastOpenedPadId)
        }
        NavBackStack(initialRoute)
    }

    fun popBackStack() {
        backStack.removeLastOrNull()
        if (backStack.isEmpty()) {
            backStack.add(Route.Pad(selectedPadId = null))
        }
    }

    //

    var pads by remember { mutableStateOf(app.pads.pads) }
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val coroutineScope = rememberCoroutineScope()
    var creatingPad by remember { mutableStateOf(false) }
    var conditionsAccepted by remember { mutableStateOf(app.conditionsAccepted) }

    val navEntryProvider = entryProvider {

        entry<Route.About> {
            AboutScreen(onDismiss = { popBackStack() })
        }

        entry<Route.Pad> { route ->

            val pad = app.pads.pads.find { it.id == route.selectedPadId }

            if (pad != null) {
                PadScreen(
                    pad = pad,
                    padsManager = app.pads,
                    sharedTransitionScope = sharedTransitionScope,
                    animatedVisibilityScope = LocalNavAnimatedContentScope.current,
                    onDrawerButtonClicked = {
                        if (drawerState.isOpen) {
                            coroutineScope.launch { drawerState.close() }
                        } else {
                            coroutineScope.launch { drawerState.open() }
                        }
                    },
                    onPadRenamed = { name ->
                        pads = pads.map { if (it.id == pad.id) it.copy(name = name) else it }
                    },
                    onPadDeleted = {
                        pads = pads.filter { it.id != pad.id }
                        Route.Pad(selectedPadId = null).navigate(backStack)
                    },
                    onSharePad = { uri ->
                        Route.SharePad(uri.toString()).navigate(backStack)
                    },
                )

            }
        }

        entry<Route.SharePad> { route ->
            SharePadScreen(
                route.uri,
                onDismiss = { popBackStack() },
            )
        }

        entry<Route.AddRemotePad> { route ->
            AddRemotePadScreen(
                route.uri,
                padsManager = app.pads,
                onContinue = { pad ->
                    popBackStack()
                    if (pad != null) {
                        Route.Pad(selectedPadId = pad.id).navigate(backStack)
                    }
                },
            )
        }
    }

    ModalNavigationDrawer(
        modifier = Modifier.testTag("drawer"),
        drawerState = drawerState,
        gesturesEnabled = backStack.lastOrNull()?.drawerEnabled ?: false,
        drawerContent = {
            DrawerContent(
                pads = pads,
                selectedPadId = backStack.lastOrNull().let {
                    if (it is Route.Pad) it.selectedPadId else null
                },
                drawerState = drawerState,
                onPadSelected = { selected ->
                    Route.Pad(selectedPadId = selected.id).navigate(backStack)
                },
                onAddPadClicked = {
                    creatingPad = true
                },
                onAboutClicked = {
                    Route.About.navigate(backStack)
                },
            )
        },
    ) {
        NavDisplay(
            backStack = backStack,
            entryProvider = navEntryProvider,
            onBack = { popBackStack() },
        )
    }

    // fallback when no pad is selected

    backStack.lastOrNull()?.let { route ->
        if (route is Route.Pad && route.selectedPadId == null) {
            val firstPad = pads.firstOrNull()
            if (firstPad != null) {
                // selecting the first pad in the list
                Route.Pad(firstPad.id).navigate(backStack)
            } else {
                // no pad available, force user to create one
                EditPadDialog(
                    title = stringResource(R.string.create_pad_dialog_title),
                    initialName = "",
                    submitButtonText = stringResource(R.string.dialog_submit_create),
                    canCancel = false,
                    onSubmit = { name ->
                        val padId = app.pads.createPad(name)
                        pads += Pad(name = name, id = padId)
                        Route.Pad(selectedPadId = padId).navigate(backStack)
                    },
                    onCancel = { assert(false) },
                )
            }
        }
    }

    // saving last opened pad id

    backStack.lastOrNull()?.let { route ->
        if (route is Route.Pad && route.selectedPadId != null) {
            app.setLastOpenedPadId(route.selectedPadId)
        }
    }

    // pad creation dialog

    if (creatingPad) {
        EditPadDialog(
            title = stringResource(R.string.create_pad_dialog_title),
            initialName = "",
            submitButtonText = stringResource(R.string.dialog_submit_create),
            canCancel = true,
            onSubmit = { name ->
                creatingPad = false
                val padId = app.pads.createPad(name)
                pads += Pad(name = name, id = padId)
                Route.Pad(selectedPadId = padId).navigate(backStack)
            },
            onCancel = { creatingPad = false },
        )
    }

    // consent dialog

    if (!conditionsAccepted) {
        ConsentDialog(
            onRefuse = {
                exitProcess(0)
            },
            onAccept = {
                app.setConditionsAccepted()
                conditionsAccepted = true
            },
        )
    }
}
