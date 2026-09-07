package fr.jloc.shoppinglist.ui.screens.share_pad

import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import fr.jloc.shoppinglist.R
import fr.jloc.shoppinglist.ui.screens.SecondaryScreen
import qrcode.QRCode

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

    // TODO(ux): add a hint about how to scan the QR-code

    Box(Modifier.fillMaxSize() then modifier) {
        Image(
            modifier = Modifier
                .widthIn(0.dp, 400.dp)
                .heightIn(0.dp, 400.dp)
                .align(Alignment.Center)
                .padding(64.dp),
            painter = BitmapPainter(bitmap),
            contentDescription = stringResource(R.string.qr_code),
        )
    }
}
