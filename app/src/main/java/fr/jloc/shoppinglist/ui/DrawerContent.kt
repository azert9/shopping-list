package fr.jloc.shoppinglist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DrawerState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.business.Pad
import kotlinx.coroutines.launch

@Composable
fun DrawerContent(
    pads: List<Pad>,
    selectedPadId: String?,
    drawerState: DrawerState,
    modifier: Modifier = Modifier,
    onPadSelected: ((pad: Pad) -> Unit)? = null,
    onAddPadClicked: (() -> Unit)? = null,
    onAboutClicked: (() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()
    val horizontalMargin = 10.dp
    val verticalMargin = 8.dp
    ModalDrawerSheet(drawerState = drawerState, modifier = modifier) {
        Column(
            Modifier
                .weight(1f)
                .padding(horizontalMargin, verticalMargin)
                .verticalScroll(rememberScrollState()),
        ) {
            for (pad in pads) {
                NavigationDrawerItem(
                    label = { Text(pad.name) },
                    selected = pad.id == selectedPadId,
                    onClick = {
                        coroutineScope.launch { drawerState.close() }
                        onPadSelected?.invoke(pad)
                    },
                )
            }
        }
        HorizontalDivider()
        Column(
            Modifier.padding(horizontalMargin, verticalMargin)
        ) {
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_create_pad)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_add),
                        stringResource(R.string.action_create_pad)
                    )
                },
                selected = false,
                onClick = {
                    coroutineScope.launch { drawerState.close() }
                    onAddPadClicked?.invoke()
                },
            )
            NavigationDrawerItem(
                label = { Text(stringResource(R.string.action_about)) },
                icon = {
                    Icon(
                        painterResource(R.drawable.ic_help), stringResource(R.string.action_about)
                    )
                },
                selected = false,
                onClick = {
                    coroutineScope.launch { drawerState.close() }
                    onAboutClicked?.invoke()
                },
            )
        }
    }
}
