package com.phoebe.app.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.resources.painterResource
import phoebe.ui.core.generated.resources.Res
import phoebe.ui.core.generated.resources.phoebe_bird

/** Phoebe brand mark — the foreground bird from the app icon shown beside the wordmark. */
@Composable
fun BrandMark(
    size: Dp,
    modifier: Modifier = Modifier,
) {
    Image(
        painter = painterResource(Res.drawable.phoebe_bird),
        contentDescription = "Phoebe",
        modifier = modifier.size(size),
    )
}
