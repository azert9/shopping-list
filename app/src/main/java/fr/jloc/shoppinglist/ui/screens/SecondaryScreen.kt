package fr.jloc.shoppinglist.ui.screens

import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import fr.jloc.shoppinglist.R

@Composable
fun SecondaryScreen(
    @StringRes title: Int,
    onDismiss: (() -> Unit),
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.ic_close),
                            stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}
