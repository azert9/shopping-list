package fr.jloc.shoppinglist.ui.screens.share_pad

import android.content.ClipData
import android.graphics.BitmapFactory
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.ui.screens.SecondaryScreen
import kotlinx.coroutines.launch
import qrcode.QRCode
import androidx.core.net.toUri

@Preview
@Composable
private fun SharePadScreenPreview() {
    SharePadScreen(uri = "https://example.com/") { }
}

@Composable
fun SharePadScreen(uri: String, onDismiss: (() -> Unit)) {
    SecondaryScreen(
        title = R.string.share_pad_screen_title,
        onDismiss = onDismiss,
    ) { innerPadding ->
        SharePadDialogBody(uri, Modifier.padding(innerPadding))
    }
}

@Composable
private fun SharePadDialogBody(
    uri: String,
    modifier: Modifier = Modifier,
) {
    val png = QRCode.ofSquares().withInnerSpacing(0)
        .withColor(MaterialTheme.colorScheme.onBackground.toArgb()).build(uri).renderToBytes()

    val bitmap = BitmapFactory.decodeByteArray(png, 0, png.size).asImageBitmap()

    val coroutineScope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardToastText = stringResource(R.string.copied_link)

    Box(Modifier.fillMaxSize() then modifier) {
        Column(
            Modifier
                .align(Alignment.Center)
                .padding(64.dp)
        ) {
            Image(
                modifier = Modifier
                    .widthIn(0.dp, 400.dp)
                    .heightIn(0.dp, 400.dp)
                    .align(Alignment.CenterHorizontally)
                    .padding(bottom = 64.dp),
                painter = BitmapPainter(bitmap),
                contentDescription = stringResource(R.string.qr_code),
            )
            Icon(
                modifier = Modifier
                    .padding(16.dp, 10.dp)
                    .align(Alignment.CenterHorizontally),
                painter = painterResource(R.drawable.ic_info),
                contentDescription = stringResource(R.string.info_icon),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val s = buildAnnotatedString {
                append(stringResource(R.string.share_pad_instructions_1))
                pushLink(LinkAnnotation.Clickable(tag = stringResource(R.string.sharing_link_label)) {
                    coroutineScope.launch {
                        clipboard.setClipEntry(ClipEntry(ClipData.newRawUri("URL", uri.toUri())))
                        // starting from android 13, a visual feedback is already provided by the system
                        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.S_V2) {
                            Toast.makeText(context, clipboardToastText, Toast.LENGTH_SHORT).show()
                        }
                    }
                })
                withStyle(style = SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                    append(stringResource(R.string.share_pad_instructions_2))
                }
                pop()
                append(".")
            }
            Text(
                text = s,
                modifier = Modifier.align(Alignment.CenterHorizontally),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
