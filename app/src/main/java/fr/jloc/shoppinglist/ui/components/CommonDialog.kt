package fr.jloc.shoppinglist.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import fr.jloc.shoppinglist.R

@Preview(showBackground = true)
@Composable
fun CommonDialogPreview() {
    CommonDialog(
        title = "Title",
        canCancel = true,
        submitAction = {
            Button(onClick = {}) {
                Text(stringResource(R.string.dialog_submit_add))
            }
        },
        tertiaryAction = {
            TextButton(
                // TODO(ux): do we really want this red color?
                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                onClick = {},
            ) {
                Text(stringResource(R.string.dialog_submit_delete))
            }
        }
    ) {
        Text("Content.")
    }
}

@Composable
fun CommonDialog(
    title: String = "",
    onCancel: (() -> Unit)? = null,
    canCancel: Boolean = true,
    submitAction: @Composable () -> Unit,
    tertiaryAction: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Dialog(
        onDismissRequest = {
            if (canCancel) {
                onCancel?.invoke()
            }
        },
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
            ) {
                if (title.isNotEmpty()) {
                    Text(
                        modifier = Modifier.padding(bottom = 16.dp).testTag("dialog_title"),
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                content()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 24.dp),
                ) {
                    if (tertiaryAction != null) {
                        tertiaryAction()
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    if (canCancel) {
                        TextButton(onClick = { onCancel?.invoke() }) {
                            Text(stringResource(R.string.dialog_cancel))
                        }
                    }
                    submitAction()
                }
            }
        }
    }
}
