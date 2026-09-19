package fr.jloc.shoppinglist.ui.screens.pad

import androidx.annotation.MainThread
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import fr.jloc.shoppinglist.business.PadsManager
import fr.jloc.shoppinglist.business.sync.SharingURI
import fr.jloc.shoppinglist.business.sync.SyncError
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PadViewModel(
    private val pad: PadsManager.PadHandle,
    var onPadRenamed: (name: String) -> Unit,
    var onPadDeleted: () -> Unit,
    var onSharePad: (uri: SharingURI) -> Unit,
) : ViewModel() {

    class Factory(
        private val pads: PadsManager,
        private val padId: String,
    ) : ViewModelProvider.Factory {

        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST") val viewModel = PadViewModel(
                pad = pads.openPad(padId),
                onPadRenamed = {},
                onPadDeleted = {},
                onSharePad = {},
            ) as? T
            if (viewModel == null) {
                // we were asked to instantiate an unsupported ViewModel class
                throw UnsupportedOperationException()
            } else {
                return viewModel
            }
        }
    }

    override fun onCleared() {
        pad.close()
    }

    private val _name = MutableStateFlow(pad.name)
    val name = _name.asStateFlow()

    data class Item(
        val name: String,
        val note: String,
        val selected: Boolean,
    )

    // TODO(perf): use binary search when possible
    data class ItemLists(
        val selectedCount: Int,
        val unchecked: List<Item>,
        val checked: List<Item>,
    )

    private val _items = MutableStateFlow<ItemLists?>(null)
    val items = _items.asStateFlow()

    enum class SyncStatus {
        NONE, OK, RUNNING, FAILED
    }

    private val _syncStatus = MutableStateFlow<SyncStatus?>(null)
    val syncStatus = _syncStatus.asStateFlow()

    private var nextSyncError: SyncError? = null
    private val _syncError = MutableStateFlow<SyncError?>(null)
    val syncError = _syncError.asStateFlow()

    sealed class Dialog {
        data class EditItem(
            val initialName: String,
            val initialNote: String,
            /** List of item name suggestions. */
            val suggestions: List<Item>,
            /** Is this a new item, or are we updating an existing one. */
            val updating: Boolean,
            val dismiss: () -> Unit,
            val submit: (name: String, note: String) -> Unit,
            val delete: () -> Unit,
        ) : Dialog()

        data class ConfirmItemOverwrite(
            val name: String,
            val dismiss: () -> Unit,
            val submit: () -> Unit,
        ) : Dialog()

        data class RenamePad(
            val initialName: String,
            val dismiss: () -> Unit,
            val submit: (name: String) -> Unit,
        ) : Dialog()

        data class DeletePad(
            val dismiss: () -> Unit,
            val submit: () -> Unit,
        ) : Dialog()

        data class StopSharingPad(
            val dismiss: () -> Unit,
            val submit: (keepOnRemote: Boolean) -> Unit,
        ) : Dialog()
    }

    private val _dialog = MutableStateFlow<Dialog?>(null)
    val dialog = _dialog.asStateFlow()

    init {
        waitEndOfSyncAndFetchItems()
    }

    /** Start interactively creating a new item. Must not be called before items are loaded. */
    fun addItem() {
        val items = _items.value!!

        _dialog.value = Dialog.EditItem(
            initialName = "",
            initialNote = "",
            suggestions = items.checked,
            updating = false,
            dismiss = { _dialog.value = null },
            submit = { name, note ->
                _dialog.value = null
                if (items.unchecked.any { it.name == name }) {
                    // replacing an unchecked item (needs user confirmation)
                    _dialog.value = Dialog.ConfirmItemOverwrite(
                        name = name,
                        dismiss = { _dialog.value = null },
                        submit = {
                            _dialog.value = null
                            pad.updateItem(name, note = note)
                            _items.value = items.copy(
                                unchecked = items.unchecked.map {
                                    if (it.name == name) it.copy(note = note) else it
                                },
                            )
                        })
                } else if (items.checked.any { it.name == name }) {
                    // replacing a checked item
                    pad.updateItem(oldName = name, note = note, checked = false)
                    _items.value = items.remove(name).first.add(
                        Item(
                            name = name,
                            note = note,
                            selected = false,
                        )
                    )
                } else {
                    // adding a new item
                    pad.addItem(name, note)
                    val item = Item(
                        name = name,
                        note = note,
                        selected = false,
                    )
                    _items.value = items.add(item)
                }
            },
            delete = {
                _dialog.value = null
                assert(false)
            },
        )
    }

    /** Start interactively editing an existing item. Must not be called before items are loaded. */
    fun editItem(item: Item) {
        val items = _items.value!!

        _dialog.value = Dialog.EditItem(
            initialName = item.name,
            initialNote = item.note,
            suggestions = items.checked,
            updating = true,
            dismiss = { _dialog.value = null },
            submit = { name, note ->
                _dialog.value = null
                if (name != item.name && items.find(name) != null) {
                    _dialog.value = Dialog.ConfirmItemOverwrite(
                        name = name,
                        dismiss = { _dialog.value = null },
                        submit = {
                            _dialog.value = null
                            pad.deleteItem(name)
                            pad.updateItem(item.name, name = name, note = note, checked = false)
                            _items.value = items.remove(name).first.remove(item.name).first.add(
                                Item(name = name, note = note, selected = false),
                                checked = false,
                            )
                        },
                    )
                } else {
                    pad.updateItem(item.name, name = name, note = note, checked = false)
                    _items.value = items.remove(item.name).first.add(
                        Item(name = name, note = note, selected = false),
                        checked = false,
                    )
                }
            },
            delete = {
                _dialog.value = null
                pad.deleteItem(item.name)
                _items.value = items.remove(item.name).first
            },
        )
    }

    fun setItemChecked(item: Item, checked: Boolean) {
        val items = _items.value!!

        val (src, dst) = if (checked) {
            Pair(items.unchecked, items.checked)
        } else {
            Pair(items.checked, items.unchecked)
        }

        val srcIndex = src.indexOfFirst { it.name == item.name }
        if (srcIndex < 0) return

        val srcUpdated = src.filterIndexed { index, _ -> index != srcIndex }

        val dstUpdated = dst.add(src[srcIndex])

        pad.updateItem(item.name, checked = checked)

        _items.value = items.copy(
            unchecked = if (checked) srcUpdated else dstUpdated,
            checked = if (checked) dstUpdated else srcUpdated,
        )
    }

    fun selectAll() {
        val items = _items.value!!
        _items.value = items.copy(
            selectedCount = items.unchecked.size + items.checked.size,
            unchecked = items.unchecked.map { it.copy(selected = true) },
            checked = items.checked.map { it.copy(selected = true) },
        )
    }

    fun clearSelection() {
        val items = _items.value!!
        _items.value = items.copy(
            selectedCount = 0,
            unchecked = items.unchecked.map { it.copy(selected = false) },
            checked = items.checked.map { it.copy(selected = false) },
        )
    }

    fun setItemSelected(item: Item, selected: Boolean) {
        val items = _items.value!!

        items.unchecked.find { it.name == item.name }?.also { item ->
            if (item.selected == selected) return
            _items.value = items.copy(
                selectedCount = items.selectedCount + if (selected) 1 else -1,
                unchecked = items.unchecked.map { if (it.name == item.name) it.copy(selected = selected) else it })
        } ?: items.checked.find { it.name == item.name }?.also { item ->
            if (item.selected == selected) return
            _items.value = items.copy(
                selectedCount = items.selectedCount + if (selected) 1 else -1,
                checked = items.checked.map { if (it.name == item.name) it.copy(selected = selected) else it })
        }
    }

    fun deleteSelection() {
        val items = _items.value!!

        if (items.selectedCount == 0) return

        val toDelete =
            (items.unchecked.filter { it.selected } + items.checked.filter { it.selected }).map { it.name }

        if (toDelete.isEmpty()) return

        pad.deleteItems(toDelete)

        _items.value = items.copy(
            selectedCount = 0,
            unchecked = items.unchecked.filter { !it.selected },
            checked = items.checked.filter { !it.selected },
        )
    }

    fun renamePad() {
        _dialog.value = Dialog.RenamePad(
            initialName = pad.name,
            dismiss = { _dialog.value = null },
            submit = { name ->
                _dialog.value = null
                pad.rename(name)
                _name.value = name
                onPadRenamed(name)
            },
        )
    }

    fun deletePad() {
        _dialog.value = Dialog.DeletePad(
            dismiss = { _dialog.value = null },
            submit = {
                _dialog.value = null
                pad.delete()
                onPadDeleted()
            },
        )
    }

    fun sharePad() {
        if (pad.syncParams == null) {
            pad.setupSync()
        }
        onSharePad(SharingURI(pad.name, pad.syncParams!!))
        startSync()
    }

    fun startSync() {
        pad.startSync()
        waitEndOfSyncAndFetchItems()
    }

    fun stopSharingPad() {
        _dialog.value =
            Dialog.StopSharingPad(dismiss = { _dialog.value = null }, submit = { keepOnRemote ->
                _dialog.value = null
                if (pad.syncParams != null && !pad.isSynchronizing) {
                    pad.unSetupSync(keepOnRemote)
                    waitEndOfSyncAndFetchItems()
                }
            })
    }

    fun onSyncErrorDismissed() {
        _syncError.value = nextSyncError
        nextSyncError = null
    }

    private var runningTask: Job? = null

    @MainThread
    private fun waitEndOfSyncAndFetchItems() {

        _syncStatus.value = if (pad.syncParams == null) {
            SyncStatus.NONE
        } else if (pad.isSynchronizing) {
            SyncStatus.RUNNING
        } else {
            SyncStatus.OK
        }

        runningTask?.cancel()

        runningTask = viewModelScope.launch {
            val syncError = pad.waitEndOfSync()

            val fetched = pad.getItems()
            ensureActive()

            val unchecked = mutableListOf<Item>()
            val checked = mutableListOf<Item>()
            for (item in fetched) {
                val x = Item(
                    name = item.name,
                    note = item.note,
                    selected = false,
                )
                if (item.checked) {
                    checked.add(x)
                } else {
                    unchecked.add(x)
                }
            }
            unchecked.sortedBy { it.name }
            checked.sortedBy { it.name }
            _items.value = ItemLists(
                selectedCount = 0,
                unchecked = unchecked,
                checked = checked,
            )

            _syncStatus.value = if (pad.syncParams == null) {
                SyncStatus.NONE
            } else if (syncError == null) {
                SyncStatus.OK
            } else {
                if (_syncError.value == null) {
                    _syncError.value = syncError
                } else {
                    nextSyncError = syncError
                }
                SyncStatus.FAILED
            }

            runningTask = null
        }
    }

    private fun ItemLists.find(name: String): Item? =
        this.unchecked.find(name) ?: this.checked.find(name)

    private fun ItemLists.add(item: Item, checked: Boolean = false): ItemLists = run {
        val selectedCount = this.selectedCount + if (item.selected) 1 else 0
        if (checked) {
            this.copy(
                selectedCount = selectedCount,
                checked = this.checked.add(item),
            )
        } else {
            this.copy(
                selectedCount = selectedCount,
                unchecked = this.unchecked.add(item),
            )
        }
    }

    private fun ItemLists.remove(name: String): Pair<ItemLists, Item?> {
        run {
            val (newList, item) = this.unchecked.remove(name)
            if (item != null) {
                return Pair(
                    this.copy(
                        selectedCount = this.selectedCount + if (item.selected) -1 else 0,
                        unchecked = newList,
                    ), item
                )
            }
        }
        run {
            val (newList, item) = this.checked.remove(name)
            if (item != null) {
                return Pair(
                    this.copy(
                        selectedCount = this.selectedCount + if (item.selected) -1 else 0,
                        checked = newList,
                    ), item
                )
            }
        }
        return Pair(this, null)
    }

    private fun List<Item>.find(name: String): Item? =
        when (val index = this.binarySearchBy(name) { it.name }) {
            in 0..Int.MAX_VALUE -> this[index]
            else -> null
        }

    private fun List<Item>.add(item: Item): List<Item> = run {
        val index = -1 - this.binarySearchBy(item.name) { it.name }
        assert(index >= 0)
        val out = ArrayList<Item>((this.size + 16) / 16 * 16) // size+1, rounded up to 16
        out.addAll(this.subList(0, index))
        out.add(item)
        out.addAll(this.subList(index, this.size))
        out
    }

    private fun List<Item>.remove(name: String): Pair<List<Item>, Item?> = run {
        val index = this.binarySearchBy(name) { it.name }
        if (index < 0) {
            Pair(this, null)
        } else {
            val out = ArrayList<Item>((this.size + 14) / 16 * 16)  // size-1, rounded ip to 16
            out.addAll(this.subList(0, index))
            out.addAll(this.subList(index + 1, this.size))
            Pair(out, this[index])
        }
    }
}
