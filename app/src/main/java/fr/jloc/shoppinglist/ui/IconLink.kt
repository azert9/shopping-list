package fr.jloc.shoppinglist.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

@Composable
fun IconLink(@DrawableRes icon: Int, @StringRes label: Int, @StringRes url: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            modifier = Modifier.padding(16.dp, 10.dp),
            painter = painterResource(icon),
            contentDescription = stringResource(label),
        )
        val s = buildAnnotatedString {
            pushLink(LinkAnnotation.Url(stringResource(url)))
            withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                append(stringResource(label))
            }
            pop()
        }
        Text(
            text = s,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}
