package fr.jloc.shoppinglist.ui.activities.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.jloc.shoppinglist.business.PadsManager
import fr.jloc.shoppinglist.ShoppingListApp
import fr.jloc.shoppinglist.business.PadSyncParams
import fr.jloc.shoppinglist.business.PadItem
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.business.sync.SyncError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.LinkedList
import kotlin.collections.binarySearchBy

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val app = app as ShoppingListApp

    // dialogs

    private var dialogState: DialogState? = null
    private val _dialog = MutableStateFlow<Dialog?>(null)
    val dialog = _dialog.asStateFlow()

    // list of pads (for navigation)

    data class SelectablePad(
        val key: Any,
        val name: String,
        val selected: Boolean,
    )

    private val _selectablePads = MutableStateFlow<List<SelectablePad>>(listOf())
    val selectablePads: StateFlow<List<SelectablePad>> = _selectablePads.asStateFlow()

    // details of the selected pad

    private val _padName = MutableStateFlow<String?>(null)
    val padName = _padName.asStateFlow()

    data class Item(
        val name: String,
        val note: String,
        val checked: Boolean,
        val selected: Boolean,
    )

    interface ItemLists {

        val padId: String

        /** Unchecked items, sorted by name. */
        val unchecked: List<Item>

        /** Checked items, sorted by name. */
        val checked: List<Item>

        val selectedCount: Int
    }

    private val _items = MutableStateFlow<InternalItemLists?>(null)
    val items: StateFlow<ItemLists?> = _items.asStateFlow()

    enum class SyncStatus {
        NONE, OK, RUNNING, FAILED,
    }

    private val _syncStatus = MutableStateFlow<SyncStatus?>(null)
    val syncStatus = _syncStatus.asStateFlow()

    // sync error notifications

    private val _syncErrors = NotificationQueue<SyncError>()
    val syncError = _syncErrors.currentNotification

    //

    private var pads: PadsManager? = null

    /** Handle of the currently selected pad. */
    private var pad: PadsManager.PadHandle? = null

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val lastSelectedPadId = (app as ShoppingListApp).getLastSelectedPad()
            pads = app.padsManager()
            _selectablePads.value = pads!!.pads.map { SelectablePad(it.id, it.name, false) }
            if (lastSelectedPadId != null && pads!!.pads.any { it.id == lastSelectedPadId }) {
                selectPad(lastSelectedPadId)
            } else {
                selectFirstPadOrPromptCreation()
            }
        }
    }

    override fun onCleared() {
        pad?.close()
        // TODO: wait for queued db operations to complete
    }

    private fun selectFirstPadOrPromptCreation() {
        if (pads!!.pads.isEmpty()) {
            // no pad available, must create one
            _padName.value = null
            _items.value = null
            _syncStatus.value = null
            setDialogState(DialogState.CreatePad(cancellable = false))
        } else {
            selectPad(pads!!.pads.first().id)
        }
    }

    private fun selectPad(id: String) {

        if (pad?.id != id) {

            viewModelScope.launch(Dispatchers.IO) {
                app.setLastSelectedPad(id)
            }

            pad?.close()
            pad = pads!!.openPad(id)

            _selectablePads.value = pads!!.pads.map { SelectablePad(it.id, it.name, it.id == id) }

            _padName.value = pad!!.name

            _items.value = null

            waitEndOfSyncAndFetchItems()
        }
    }

    fun onSyncErrorDismissed(detailsRequested: Boolean) {

        val notif = _syncErrors.currentNotification.value
        if (detailsRequested && notif != null) {
            setDialogState(DialogState.SyncErrorDetails(notif))
        }

        _syncErrors.dismiss()
    }

    fun onPadSelected(key: Any) {
        selectPad(key as String)
    }

    fun onPadCreationRequested() {
        if (dialogState == null) {
            setDialogState(DialogState.CreatePad(true))
        }
    }

    fun onPadDeletionRequested() {
        val pad = pad ?: return
        setDialogState(DialogState.DeletePad(pad.id, pad.name))
    }

    fun onPadRenamingRequested() {
        val pad = pad ?: return
        setDialogState(DialogState.RenamePad(pad.id, pad.name))
    }

    fun onPadSharingRequested() {

        val pad = pad ?: return

        if (pad.syncParams == null) {
            pad.setupSync()
        }
        val syncParams = pad.syncParams!!

        setDialogState(DialogState.SharePad(pad.name, syncParams))

        pad.startSync()
        waitEndOfSyncAndFetchItems()
    }

    fun onPadStopSharingRequested() {

        val pad = pad ?: return
        if (pad.syncParams == null) return
        if (pad.isSynchronizing) return

        setDialogState(DialogState.StopSharingPad(pad.id, pad.name))
    }

    fun onSyncRequested() {

        val pad = pad ?: return

        if (pad.syncParams != null) {
            pad.startSync()
            waitEndOfSyncAndFetchItems()
        }
    }

    private var runningTask: Job? = null

    private fun waitEndOfSyncAndFetchItems() {

        _syncStatus.value = if (pad == null) {
            null
        } else if (pad!!.syncParams == null) {
            SyncStatus.NONE
        } else if (pad!!.isSynchronizing) {
            SyncStatus.RUNNING
        } else {
            SyncStatus.OK
        }

        val pad = pad!!

        runningTask?.cancel()

        runningTask = viewModelScope.launch {
            val syncError = pad.waitEndOfSync()
            val fetched = pad.getItems()
            ensureActive()
            _items.value = InternalItemLists.new(pad.id, fetched)
            _syncStatus.value = if (pad.syncParams == null) {
                SyncStatus.NONE
            } else if (syncError == null) {
                SyncStatus.OK
            } else {
                _syncErrors.push(syncError)
                SyncStatus.FAILED
            }
            runningTask = null
        }
    }

    fun onItemCreationRequested() {

        val pad = pad ?: return
        if (pad.isSynchronizing) return

        setDialogState(
            DialogState.EditItem(
                padId = pad.id,
                editedItem = null,
                initialName = "",
                initialNote = "",
            )
        )
    }

    fun onItemEditRequested(item: Item) {

        val pad = pad ?: return
        if (pad.isSynchronizing) return

        setDialogState(
            DialogState.EditItem(
                padId = pad.id,
                editedItem = item,
                initialName = item.name,
                initialNote = item.note,
            )
        )
    }

    fun onItemCheckedChanged(name: String, checked: Boolean) {

        val pad = pad ?: return
        if (pad.isSynchronizing) return
        val items = _items.value ?: return

        _items.value = items.withItemUpdated(name, checked)

        pad.updateItem(name, checked = checked)
    }

    fun onClearSelection() {
        _items.value = _items.value?.withNoneSelected()
    }

    fun onSelectAll() {
        _items.value = _items.value?.withAllSelected()
    }

    fun onItemSelectedChanged(name: String, selected: Boolean) {
        _items.value = _items.value?.withItemSelected(name, selected)
    }

    fun onDeleteSelection() {

        val pad = pad ?: return
        if (pad.isSynchronizing) return
        val items = _items.value ?: return

        val (remaining, deleted) = items.withoutSelectedItems()
        _items.value = remaining

        pad.deleteItems(deleted.map { it.name })
    }

    private fun onAddPadDialogSubmitted(name: String) {

        // TODO: disable action if the PadManager is not loaded

        setDialogState(null)

        val newPadId = pads!!.createPad(name)

        selectPad(newPadId)
    }

    private fun onDeletePadDialogSubmitted() {

        val dialogState = dialogState as DialogState.DeletePad
        setDialogState(null)

        val pad = pad ?: return
        if (dialogState.padId != pad.id) return

        pad.delete()
        pad.close()
        this.pad = null

        _selectablePads.value = _selectablePads.value.filter { it.key as String != pad.id }

        selectFirstPadOrPromptCreation()
    }

    private fun onRenamePadDialogSubmitted(newName: String) {

        val dialogState = dialogState as DialogState.RenamePad
        setDialogState(null)

        val pad = pad ?: return
        if (dialogState.padId != pad.id) return

        pad.rename(newName)

        _padName.value = newName

        _selectablePads.value = _selectablePads.value.map { selectablePad ->
            if (selectablePad.key as String == pad.id) {
                selectablePad.copy(name = newName)
            } else {
                selectablePad
            }
        }
    }

    private fun onStopSharingPadDialogSubmitted(keepOnRemote: Boolean) {

        val dialogState = dialogState as DialogState.StopSharingPad
        setDialogState(null)

        val pad = pad ?: return
        if (dialogState.padId != pad.id) return
        if (pad.syncParams == null || pad.isSynchronizing) return

        pad.unSetupSync(keepOnRemote)
        waitEndOfSyncAndFetchItems()
    }

    private fun onEditItemDialogSubmitted(name: String, note: String) {

        val dialogState = dialogState as DialogState.EditItem
        setDialogState(null)

        val pad = pad ?: return
        if (pad.isSynchronizing) return
        var items = _items.value!!

        // if we are editing an existing item without changing its name,
        // we don't have to check for conflicts

        if (name == dialogState.editedItem?.name) {

            // updating the view model

            _items.value = items.withItemUpdated(
                name,
                checked = dialogState.editedItem.checked,
                note = note,
            )

            // updating the database

            pad.updateItem(name, checked = dialogState.editedItem.checked, note = note)

            return
        }

        //

        val existing = items.getItem(name)

        // if the item exists and is unchecked, we want a confirmation before overwriting it

        if (existing != null && !existing.checked) {
            setDialogState(
                DialogState.ConfirmItemOverwrite(
                    prevState = DialogState.EditItem(
                        padId = items.padId,
                        editedItem = dialogState.editedItem,
                        initialName = name,
                        initialNote = existing.note
                    ),
                    newName = name,
                    newNote = note,
                )
            )
            return
        }

        // if the item exists and is checked, we accept to overwrite it without confirmation

        if (existing != null) {
            pad.deleteItem(name)
            items = items.withoutItem(name)
        }

        if (dialogState.editedItem != null) {
            pad.updateItem(
                oldName = dialogState.editedItem.name,
                name = name,
                note = note,
                checked = false,
            )
            items = items.withoutItem(dialogState.editedItem.name).withItemAdded(
                name = name,
                note = note,
                checked = false,
            )
        } else {
            pad.addItem(name = name, note = note)
            items = items.withItemAdded(name = name, note = note, checked = false)
        }

        _items.value = items
    }

    private fun onEditItemDialogDelete() {

        val dialogState = dialogState as DialogState.EditItem
        val item = dialogState.editedItem!!
        setDialogState(null)

        val pad = pad ?: return
        if (pad.isSynchronizing) return
        val items = _items.value ?: return

        _items.value = items.withoutItem(item.name)

        pad.deleteItem(item.name)
    }

    private fun onConfirmItemOverwriteDialogSubmitted() {

        val dialogState = dialogState as DialogState.ConfirmItemOverwrite
        setDialogState(null)

        val pad = pad ?: return
        if (pad.isSynchronizing) return
        var items = _items.value!!

        pad.deleteItem(dialogState.newName)
        items = items.withoutItem(dialogState.newName)

        if (dialogState.prevState.editedItem != null) {
            pad.updateItem(
                oldName = dialogState.prevState.editedItem.name,
                name = dialogState.newName,
                note = dialogState.newNote,
                checked = false,
            )
            items = items.withoutItem(dialogState.prevState.editedItem.name)
        } else {
            pad.addItem(
                name = dialogState.newName,
                note = dialogState.newNote,
            )
        }

        items = items.withItemAdded(
            name = dialogState.newName,
            checked = false,
            note = dialogState.newNote,
        )

        _items.value = items
    }

    private fun setDialogState(state: DialogState?) {
        this.dialogState = state
        _dialog.value = state?.toDialog(this)
    }

    sealed class Dialog(protected val viewModel: MainViewModel) {

        fun onCancel() {
            viewModel.setDialogState(viewModel.dialogState!!.cancel())
        }

        class AddPad(
            viewModel: MainViewModel,
            val cancellable: Boolean,
        ) : Dialog(viewModel) {

            fun onSubmit(name: String) {
                viewModel.onAddPadDialogSubmitted(name)
            }
        }

        class DeletePad(
            viewModel: MainViewModel,
            val padName: String,
        ) : Dialog(viewModel) {

            fun onSubmit() {
                viewModel.onDeletePadDialogSubmitted()
            }
        }

        class RenamePad(
            viewModel: MainViewModel,
            val initialName: String,
        ) : Dialog(viewModel) {

            fun onSubmit(newName: String) {
                viewModel.onRenamePadDialogSubmitted(newName)
            }
        }

        class SharePad(
            viewModel: MainViewModel,
            val uri: String,
        ) : Dialog(viewModel)

        class StopSharingPad(
            viewModel: MainViewModel,
            val padName: String,
        ) : Dialog(viewModel) {

            fun onSubmit(keepOnRemote: Boolean) {
                viewModel.onStopSharingPadDialogSubmitted(keepOnRemote)
            }
        }

        class EditItem(
            viewModel: MainViewModel,
            val updating: Boolean,
            val initialName: String,
            val initialNote: String,
        ) : Dialog(viewModel) {

            fun onSubmit(name: String, note: String) {
                viewModel.onEditItemDialogSubmitted(name, note)
            }

            fun onDelete() {
                viewModel.onEditItemDialogDelete()
            }
        }

        class ConfirmItemOverwrite(viewModel: MainViewModel, val name: String) : Dialog(viewModel) {

            fun onSubmit() {
                viewModel.onConfirmItemOverwriteDialogSubmitted()
            }
        }

        class SyncErrorDetails(viewModel: MainViewModel, val error: SyncError) : Dialog(viewModel)
    }
}

