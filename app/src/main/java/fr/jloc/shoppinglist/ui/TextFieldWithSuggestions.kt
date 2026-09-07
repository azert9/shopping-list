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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun TextFieldWithSuggestions(
    state: TextFieldState,
    modifier: Modifier = Modifier,
    suggestions: List<String>,
    label: @Composable (TextFieldLabelScope.() -> Unit)?,
    onSubmit: ((CharSequence) -> Unit)? = null,
    isError: Boolean = false,
) {
    // Combination of a text field and a dropdown menu.
    // The dropdown is displayed when the text field is focused.

    val relevantSuggestions = suggestions.filter { suggestion ->
        // TODO(feature): fuzzy search (should at least be cases-insensitive)
        suggestion.startsWith(state.text)
    }

    var suggestionsDismissed by remember { mutableStateOf(false) }
    val suggestionsExpanded =
        !suggestionsDismissed && relevantSuggestions.isNotEmpty() && relevantSuggestions.size <= 5

    ExposedDropdownMenuBox(
        modifier = modifier,
        expanded = suggestionsExpanded,
        onExpandedChange = {},
    ) {
        OutlinedTextField(
            state = state,
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable)
                .fillMaxWidth()
                .onFocusChanged { state -> suggestionsDismissed = !state.isFocused },
            label = label,
            lineLimits = TextFieldLineLimits.SingleLine,
            onKeyboardAction = {
                onSubmit?.invoke(state.text)
            },
            isError = isError,
        )
        ExposedDropdownMenu(
            expanded = suggestionsExpanded,
            onDismissRequest = { suggestionsDismissed = true },
        ) {
            if (suggestionsExpanded) {
                for (suggestion in relevantSuggestions) {
                    DropdownMenuItem(text = { Text(suggestion) }, onClick = {
                        state.setTextAndPlaceCursorAtEnd(suggestion)
                        onSubmit?.invoke(suggestion)
                    })
                }
            }
        }
    }
}
