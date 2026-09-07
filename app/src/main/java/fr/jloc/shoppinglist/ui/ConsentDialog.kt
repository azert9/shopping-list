package fr.jloc.shoppinglist.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R

@Composable
fun ConsentDialog(
    onAccept: (() -> Unit),
    onRefuse: (() -> Unit),
) {
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
        onDismissRequest = onRefuse,
        dismissButton = {
            TextButton(onClick = onRefuse) {
                Text(stringResource(R.string.dialog_submit_refuse))
            }
        },
        confirmButton = {
            TextButton(onClick = onAccept) {
                Text(stringResource(R.string.dialog_submit_accept))
            }
        },
    )
}
