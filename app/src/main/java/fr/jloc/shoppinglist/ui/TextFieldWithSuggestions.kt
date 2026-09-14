package fr.jloc.shoppinglist.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldLabelScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.TimeSource

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TextFieldWithSuggestions(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    suggestionPool: List<String>,
    label: @Composable (TextFieldLabelScope.() -> Unit)?,
    onSubmit: ((String, Boolean) -> Unit)? = null,
    isError: Boolean = false,
) {
    // Combination of a text field and a dropdown menu.
    // The dropdown is displayed when the text field is focused.

    val coroutineScope = rememberCoroutineScope()
    var suggestions by remember { mutableStateOf(listOf<String>()) }
    var suggestionJob by remember { mutableStateOf<Job?>(null) }
    var acceptedSuggestion by remember { mutableStateOf<String?>(null) } // needed because filing the text field by suggestion would trigger suggestions again
    var focused by remember { mutableStateOf(false) }

    // computing suggestions asynchronously

    val suggestionDelay = 200.milliseconds

    fun stopSuggestions() {
        suggestionJob?.cancel()
        suggestions = listOf()
    }

    LaunchedEffect(state.text, focused) {
        stopSuggestions()
        if (focused && !state.text.isEmpty() && state.text != acceptedSuggestion) {
            acceptedSuggestion = null
            val input = state.text
            val suggestionStartT = TimeSource.Monotonic.markNow()
            suggestionJob = coroutineScope.launch(Dispatchers.Default) {
                // computing suggestions from background thread
                val result = getSuggestions(input, suggestionPool)
                // small delay to avoid showing and hiding suggestions too rapidly between keystrokes
                val elapsed = TimeSource.Monotonic.markNow() - suggestionStartT
                if (elapsed < suggestionDelay) {
                    delay(suggestionDelay - elapsed)
                }
                // applying the result from the UI thread
                withContext(Dispatchers.Main) {
                    ensureActive()
                    suggestions = result
                }
            }
        }
    }

    // ui

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = suggestions.isNotEmpty(),
        onExpandedChange = {},
    ) {
        OutlinedTextField(
            state = state,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .onFocusChanged { state -> focused = state.isFocused },
            label = label,
            lineLimits = TextFieldLineLimits.SingleLine,
            onKeyboardAction = {
                onSubmit?.invoke(state.text.toString(), false)
            },
            isError = isError,
        )
        ExposedDropdownMenu(
            expanded = suggestions.isNotEmpty(),
            onDismissRequest = { suggestions = listOf() },
        ) {
            for (suggestion in suggestions) {
                DropdownMenuItem(text = { Text(suggestion) }, onClick = {
                    state.setTextAndPlaceCursorAtEnd(suggestion)
                    acceptedSuggestion = suggestion
                    stopSuggestions()
                    onSubmit?.invoke(suggestion, true)
                })
            }
        }
    }
}

private fun getSuggestions(
    input: CharSequence, pool: List<String>
): List<String> {

    val inputLower = input.toString().lowercase()

    val candidates = pool.filter { it.lowercase().indexOf(inputLower) >= 0 }

    return if (candidates.size <= 5) {
        candidates
    } else {
        listOf()
    }
}
