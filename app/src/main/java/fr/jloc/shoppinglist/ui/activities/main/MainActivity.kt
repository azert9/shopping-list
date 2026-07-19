package fr.jloc.shoppinglist.ui.activities.main

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DrawerState
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.ShoppingListApp
import fr.jloc.shoppinglist.business.sync.SyncError
import fr.jloc.shoppinglist.ui.activities.about.AboutActivity
import fr.jloc.shoppinglist.ui.components.CommonDialog
import fr.jloc.shoppinglist.ui.components.TextFieldWithSuggestions
import fr.jloc.shoppinglist.ui.syncErrorMessage
import fr.jloc.shoppinglist.ui.theme.ShoppingListTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import qrcode.QRCode
import kotlin.system.exitProcess

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {

            val coroutineContext = rememberCoroutineScope()
            var acceptedConditions: Boolean? by remember { mutableStateOf(null) }

            LaunchedEffect(Unit) {
                withContext(Dispatchers.IO) {
                    acceptedConditions = (application as ShoppingListApp).getConditionsAccepted()
                }
            }

            if (acceptedConditions == true) {
                Screen()
            } else if (acceptedConditions == false) {
                ConditionsScreen(
                    onRefuse = {
                        exitProcess(0)
                    },
                    onAccept = {
                        coroutineContext.launch(Dispatchers.IO) {
                            (application as ShoppingListApp).setConditionsAccepted()
                        }
                        acceptedConditions = true
                    },
                )
            }
        }
    }
}

