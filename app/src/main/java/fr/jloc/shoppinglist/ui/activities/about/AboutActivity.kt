package fr.jloc.shoppinglist.ui.activities.about

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.ui.components.IconLink
import fr.jloc.shoppinglist.ui.theme.ShoppingListTheme

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Screen(onBackPressed = { onBackPressedDispatcher.onBackPressed() }) }
    }
}

@Preview(showBackground = true)
@Composable
fun ScreenPreview() {
    Screen()
}

@Composable
fun Screen(onBackPressed: (() -> Unit)? = null) {
    ShoppingListTheme {
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.activity_about_title)) },
                    navigationIcon = {
                        IconButton(onClick = { onBackPressed?.invoke() }) {
                            Icon(
                                painterResource(R.drawable.ic_arrow_back),
                                stringResource(R.string.navigate_back),
                            )
                        }
                    },
                )
            },
        ) { contentPadding ->
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
}
