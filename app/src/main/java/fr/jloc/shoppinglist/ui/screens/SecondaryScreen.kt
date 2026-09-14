package fr.jloc.shoppinglist.ui.screens

import android.os.Build
import android.view.RoundedCorner
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R

@Composable
fun SecondaryScreen(
    @StringRes title: Int,
    onDismiss: (() -> Unit),
    content: @Composable (PaddingValues) -> Unit,
) {
    // the rounded corners of the screen can become visible when performing predictive back navigation

    val shape = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val insets = LocalView.current.rootWindowInsets
        val density = LocalDensity.current
        val layoutDirection = LocalLayoutDirection.current
        remember(insets, density, layoutDirection) {
            val ltr = layoutDirection == LayoutDirection.Ltr
            with(density) {
                RoundedCornerShape(
                    insets.getRoundedCorner(if (ltr) RoundedCorner.POSITION_TOP_LEFT else RoundedCorner.POSITION_TOP_RIGHT)?.radius?.toDp()
                        ?: 0.dp,
                    insets.getRoundedCorner(if (ltr) RoundedCorner.POSITION_TOP_RIGHT else RoundedCorner.POSITION_TOP_LEFT)?.radius?.toDp()
                        ?: 0.dp,
                    insets.getRoundedCorner(if (ltr) RoundedCorner.POSITION_BOTTOM_RIGHT else RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.toDp()
                        ?: 0.dp,
                    insets.getRoundedCorner(if (ltr) RoundedCorner.POSITION_BOTTOM_LEFT else RoundedCorner.POSITION_BOTTOM_RIGHT)?.radius?.toDp()
                        ?: 0.dp,
                )
            }
        }
    } else {
        RoundedCornerShape(0, 0, 0, 0)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape = shape),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(title)) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(
                            painterResource(R.drawable.ic_arrow_back),
                            stringResource(R.string.navigate_back),
                        )
                    }
                },
            )
        },
        content = content,
    )
}
