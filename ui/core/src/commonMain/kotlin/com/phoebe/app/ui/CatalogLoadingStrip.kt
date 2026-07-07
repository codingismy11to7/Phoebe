package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp

@Composable
fun CatalogLoadingStrip(modifier: Modifier = Modifier) {
    val hasContent = LocalCatalogHasContent.current
    val syncState = LocalCatalogSyncState.current
    val message = syncState.message ?: if (hasContent) "Syncing..." else "Loading your library..."
    val detail = syncState.detail
    val progress = syncState.progress
    val animatedProgress by animateFloatAsState(
        targetValue = progress?.coerceIn(0f, 1f) ?: 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "catalog-sync-progress",
    )
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (progress != null) {
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = PhoebeUi.accentLight,
                trackColor = PhoebeUi.progressTrack,
            )
        } else {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(999.dp)),
                color = PhoebeUi.accentLight,
                trackColor = PhoebeUi.progressTrack,
            )
        }
        AnimatedContent(
            targetState = syncState.phase to message,
            transitionSpec = {
                fadeIn(tween(180)) togetherWith fadeOut(tween(120))
            },
            label = "catalog-sync-message",
        ) { (_, animatedMessage) ->
            Text(
                animatedMessage,
                color = PhoebeUi.mutedText,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.06.em,
            )
        }
        AnimatedVisibility(
            visible = !detail.isNullOrBlank(),
            enter = fadeIn(tween(180)),
            exit = fadeOut(tween(120)),
        ) {
            Text(
                detail.orEmpty(),
                color = PhoebeUi.mutedText.copy(alpha = 0.85f),
                fontSize = 10.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
