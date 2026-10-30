package com.ltq.count.ui.quick

import android.view.WindowManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltq.count.R
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.model.EventSource
import com.ltq.count.ui.components.AnimatedValueText
import com.ltq.count.ui.components.CircleIconButton
import com.ltq.count.ui.components.StepEntryDialog
import com.ltq.count.ui.theme.extras
import com.ltq.count.ui.util.CountFormat
import com.ltq.count.ui.util.findActivity
import kotlinx.coroutines.delay

@Composable
fun QuickCountScreen(
    counterId: String,
    fromShortcut: Boolean,
    repository: CounterRepository,
    onExit: () -> Unit,
    viewModel: QuickCountViewModel = viewModel(
        key = counterId,
        factory = quickCountViewModelFactory(counterId, repository),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val source = if (fromShortcut) EventSource.SHORTCUT else EventSource.MANUAL
    var notice by remember { mutableStateOf<String?>(null) }
    var showStepDialog by remember { mutableStateOf(false) }

    ImmersiveQuickCount()

    DisposableEffect(source) {
        QuickKeyBus.adjust = { direction -> viewModel.adjust(direction, source) }
        onDispose { QuickKeyBus.adjust = null }
    }

    LaunchedEffect(Unit) {
        viewModel.effects.collect { effect ->
            when (effect) {
                is QuickEffect.Message -> notice = context.getString(
                    effect.messageRes,
                    *effect.args.toTypedArray(),
                )
            }
        }
    }
    LaunchedEffect(notice) {
        if (notice != null) {
            delay(2_400)
            notice = null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(24.dp),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleIconButton(
                    icon = Icons.Rounded.Close,
                    contentDescription = stringResource(R.string.quick_exit),
                    onClick = onExit,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = { showStepDialog = true }) {
                    Text(
                        text = stringResource(R.string.quick_step_label, state.step),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Spacer(Modifier.weight(0.7f))

            val summary = state.summary
            if (state.loading) {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (summary == null) {
                Text(
                    text = stringResource(R.string.value_counter_missing),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                )
            } else {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = summary.counter.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(16.dp))
                    AnimatedValueText(
                        value = summary.currentValue,
                        style = MaterialTheme.typography.displayLarge.copy(fontSize = 88.sp, lineHeight = 92.sp),
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(
                            R.string.quick_session,
                            CountFormat.signedLong(state.sessionDelta),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            if (!state.loading && state.summary != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    QuickAdjustButton(
                        icon = Icons.Rounded.Remove,
                        contentDescription = stringResource(R.string.quick_decrease),
                        background = MaterialTheme.colorScheme.surfaceContainerHighest,
                        tint = MaterialTheme.colorScheme.onSurface,
                        onClick = { viewModel.adjust(-1, source) },
                    )
                    Spacer(Modifier.size(40.dp))
                    QuickAdjustButton(
                        icon = Icons.Rounded.Add,
                        contentDescription = stringResource(R.string.quick_increase),
                        background = MaterialTheme.colorScheme.primaryContainer,
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = { viewModel.adjust(1, source) },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                text = notice ?: stringResource(R.string.quick_volume_hint),
                style = MaterialTheme.typography.labelSmall,
                color = if (notice != null) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }

    if (showStepDialog) {
        StepEntryDialog(
            initialStep = state.step,
            onDismiss = { showStepDialog = false },
            onConfirm = { step ->
                viewModel.setStep(step)
                showStepDialog = false
            },
        )
    }
}

@Composable
private fun QuickAdjustButton(
    icon: ImageVector,
    contentDescription: String,
    background: androidx.compose.ui.graphics.Color,
    tint: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    val extras = MaterialTheme.extras
    Box(
        modifier = Modifier
            .size(112.dp)
            .shadow(
                elevation = 14.dp,
                shape = CircleShape,
                ambientColor = extras.cardShadowAmbient,
                spotColor = extras.cardShadowSpot,
                clip = false,
            )
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(40.dp),
        )
    }
}

@Composable
private fun ImmersiveQuickCount() {
    val view = LocalView.current
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        controller?.hide(WindowInsetsCompat.Type.systemBars())
        controller?.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose {
            controller?.show(WindowInsetsCompat.Type.systemBars())
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }
}