@Composable
fun ConditionsScreen(
    onAccept: (() -> Unit)? = null,
    onRefuse: (() -> Unit)? = null,
) {
    ShoppingListTheme {
        AlertDialog(
            text = {
                Column {
                    Text(text = stringResource(R.string.welcome_message_1))
                    val html = stringResource(R.string.welcome_message_2).format(
                        "<a href=\"${stringResource(R.string.privacy_policy_url)}\">${
                            stringResource(
                                R.string.privacy_policy
                            )
                        }</a>",
                        "<a href=\"${stringResource(R.string.terms_of_use_url)}\">${
                            stringResource(
                                R.string.terms_of_use
                            )
                        }</a>",
                    )
                    Text(
                        text = AnnotatedString.fromHtml(html),
                        modifier = Modifier.padding(top = 16.dp),
                    )
                }
            },
            onDismissRequest = { onRefuse?.invoke() },
            dismissButton = {
                TextButton(onClick = { onRefuse?.invoke() }) {
                    Text(stringResource(R.string.dialog_submit_refuse))
                }
            },
            confirmButton = {
                TextButton(onClick = { onAccept?.invoke() }) {
                    Text(stringResource(R.string.dialog_submit_accept))
                }
            },
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun ScreenPreview() {
    Screen()
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun Screen(viewModel: MainViewModel = viewModel()) {

    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val dialog by viewModel.dialog.collectAsStateWithLifecycle()
    val selectablePads by viewModel.selectablePads.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val syncError by viewModel.syncError.collectAsStateWithLifecycle()
    val items by viewModel.items.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val syncErrorSnackbarMessage = stringResource(R.string.sync_error)
    val syncErrorSnackbarActionLabel = stringResource(R.string.snackbar_action_details)
    LaunchedEffect(syncError) {
        if (syncError != null) {
            coroutineScope.launch {
                val result = snackbarHostState.showSnackbar(
                    syncErrorSnackbarMessage,
                    actionLabel = syncErrorSnackbarActionLabel,
                    duration = SnackbarDuration.Short,
                )
                viewModel.onSyncErrorDismissed(result == SnackbarResult.ActionPerformed)
            }
        }
    }

    val fullScreenDialog = dialog is MainViewModel.Dialog.SharePad

    // TODO(ux): disable the navigation drawer when a full-screen dialog is opened

    ShoppingListTheme {
        NavigationDrawer(
            drawerState,
            selectablePads,
            onPadClicked = { pad ->
                viewModel.onPadSelected(pad.key)
                coroutineScope.launch {
                    drawerState.close()
                }
            },
            onAddPadClicked = {
                viewModel.onPadCreationRequested()
            },
        ) {
            Scaffold(modifier = Modifier.fillMaxSize(), topBar = {
                if (dialog is MainViewModel.Dialog.SharePad) {
                    DialogTopAppBar(
                        title = stringResource(R.string.action_share_pad),
                        dialog = dialog!!,
                    )
                } else if ((items?.selectedCount ?: 0) == 0) {
                    DefaultTopAppBar(viewModel, drawerState)
                } else {
                    SelectionTopAppBar(viewModel)
                }
            }, floatingActionButton = {
                if (!fullScreenDialog && syncStatus != MainViewModel.SyncStatus.RUNNING) {
                    FloatingActionButton(
                        onClick = {
                            viewModel.onItemCreationRequested()
                        },
                    ) {
                        Icon(
                            painterResource(R.drawable.ic_add_big),
                            stringResource(R.string.action_add_pad_item),
                        )
                    }
                }
            }, snackbarHost = {
                SnackbarHost(snackbarHostState)
            }) { innerPadding ->
                Box(modifier = Modifier.fillMaxSize()) {
                    val items = items
                    if (!fullScreenDialog) {
                        PadContent(
                            modifier = Modifier
                                .padding(innerPadding)
                                .fillMaxSize(),
                            uncheckedItems = items?.unchecked ?: listOf(),
                            checkedItems = items?.checked ?: listOf(),
                            selectionMode = items != null && items.selectedCount != 0,
                            readOnly = items == null,
                            onItemCheckedChange = { item, checked ->
                                viewModel.onItemCheckedChanged(item.name, checked)
                            },
                            onItemEditRequested = { item ->
                                viewModel.onItemEditRequested(item)
                            },
                            onItemSelectedChange = { item, selected ->
                                viewModel.onItemSelectedChanged(item.name, selected)
                            },
                        )
                    }
                    when (val dialog = dialog) {
                        null -> {}

                        is MainViewModel.Dialog.AddPad -> {
                            EditPadDialog(
                                title = stringResource(R.string.create_pad_dialog_title),
                                initialName = "",
                                submitButtonText = stringResource(R.string.dialog_submit_create),
                                canCancel = dialog.cancellable,
                                onSubmit = { name ->
                                    dialog.onSubmit(name)
                                },
                                onCancel = {
                                    dialog.onCancel()
                                },
                            )
                        }

                        is MainViewModel.Dialog.DeletePad -> {
                            AlertDialog(
                                title = { Text(stringResource(R.string.delete_pad_dialog_title)) },
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.delete_pad_dialog_body,
                                            dialog.padName,
                                        )
                                    )
                                },
                                onDismissRequest = {
                                    dialog.onCancel()
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            dialog.onSubmit()
                                        },
                                    ) { Text(stringResource(R.string.dialog_submit_delete)) }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            dialog.onCancel()
                                        },
                                    ) { Text(stringResource(R.string.dialog_cancel)) }
                                },
                            )
                        }

                        is MainViewModel.Dialog.RenamePad -> {
                            EditPadDialog(
                                title = stringResource(R.string.rename_pad_dialog_title),
                                initialName = dialog.initialName,
                                submitButtonText = stringResource(R.string.dialog_submit_apply),
                                onSubmit = { newName ->
                                    dialog.onSubmit(newName)
                                },
                                onCancel = {
                                    dialog.onCancel()
                                },
                            )
                        }

                        is MainViewModel.Dialog.SharePad -> {
                            SharePadDialogBody(dialog, modifier = Modifier.padding(innerPadding))
                        }

                        is MainViewModel.Dialog.StopSharingPad -> {
                            StopSharingDialog(
                                padName = dialog.padName,
                                onSubmit = { keepOnRemote -> dialog.onSubmit(keepOnRemote) },
                                onCancel = { dialog.onCancel() },
                            )
                        }

                        is MainViewModel.Dialog.EditItem -> {
                            EditItemDialog(
                                suggestions = if (dialog.updating) {
                                    listOf()
                                } else {
                                    items?.checked?.map { it.name } ?: listOf()
                                },
                                updating = dialog.updating,
                                initialName = dialog.initialName,
                                initialNote = dialog.initialNote,
                                onCancel = {
                                    dialog.onCancel()
                                },
                                onSubmit = { name, note ->
                                    dialog.onSubmit(name, note)
                                },
                                onDelete = {
                                    dialog.onDelete()
                                },
                            )
                        }

                        is MainViewModel.Dialog.ConfirmItemOverwrite -> {
                            AlertDialog(
                                title = { Text(stringResource(R.string.overwrite_pad_item_dialog_title)) },
                                text = {
                                    Text(
                                        stringResource(
                                            R.string.overwrite_pad_item_dialog_body,
                                            dialog.name,
                                        )
                                    )
                                },
                                onDismissRequest = {
                                    dialog.onCancel()
                                },
                                confirmButton = {
                                    TextButton(
                                        onClick = {
                                            dialog.onSubmit()
                                        },
                                    ) { Text(stringResource(R.string.dialog_submit_replace)) }
                                },
                                dismissButton = {
                                    TextButton(
                                        onClick = {
                                            dialog.onCancel()
                                        },
                                    ) { Text(stringResource(R.string.dialog_cancel)) }
                                },
                            )
                        }

                        is MainViewModel.Dialog.SyncErrorDetails -> {
                            SyncErrorDialog(
                                dialog.error,
                                onCancel = { dialog.onCancel() },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DefaultTopAppBar(
    viewModel: MainViewModel,
    drawerState: DrawerState,
) {
    val padName by viewModel.padName.collectAsStateWithLifecycle()
    val syncStatus by viewModel.syncStatus.collectAsStateWithLifecycle()
    val coroutineScope = rememberCoroutineScope()

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = { Text(padName ?: "") },
        navigationIcon = {
            IconButton(onClick = {
                coroutineScope.launch {
                    if (drawerState.isClosed) {
                        drawerState.open()
                    } else {
                        drawerState.close()
                    }
                }
            }) {
                Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.menu))
            }
        },
        actions = {
            var menuExpanded by remember { mutableStateOf(false) }
            when (syncStatus) {
                null, MainViewModel.SyncStatus.NONE -> {}

                MainViewModel.SyncStatus.OK -> {
                    IconButton(onClick = { viewModel.onSyncRequested() }) {
                        Icon(
                            painterResource(R.drawable.ic_sync),
                            stringResource(R.string.action_sync_pad),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                MainViewModel.SyncStatus.RUNNING -> {
                    // TODO(ux): use a spinning sync icon instead?
                    CircularProgressIndicator()
                }

                MainViewModel.SyncStatus.FAILED -> {
                    IconButton(onClick = { viewModel.onSyncRequested() }) {
                        Icon(
                            painterResource(R.drawable.ic_sync_problem),
                            stringResource(R.string.action_retry_sync_pad),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }
            }
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    painterResource(R.drawable.ic_more_vert),
                    stringResource(R.string.pad_options),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false },
            ) {
                if (syncStatus == MainViewModel.SyncStatus.NONE) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.action_delete_pad),
                            )
                        },
                        text = { Text(stringResource(R.string.action_delete_pad)) },
                        onClick = {
                            viewModel.onPadDeletionRequested()
                            menuExpanded = false
                        },
                    )
                } else if (syncStatus != null) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_cloud_off),
                                stringResource(R.string.action_stop_sharing_pad)
                            )
                        },
                        text = { Text(stringResource(R.string.action_stop_sharing_pad)) },
                        onClick = {
                            viewModel.onPadStopSharingRequested()
                            menuExpanded = false
                        },
                        enabled = syncStatus != MainViewModel.SyncStatus.RUNNING,
                    )
                }
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_edit),
                            stringResource(R.string.action_rename_pad)
                        )
                    },
                    text = { Text(stringResource(R.string.action_rename_pad)) },
                    onClick = {
                        viewModel.onPadRenamingRequested()
                        menuExpanded = false
                    },
                )
                DropdownMenuItem(
                    leadingIcon = {
                        Icon(
                            painterResource(R.drawable.ic_share),
                            stringResource(R.string.action_share_pad)
                        )
                    },
                    text = { Text(stringResource(R.string.action_share_pad)) },
                    onClick = {
                        viewModel.onPadSharingRequested()
                        menuExpanded = false
                    },
                )
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SelectionTopAppBar(viewModel: MainViewModel) {
    val items by viewModel.items.collectAsStateWithLifecycle()

    BackHandler { viewModel.onClearSelection() }

    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(
                stringResource(
                    R.string.pad_items_selection_title,
                    items?.selectedCount ?: 0,
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = { viewModel.onClearSelection() }) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    stringResource(R.string.navigate_back),
                )
            }
        },
        actions = {
            IconButton(onClick = {
                viewModel.onDeleteSelection()
            }) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.action_delete_selected_pad_items),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = {
                viewModel.onSelectAll()
            }) {
                Icon(
                    painterResource(R.drawable.ic_select_all),
                    stringResource(R.string.action_select_all),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DialogTopAppBar(
    title: String,
    dialog: MainViewModel.Dialog,
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { dialog.onCancel() }) {
                Icon(painterResource(R.drawable.ic_close), stringResource(R.string.menu))
            }
        },
    )
}

