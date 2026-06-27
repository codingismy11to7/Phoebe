package com.phoebe.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun LibraryTopBar(searchQuery: String, onSearchQuery: (String) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(
                start = PhoebeDesktopLayout.contentStart,
                top = PhoebeDesktopLayout.contentTop,
                end = PhoebeDesktopLayout.contentEnd,
                bottom = 16.dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End,
    ) {
        SearchPill(searchQuery, onSearchQuery, Modifier.width(PhoebeDesktopLayout.searchWidth))
    }
}
