package fr.jloc.shoppinglist.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import fr.jloc.shoppinglist.R

@Preview(showBackground = true)
@Composable
private fun EditPadDialogPreview() {
    EditPadDialog(
        title = "Rename shopping list",
        initialName = "Groceries",
        submitButtonText = "Apply",
        canCancel = true,
        onCancel = {},
        onSubmit = {},
    )
}

@Composable
@OptIn(ExperimentalMaterial3Api::class)
fun EditPadDialog(
    title: String,
    initialName: String,
    submitButtonText: String,
    canCancel: Boolean,
    onCancel: () -> Unit,
    onSubmit: (String) -> Unit,
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
                    onSubmit(
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
