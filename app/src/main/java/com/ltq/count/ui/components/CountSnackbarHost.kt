package com.ltq.count.ui.components

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance

/**
 * Snackbars use a soft raised surface in dark mode instead of the default bright inverse
 * surface, keeping the dark layer hierarchy calm while retaining contrast.
 */
@Composable
fun CountSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    SnackbarHost(hostState = hostState, modifier = modifier) { data ->
        Snackbar(
            snackbarData = data,
            shape = MaterialTheme.shapes.medium,
            containerColor = if (isDark) {
                MaterialTheme.colorScheme.surfaceContainerHighest
            } else {
                MaterialTheme.colorScheme.inverseSurface
            },
            contentColor = if (isDark) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.inverseOnSurface
            },
            actionContentColor = if (isDark) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.inversePrimary
            },
        )
    }
}