private sealed class DialogState {

    abstract fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog

    open fun cancel(): DialogState? {
        return null
    }

    class CreatePad(val cancellable: Boolean) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.AddPad(
                viewModel = viewModel,
                cancellable = cancellable,
            )
        }
    }

    class DeletePad(val padId: String, val padName: String) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.DeletePad(
                viewModel = viewModel,
                padName = padName,
            )
        }
    }

    class RenamePad(val padId: String, val initialName: String) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.RenamePad(
                viewModel = viewModel,
                initialName = initialName,
            )
        }
    }

    data class SharePad(
        val padName: String,
        val syncParams: PadSyncParams,
    ) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.SharePad(
                viewModel = viewModel,
                uri = SharingURI(padName, syncParams).encode(),
            )
        }
    }

    class StopSharingPad(val padId: String, val padName: String) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.StopSharingPad(
                viewModel = viewModel,
                padName = padName,
            )
        }
    }

    data class EditItem(
        val padId: String,
        val editedItem: MainViewModel.Item?, // null = new item being created
        val initialName: String,
        val initialNote: String,
    ) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.EditItem(
                viewModel = viewModel,
                updating = editedItem != null,
                initialName = initialName,
                initialNote = initialNote,
            )
        }
    }

    data class ConfirmItemOverwrite(
        val prevState: EditItem,
        val newName: String,
        val newNote: String,
    ) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.ConfirmItemOverwrite(
                viewModel = viewModel,
                name = newName,
            )
        }

        override fun cancel(): DialogState {
            return prevState
        }
    }

    data class SyncErrorDetails(
        val error: SyncError
    ) : DialogState() {

        override fun toDialog(viewModel: MainViewModel): MainViewModel.Dialog {
            return MainViewModel.Dialog.SyncErrorDetails(
                viewModel = viewModel,
                error = error,
            )
        }
    }
}

