package com.phoebe.app.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.sp

private val countTransitionSpec: AnimatedContentTransitionScope<Int>.() -> ContentTransform = {
    (slideInVertically(animationSpec = tween(200)) { it / 3 } + fadeIn(tween(200))) togetherWith
        (slideOutVertically(animationSpec = tween(160)) { -it / 3 } + fadeOut(tween(160)))
}

@Composable
fun PlaylistTrackSummaryLine(
    totalCount: Int,
    visibleCount: Int,
    searchQuery: String,
    modifier: Modifier = Modifier,
) {
    val searchActive = searchQuery.isNotBlank()
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        AnimatedContent(
            targetState = totalCount,
            transitionSpec = countTransitionSpec,
            label = "playlist-total-count",
        ) { count ->
            Text(
                "$count ${if (count == 1) "song" else "songs"}",
                color = PhoebeUi.secondaryText,
                fontSize = 14.sp,
            )
        }
        AnimatedVisibility(
            visible = searchActive,
            enter = fadeIn(tween(180)) + expandHorizontally(tween(200)),
            exit = fadeOut(tween(140)) + shrinkHorizontally(tween(160)),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(" · ", color = PhoebeUi.mutedText, fontSize = 14.sp)
                AnimatedContent(
                    targetState = visibleCount,
                    transitionSpec = countTransitionSpec,
                    label = "playlist-filter-count",
                ) { count ->
                    Text(
                        "$count ${if (count == 1) "result" else "results"}",
                        color = PhoebeUi.secondaryText,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}