@Preview(showBackground = true)
@Composable
private fun NavigationDrawerPreview() {
    NavigationDrawer(
        DrawerState(DrawerValue.Open),
        listOf(
            MainViewModel.SelectablePad(key = Unit, name = "Groceries", selected = true),
            MainViewModel.SelectablePad(key = Unit, name = "Mall", selected = false),
        ),
    ) { }
}

@Composable
private fun NavigationDrawer(
    drawerState: DrawerState,
    selectablePads: List<MainViewModel.SelectablePad>,
    onPadClicked: ((MainViewModel.SelectablePad) -> Unit)? = null,
    onAddPadClicked: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    val horizontalMargin = 10.dp
    val verticalMargin = 8.dp
    val context = LocalContext.current
    ModalNavigationDrawer(
        modifier = Modifier.testTag("drawer"),
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerState = drawerState) {
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontalMargin, verticalMargin)
                        .verticalScroll(rememberScrollState()),
                ) {
                    for (pad in selectablePads) {
                        NavigationDrawerItem(
                            label = { Text(pad.name) },
                            selected = pad.selected,
                            onClick = { onPadClicked?.invoke(pad) },
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
                        onClick = { onAddPadClicked?.invoke() },
                    )
                    NavigationDrawerItem(
                        label = { Text(stringResource(R.string.action_about)) },
                        icon = {
                            Icon(
                                painterResource(R.drawable.ic_help),
                                stringResource(R.string.action_about)
                            )
                        },
                        selected = false,
                        onClick = {
                            val intent = Intent(
                                context,
                                AboutActivity::class.java,
                            )
                            context.startActivity(intent)
                        },
                    )
                }
            }
        },
    ) {
        content()
    }
}