private class NotificationQueue<T> {

    private val queue = LinkedList<T>()

    private val _currentNotification = MutableStateFlow<T?>(null)
    val currentNotification = _currentNotification.asStateFlow()

    fun push(notification: T) {
        if (_currentNotification.value == null) {
            _currentNotification.value = notification
        } else {
            queue.addLast(notification)
        }
    }

    fun dismiss() {
        if (queue.isNotEmpty()) {
            _currentNotification.value = queue.removeFirst()
        } else {
            _currentNotification.value = null
        }
    }
}

/** Provides facilities that are useful for the ViewModel, but not needed by the View. */
private class InternalItemLists(
    override val padId: String,
    override val unchecked: List<MainViewModel.Item>,
    override val checked: List<MainViewModel.Item>,
    override val selectedCount: Int,
) : MainViewModel.ItemLists {
    companion object {
        fun new(
            padId: String, items: List<PadItem> = listOf()
        ): InternalItemLists {

            val unchecked = mutableListOf<MainViewModel.Item>()
            val checked = mutableListOf<MainViewModel.Item>()

            for (item in items) {
                val x = MainViewModel.Item(
                    name = item.name,
                    note = item.note,
                    checked = item.checked,
                    selected = false,
                )
                if (item.checked) {
                    checked.add(x)
                } else {
                    unchecked.add(x)
                }
            }

            unchecked.sortBy { it.name }
            checked.sortBy { it.name }

            return InternalItemLists(
                padId = padId,
                unchecked = unchecked,
                checked = checked,
                selectedCount = 0,
            )
        }

        private fun <T> copyListWithoutIndex(items: List<T>, index: Int): List<T> {
            val out = arrayListOf<T>()
            out.ensureCapacity(items.size - 1)
            out.addAll(items.subList(0, index))
            out.addAll(items.subList(index + 1, items.size))
            return out
        }

        private fun copyListWithAddedItem(
            items: List<MainViewModel.Item>, item: MainViewModel.Item
        ): List<MainViewModel.Item> {

            val out = arrayListOf<MainViewModel.Item>()
            out.ensureCapacity(items.size + 1)

            var inserted = false

            for (x in items) {
                val cmp = item.name.compareTo(x.name)
                if (inserted || cmp > 0) {
                    out.add(x)
                } else if (cmp < 0) {
                    out.add(item)
                    out.add(x)
                    inserted = true
                } else {
                    // replacing an existing item
                    out.add(item)
                    inserted = true
                }
            }

            if (!inserted) {
                out.add(item)
            }

            return out
        }
    }

    fun getItem(name: String): MainViewModel.Item? {
        return findItemByName(name)?.second
    }

    fun withNoneSelected(): InternalItemLists {
        return if (selectedCount == 0) {
            this
        } else {
            InternalItemLists(
                padId = padId,
                unchecked = unchecked.map { it.copy(selected = false) },
                checked = checked.map { it.copy(selected = false) },
                selectedCount = 0,
            )
        }
    }

    fun withAllSelected(): InternalItemLists {
        return if (selectedCount == unchecked.size + checked.size) {
            this
        } else {
            InternalItemLists(
                padId = padId,
                unchecked = unchecked.map { it.copy(selected = true) },
                checked = checked.map { it.copy(selected = true) },
                selectedCount = unchecked.size + checked.size,
            )
        }
    }

    fun withItemAdded(name: String, note: String, checked: Boolean): InternalItemLists {

        if (findItemByName(name) != null) throw RuntimeException("An item exists with this name: $name")

        val newItem = MainViewModel.Item(
            name = name,
            note = note,
            checked = checked,
            selected = false,
        )

        return if (checked) {
            InternalItemLists(
                padId = padId,
                unchecked = unchecked,
                checked = copyListWithAddedItem(this.checked, newItem),
                selectedCount = selectedCount,
            )
        } else {
            InternalItemLists(
                padId = padId,
                unchecked = copyListWithAddedItem(unchecked, newItem),
                checked = this.checked,
                selectedCount = selectedCount,
            )
        }
    }

    fun withItemUpdated(
        name: String,
        checked: Boolean? = null,
        note: String? = null,
    ): InternalItemLists {

        val (index, item) = findItemByName(name)
            ?: throw RuntimeException("No item with this name: $name")

        val newItem = item.copy(
            checked = checked ?: item.checked,
            note = note ?: item.note,
        )

        return if (item.checked == newItem.checked) {

            // max one list to rebuild

            if (item.note == note) {
                this // no change at all
            } else {
                if (item.checked) {
                    InternalItemLists(
                        padId = padId,
                        unchecked = unchecked,
                        checked = copyListWithAddedItem(this.checked, newItem),
                        selectedCount = selectedCount,
                    )
                } else {
                    InternalItemLists(
                        padId = padId,
                        unchecked = copyListWithAddedItem(this.unchecked, newItem),
                        checked = this.checked,
                        selectedCount = selectedCount,
                    )
                }
            }

        } else {

            // we have to rebuild both lists

            if (newItem.checked) {
                InternalItemLists(
                    padId = padId,
                    unchecked = copyListWithoutIndex(unchecked, index),
                    checked = copyListWithAddedItem(this.checked, newItem),
                    selectedCount = selectedCount,
                )
            } else {
                InternalItemLists(
                    padId = padId,
                    unchecked = copyListWithAddedItem(unchecked, newItem),
                    checked = copyListWithoutIndex(this.checked, index),
                    selectedCount = selectedCount,
                )
            }
        }
    }

    fun withItemSelected(name: String, selected: Boolean): InternalItemLists {

        val (index, item) = findItemByName(name)
            ?: throw RuntimeException("No item with this name: $name")

        if (item.selected == selected) {
            // no change
            return this
        }

        fun copyWithSelected(
            items: List<MainViewModel.Item>, index: Int, selected: Boolean
        ): List<MainViewModel.Item> {
            val out = items.toMutableList()
            out[index] = out[index].copy(selected = selected)
            return out
        }

        return InternalItemLists(
            padId = padId,
            unchecked = if (item.checked) unchecked else copyWithSelected(
                unchecked, index, selected
            ),
            checked = if (item.checked) copyWithSelected(checked, index, selected) else checked,
            selectedCount = selectedCount + if (selected) 1 else -1
        )
    }

    /** If the item does not exist, acts as the identity function. */
    fun withoutItem(name: String): InternalItemLists {

        val (index, item) = findItemByName(name) ?: return this

        val newSelectedCount = if (item.selected) selectedCount - 1 else selectedCount

        return if (item.checked) {
            InternalItemLists(
                padId = padId,
                unchecked = unchecked,
                checked = copyListWithoutIndex(checked, index),
                selectedCount = newSelectedCount,
            )
        } else {
            InternalItemLists(
                padId = padId,
                unchecked = copyListWithoutIndex(unchecked, index),
                checked = checked,
                selectedCount = newSelectedCount,
            )
        }
    }

    fun withoutSelectedItems(): Pair<InternalItemLists, List<MainViewModel.Item>> {

        val deleted = arrayListOf<MainViewModel.Item>()
        deleted.ensureCapacity(selectedCount)

        val remaining = InternalItemLists(
            padId = padId,
            unchecked = unchecked.filter { item ->
                if (item.selected) {
                    deleted.add(item)
                    false
                } else {
                    true
                }
            },
            checked = checked.filter { item ->
                if (item.selected) {
                    deleted.add(item)
                    false
                } else {
                    true
                }
            },
            selectedCount = 0,
        )

        return Pair(remaining, deleted)
    }

    private fun findItemByName(name: String): Pair<Int, MainViewModel.Item>? {

        val indexInUnchecked = unchecked.binarySearchBy(name, selector = { it.name })
        if (indexInUnchecked >= 0) {
            return Pair(indexInUnchecked, unchecked[indexInUnchecked])
        }

        val indexInChecked = checked.binarySearchBy(name, selector = { it.name })
        if (indexInChecked >= 0) {
            return Pair(indexInChecked, checked[indexInChecked])
        }

        return null
    }
}
