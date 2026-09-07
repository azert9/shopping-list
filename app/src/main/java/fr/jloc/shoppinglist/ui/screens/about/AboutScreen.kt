package fr.jloc.shoppinglist.ui.screens.about

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.ui.IconLink
import fr.jloc.shoppinglist.ui.screens.SecondaryScreen

@Preview(showBackground = true)
@Composable
fun AboutScreenPreview() {
    AboutScreen(onDismiss = {})
}

@Composable
fun AboutScreen(onDismiss: () -> Unit) {
    SecondaryScreen(title = R.string.about_screen_title, onDismiss = onDismiss) { contentPadding ->
        Surface(
            modifier = Modifier
                .padding(contentPadding)
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
        ) {
            Column(Modifier.padding(8.dp, 16.dp)) {

                // TODO: license, version

                IconLink(
                    R.drawable.ic_privacy,
                    R.string.privacy_policy,
                    R.string.privacy_policy_url,
                )
                IconLink(
                    R.drawable.ic_contract,
                    R.string.terms_of_use,
                    R.string.terms_of_use_url,
                )
                IconLink(
                    R.drawable.ic_source_code,
                    R.string.source_code,
                    R.string.source_code_url,
                )
            }
        }
    }
}