@Stable
@Composable
private fun PadContent(
    modifier: Modifier = Modifier,
    uncheckedItems: List<MainViewModel.Item>,
    checkedItems: List<MainViewModel.Item>,
    selectionMode: Boolean,
    readOnly: Boolean,
    onItemCheckedChange: (MainViewModel.Item, Boolean) -> Unit,
    onItemEditRequested: (MainViewModel.Item) -> Unit,
    onItemSelectedChange: (MainViewModel.Item, Boolean) -> Unit,
) {
    val lazyColumnState = rememberLazyListState()

    Box(modifier) {

        if (uncheckedItems.isEmpty() && checkedItems.isEmpty()) {
            // TODO(ux): hide while loading
            Text(
                stringResource(R.string.empty_pad_placeholder),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f),
                modifier = Modifier.align(Alignment.Center),
            )
            return
        }

        LazyColumn(
            state = lazyColumnState,
            modifier = Modifier.fillMaxSize(),
        ) {
            items(
                count = uncheckedItems.size,
                key = { index -> uncheckedItems[index].name },
            ) { index ->
                val item = uncheckedItems[index]
                PadItem(
                    item,
                    modifier = Modifier.animateItem(),
                    readOnly = selectionMode || readOnly,
                    onClick = {
                        if (readOnly) {
                            // noop
                        } else if (selectionMode) {
                            onItemSelectedChange(item, !item.selected)
                        } else {
                            onItemEditRequested(item)
                        }
                    },
                    onLongClick = { if (!readOnly) onItemSelectedChange(item, !item.selected) },
                    onCheckedChange = { checked -> onItemCheckedChange(item, checked) })
            }
            if (checkedItems.isNotEmpty()) {
                item {
                    if (uncheckedItems.isNotEmpty()) {
                        HorizontalDivider(Modifier.padding(top = 32.dp))
                    }
                    Text(
                        stringResource(R.string.bought_previously_title),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                items(
                    count = checkedItems.size,
                    key = { index -> checkedItems[index].name },
                ) { index ->
                    val item = checkedItems[index]
                    PadItem(
                        item,
                        modifier = Modifier.animateItem(),
                        readOnly = selectionMode || readOnly,
                        onClick = {
                            if (readOnly) {
                                // noop
                            } else if (selectionMode) {
                                onItemSelectedChange(item, !item.selected)
                            } else {
                                onItemEditRequested(item)
                            }
                        },
                        onLongClick = { if (!readOnly) onItemSelectedChange(item, !item.selected) },
                        onCheckedChange = { checked -> onItemCheckedChange(item, checked) },
                    )
                }
            }
            // spacing to avoid the last item being obscured by the FAB
            item {
                Spacer(modifier = Modifier.height(80.dp))
            }
        }
    }
}

@Composable
@Preview
private fun PadItemPreview() {
    PadItem(
        MainViewModel.Item(
            checked = false,
            name = "Potatoes",
            note = "2Kg",
            selected = false,
        ),
        onClick = {},
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun PadItem(
    item: MainViewModel.Item,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    onCheckedChange: ((value: Boolean) -> Unit)? = null,
) {
    ListItem(
        modifier = modifier,
        selected = item.selected,
        onClick = onClick,
        onLongClick = onLongClick,
        trailingContent = {
            Row {
                Text(
                    item.note.lineSequence().first(),
                    modifier = Modifier
                        .align(Alignment.CenterVertically)
                        .padding(end = 16.dp),
                    softWrap = false,
                )
                Checkbox(
                    item.checked,
                    enabled = !readOnly,
                    onCheckedChange = onCheckedChange,
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
        },
    ) {
        Text(item.name)
    }
}

@Preview(showBackground = true)
@Composable
private fun EditPadDialogPreview() {
    EditPadDialog("Rename shopping list", "Groceries", "Apply")
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun EditPadDialog(
    title: String,
    initialName: String,
    submitButtonText: String,
    canCancel: Boolean = true,
    onCancel: (() -> Unit)? = null,
    onSubmit: ((String) -> Unit)? = null,
) {
    val nameTextFieldState = rememberTextFieldState(initialText = initialName)
    val nameTextFieldOk = nameTextFieldState.text.isNotBlank()
    var nameTextFieldEdited by remember { mutableStateOf(false) }
    if (nameTextFieldState.text != initialName) {
        nameTextFieldEdited = true
    }

    CommonDialog(
        title, onCancel = onCancel, canCancel, submitAction = {
            Button(
                onClick = {
                    onSubmit?.invoke(
                        nameTextFieldState.text.toString(),
                    )
                },
                enabled = nameTextFieldOk,
            ) {
                Text(submitButtonText)
            }
        }) {
        OutlinedTextField(
            state = nameTextFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.pad_name_input_label)) },
            lineLimits = TextFieldLineLimits.SingleLine,
            isError = nameTextFieldEdited && !nameTextFieldOk,
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun StopSharingDialogPreview() {
    StopSharingDialog(padName = "My Shopping List")
}

@Composable
private fun StopSharingDialog(
    padName: String,
    onCancel: (() -> Unit)? = null,
    onSubmit: ((keepOnRemote: Boolean) -> Unit)? = null,
) {
    var keepOnRemoteChecked by remember { mutableStateOf(false) }

    CommonDialog(
        onCancel = onCancel,
        submitAction = {
            Button(onClick = { onSubmit?.invoke(keepOnRemoteChecked) }) {
                Text(stringResource(R.string.dialog_submit_apply))
            }
        },
    ) {
        Text(stringResource(R.string.stop_sharing_pad_dialog_body).format(padName))
        Row(
            modifier = Modifier.padding(top = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Spacer(Modifier.weight(1F))
            Text(
                stringResource(R.string.keep_on_server_checkbox_label),
            )
            Checkbox(
                checked = keepOnRemoteChecked,
                onCheckedChange = { keepOnRemoteChecked = it },
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun EditItemDialogPreview() {
    EditItemDialog(
        suggestions = listOf(),
        false,
        initialName = "",
        initialNote = "",
    )
}

@Composable
private fun EditItemDialog(
    suggestions: List<String>,
    updating: Boolean,
    initialName: String,
    initialNote: String,
    onCancel: (() -> Unit)? = null,
    onSubmit: ((name: String, note: String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val nameTextFieldState = rememberTextFieldState()
    val nameTextFieldValue = nameTextFieldState.text.toString()
    val nameTextFieldFocusRequester = remember { FocusRequester() }
    val nameTextFieldOk = nameTextFieldState.text.isNotBlank()
    var nameTextFieldLifecycle by remember { mutableIntStateOf(0) }

    val noteTextFieldState = rememberTextFieldState()
    val noteTextFieldFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        nameTextFieldState.setTextAndPlaceCursorAtEnd(initialName)
        noteTextFieldState.setTextAndPlaceCursorAtEnd(initialNote)
        if (initialName.isEmpty()) {
            nameTextFieldFocusRequester.requestFocus()
        } else {
            noteTextFieldFocusRequester.requestFocus()
        }
    }

    CommonDialog(
        onCancel = onCancel,
        submitAction = {
            Button(
                onClick = {
                    onSubmit?.invoke(nameTextFieldValue, noteTextFieldState.text.toString())
                },
                enabled = nameTextFieldOk,
            ) {
                Text(
                    stringResource(
                        if (updating) {
                            R.string.dialog_submit_apply
                        } else {
                            R.string.dialog_submit_add
                        }
                    )
                )
            }
        },
        tertiaryAction = if (updating) {
            {
                TextButton(
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    onClick = { onDelete?.invoke() },
                ) {
                    Text(stringResource(R.string.dialog_submit_delete))
                }
            }
        } else {
            null
        },
    ) {
        TextFieldWithSuggestions(
            suggestions = suggestions,
            state = nameTextFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .focusRequester(nameTextFieldFocusRequester)
                .onFocusChanged { state ->
                    if ((state.isFocused && nameTextFieldLifecycle == 0) || (!state.isFocused && nameTextFieldLifecycle == 1)) {
                        nameTextFieldLifecycle++
                    }
                },
            label = { Text(stringResource(R.string.pad_item_name_input_label)) },
            isError = !nameTextFieldOk && nameTextFieldLifecycle == 2,
            onSubmit = {
                noteTextFieldFocusRequester.requestFocus()
            },
        )

        OutlinedTextField(
            state = noteTextFieldState,
            modifier = Modifier
                .fillMaxWidth()
                .focusRequester(noteTextFieldFocusRequester),
            label = { Text(stringResource(R.string.pad_item_note_input_label)) },
            lineLimits = TextFieldLineLimits.MultiLine(maxHeightInLines = 10),
        )
    }
}

@Composable
private fun SharePadDialogBody(
    dialog: MainViewModel.Dialog.SharePad,
    modifier: Modifier = Modifier,
) {
    val png = QRCode.ofSquares().withInnerSpacing(0)
        .withColor(MaterialTheme.colorScheme.onBackground.toArgb()).build(dialog.uri)
        .renderToBytes()

    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size).asImageBitmap()

    Box(Modifier.fillMaxSize() then modifier) {
        Image(
            modifier = Modifier
                .widthIn(0.dp, 400.dp)
                .heightIn(0.dp, 400.dp)
                .align(Alignment.Center)
                .padding(64.dp),
            painter = BitmapPainter(bitmap),
            contentDescription = stringResource(R.string.qr_code),
        )
    }
}

@Composable
private fun SyncErrorDialog(
    error: SyncError,
    onCancel: (() -> Unit)? = null,
) {
    AlertDialog(
        title = { Text(stringResource(R.string.sync_error)) },
        text = { Text(syncErrorMessage(error)) },
        onDismissRequest = { onCancel?.invoke() },
        confirmButton = {
            TextButton(
                onClick = {
                    onCancel?.invoke()
                },
            ) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}
