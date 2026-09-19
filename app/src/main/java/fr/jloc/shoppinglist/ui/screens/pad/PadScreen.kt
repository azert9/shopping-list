package fr.jloc.shoppinglist.ui.screens.pad

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndSelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.business.Pad
import fr.jloc.shoppinglist.business.PadsManager
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.business.sync.SyncError
import fr.jloc.shoppinglist.ui.CommonDialog
import fr.jloc.shoppinglist.ui.EditPadDialog
import fr.jloc.shoppinglist.ui.TextFieldWithSuggestions
import fr.jloc.shoppinglist.ui.isFirstComposition
import fr.jloc.shoppinglist.ui.syncErrorMessage
import kotlinx.coroutines.launch
import kotlin.collections.isNotEmpty

@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun PadScreen(
    padsManager: PadsManager,
    pad: Pad,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    onDrawerButtonClicked: (() -> Unit)? = null,
    onPadRenamed: ((name: String) -> Unit)? = null,
    onPadDeleted: (() -> Unit)? = null,
    onSharePad: ((uri: SharingURI) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    // view model

    val padViewModel = viewModel<PadViewModel>(
        factory = PadViewModel.Factory(
            padsManager,
            pad.id,
        ),
        key = pad.id,
    )
    padViewModel.onPadRenamed = { name -> onPadRenamed?.invoke(name) }
    padViewModel.onPadDeleted = { onPadDeleted?.invoke() }
    padViewModel.onSharePad = { uri -> onSharePad?.invoke(uri) }

    val padName by padViewModel.name.collectAsStateWithLifecycle()
    val items by padViewModel.items.collectAsStateWithLifecycle()
    val syncStatus by padViewModel.syncStatus.collectAsStateWithLifecycle()
    val syncError by padViewModel.syncError.collectAsStateWithLifecycle()

    val selectionMode = (items?.selectedCount ?: 0) != 0
    val readOnly = items == null || syncStatus == PadViewModel.SyncStatus.RUNNING

    // snackbar for showing synchronization errors

    var syncErrorDetails by remember { mutableStateOf<SyncError?>(null) }

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
                if (result == SnackbarResult.ActionPerformed) {
                    syncErrorDetails = syncError
                }
                padViewModel.onSyncErrorDismissed()
            }
        }
    }

    // main content

    Scaffold(
        topBar = {
            val modifier = with(sharedTransitionScope) {
                Modifier.sharedElement(
                    rememberSharedContentState(key = "d7d32e93-8773-408c-8a40-b9d10ed84cc6"),
                    animatedVisibilityScope,
                )
            }
            if (selectionMode) {
                SelectionTopAppBar(
                    modifier = modifier,
                    selectedCount = items?.selectedCount ?: 0,
                    onClearSelection = { padViewModel.clearSelection() },
                    onSelectAll = { padViewModel.selectAll() },
                    onDeleteSelection = { padViewModel.deleteSelection() },
                )
            } else {
                DefaultTopAppBar(
                    modifier = modifier,
                    padName = padName,
                    syncStatus = syncStatus,
                    onDrawerButtonClicked = { onDrawerButtonClicked?.invoke() },
                    onDeletePadClicked = { padViewModel.deletePad() },
                    onStopSharingPadClicked = { padViewModel.stopSharingPad() },
                    onRenamePadClicked = { padViewModel.renamePad() },
                    onSharePadClicked = { padViewModel.sharePad() },
                    onSyncRequested = { padViewModel.startSync() },
                )
            }
        },
        floatingActionButton = {
            if (items != null && !readOnly && !selectionMode) {
                FloatingActionButton(onClick = { padViewModel.addItem() }) {
                    Icon(
                        painterResource(R.drawable.ic_add_big),
                        stringResource(R.string.action_add_pad_item),
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        // show a blank body while loading
        items?.let { items ->
            PadContent(
                Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                uncheckedItems = items.unchecked,
                checkedItems = items.checked,
                selectionMode = selectionMode,
                readOnly = readOnly,
                onItemCheckedChange = { item, checked ->
                    padViewModel.setItemChecked(item, checked)
                },
                onItemEditRequested = { item -> padViewModel.editItem(item) },
                onItemSelectedChange = { item, selected ->
                    padViewModel.setItemSelected(item, selected)
                },
            )
        }
    }

    when (val dialog = padViewModel.dialog.collectAsStateWithLifecycle().value) {

        is PadViewModel.Dialog.EditItem -> {
            EditItemDialog(
                suggestions = dialog.suggestions,
                updating = dialog.updating,
                initialName = dialog.initialName,
                initialNote = dialog.initialNote,
                onCancel = dialog.dismiss,
                onSubmit = dialog.submit,
                onDelete = dialog.delete,
            )
        }

        is PadViewModel.Dialog.ConfirmItemOverwrite -> {
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
                onDismissRequest = dialog.dismiss,
                confirmButton = {
                    TextButton(
                        onClick = {
                            dialog.submit()
                        },
                    ) { Text(stringResource(R.string.dialog_submit_replace)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = {
                            dialog.dismiss()
                        },
                    ) { Text(stringResource(R.string.dialog_cancel)) }
                },
            )
        }

        is PadViewModel.Dialog.RenamePad -> {
            EditPadDialog(
                title = stringResource(R.string.rename_pad_dialog_title),
                initialName = dialog.initialName,
                submitButtonText = stringResource(R.string.dialog_submit_apply),
                canCancel = true,
                onSubmit = dialog.submit,
                onCancel = dialog.dismiss,
            )
        }

        is PadViewModel.Dialog.DeletePad -> {
            AlertDialog(
                title = { Text(stringResource(R.string.delete_pad_dialog_title)) },
                text = {
                    Text(
                        stringResource(
                            R.string.delete_pad_dialog_body,
                            padName,
                        )
                    )
                },
                onDismissRequest = dialog.dismiss,
                confirmButton = {
                    TextButton(
                        onClick = dialog.submit,
                    ) { Text(stringResource(R.string.dialog_submit_delete)) }
                },
                dismissButton = {
                    TextButton(
                        onClick = dialog.dismiss,
                    ) { Text(stringResource(R.string.dialog_cancel)) }
                },
            )
        }

        is PadViewModel.Dialog.StopSharingPad -> {
            StopSharingDialog(
                padName = padName,
                onSubmit = dialog.submit,
                onCancel = dialog.dismiss,
            )
        }

        null -> {}
    }

    // sync error details dialog

    syncErrorDetails?.let {
        SyncErrorDialog(it, onDismiss = { syncErrorDetails = null })
    }
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun DefaultTopAppBar(
    padName: String,
    syncStatus: PadViewModel.SyncStatus?,
    onDrawerButtonClicked: () -> Unit,
    onDeletePadClicked: () -> Unit,
    onStopSharingPadClicked: () -> Unit,
    onRenamePadClicked: () -> Unit,
    onSharePadClicked: () -> Unit,
    onSyncRequested: () -> Unit,
    modifier: Modifier = Modifier,
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = { Text(padName) },
        navigationIcon = {
            IconButton(onClick = onDrawerButtonClicked) {
                Icon(painterResource(R.drawable.ic_menu), stringResource(R.string.menu))
            }
        },
        actions = {
            var menuExpanded by remember { mutableStateOf(false) }
            when (syncStatus) {
                null, PadViewModel.SyncStatus.NONE -> {}

                PadViewModel.SyncStatus.OK -> {
                    IconButton(onClick = onSyncRequested) {
                        Icon(
                            painterResource(R.drawable.ic_sync),
                            stringResource(R.string.action_sync_pad),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    }
                }

                PadViewModel.SyncStatus.RUNNING -> {
                    // TODO(ux): use a spinning sync icon instead?
                    CircularProgressIndicator()
                }

                PadViewModel.SyncStatus.FAILED -> {
                    IconButton(onClick = onSyncRequested) {
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
                if (syncStatus == PadViewModel.SyncStatus.NONE) {
                    DropdownMenuItem(
                        leadingIcon = {
                            Icon(
                                painterResource(R.drawable.ic_delete),
                                stringResource(R.string.action_delete_pad),
                            )
                        },
                        text = { Text(stringResource(R.string.action_delete_pad)) },
                        onClick = {
                            onDeletePadClicked()
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
                            onStopSharingPadClicked()
                            menuExpanded = false
                        },
                        enabled = syncStatus != PadViewModel.SyncStatus.RUNNING,
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
                        onRenamePadClicked()
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
                        onSharePadClicked()
                        menuExpanded = false
                    },
                )
            }
        },
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
private fun SelectionTopAppBar(
    selectedCount: Int,
    onClearSelection: () -> Unit,
    onSelectAll: () -> Unit,
    onDeleteSelection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    BackHandler { onClearSelection() }

    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            titleContentColor = MaterialTheme.colorScheme.primary,
        ),
        title = {
            Text(
                stringResource(
                    R.string.pad_items_selection_title,
                    selectedCount,
                )
            )
        },
        navigationIcon = {
            IconButton(onClick = onClearSelection) {
                Icon(
                    painterResource(R.drawable.ic_arrow_back),
                    stringResource(R.string.navigate_back),
                )
            }
        },
        actions = {
            IconButton(onClick = onDeleteSelection) {
                Icon(
                    painterResource(R.drawable.ic_delete),
                    stringResource(R.string.action_delete_selected_pad_items),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            IconButton(onClick = onSelectAll) {
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
private fun PadContent(
    modifier: Modifier = Modifier,
    uncheckedItems: List<PadViewModel.Item>,
    checkedItems: List<PadViewModel.Item>,
    selectionMode: Boolean,
    readOnly: Boolean,
    onItemCheckedChange: (PadViewModel.Item, Boolean) -> Unit,
    onItemEditRequested: (PadViewModel.Item) -> Unit,
    onItemSelectedChange: (PadViewModel.Item, Boolean) -> Unit,
) {
    val lazyColumnState = rememberLazyListState()

    Box(modifier) {

        if (uncheckedItems.isEmpty() && checkedItems.isEmpty()) {
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
                    checked = false,
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
                        checked = true,
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
        item = PadViewModel.Item(
            name = "Potatoes",
            note = "2Kg",
            selected = false,
        ),
        checked = false,
        onClick = {},
    )
}

@Composable
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
private fun PadItem(
    item: PadViewModel.Item,
    checked: Boolean,
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
                    checked,
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
    suggestions: List<PadViewModel.Item>,
    updating: Boolean,
    initialName: String,
    initialNote: String,
    onCancel: (() -> Unit)? = null,
    onSubmit: ((name: String, note: String) -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val nameTextFieldState = rememberTextFieldState(initialText = initialName)
    val nameTextFieldValue = nameTextFieldState.text.toString()
    val nameTextFieldFocusRequester = remember { FocusRequester() }
    val nameTextFieldOk = nameTextFieldState.text.isNotBlank()
    var nameTextFieldLifecycle by remember { mutableIntStateOf(0) }

    val noteTextFieldState = rememberTextFieldState(initialText = initialNote)
    val noteTextFieldFocusRequester = remember { FocusRequester() }

    if (isFirstComposition()) {
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
            suggestionPool = suggestions.map { it.name },
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
            onSubmit = { input, fromSuggestions ->
                val fromItem = if (fromSuggestions) {
                    suggestions.find { it.name == input }
                } else {
                    null
                }
                if (fromItem != null) {
                    noteTextFieldState.setTextAndSelectAll(fromItem.note)
                }
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

@Composable
private fun SyncErrorDialog(
    error: SyncError,
    onDismiss: (() -> Unit),
) {
    AlertDialog(
        title = { Text(stringResource(R.string.sync_error)) },
        text = { Text(syncErrorMessage(error)) },
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = onDismiss,
            ) { Text(stringResource(R.string.dialog_close)) }
        },
    )
}
