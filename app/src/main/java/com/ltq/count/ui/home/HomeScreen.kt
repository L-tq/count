package com.ltq.count.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltq.count.R
import com.ltq.count.data.prefs.CardSize
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.model.CounterSummary
import com.ltq.count.shortcut.ShortcutHelper
import com.ltq.count.ui.components.AnimatedValueText
import com.ltq.count.ui.components.CircleIconButton
import com.ltq.count.ui.components.CountSnackbarHost
import com.ltq.count.ui.components.ConfirmDialog
import com.ltq.count.ui.components.EmptyState
import com.ltq.count.ui.components.Pill
import com.ltq.count.ui.components.SectionLabel
import com.ltq.count.ui.components.StepEntryDialog
import com.ltq.count.ui.components.TagChips
import com.ltq.count.ui.components.TagManagerDialog
import com.ltq.count.ui.components.TagPickerDialog
import com.ltq.count.ui.theme.extras
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    repository: CounterRepository,
    cardSize: CardSize,
    onOpenCounter: (String) -> Unit,
    onEditCounter: (String) -> Unit,
    onNewCounter: () -> Unit,
    onOpenSettings: () -> Unit,
    onQuickCount: (String) -> Unit,
    viewModel: HomeViewModel = viewModel(factory = homeViewModelFactory(repository)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScopeCompat()

    var menuTarget by remember { mutableStateOf<CounterSummary?>(null) }
    var tagTarget by remember { mutableStateOf<CounterSummary?>(null) }
    var deleteTarget by remember { mutableStateOf<CounterSummary?>(null) }
    var customStepTarget by remember { mutableStateOf<CounterSummary?>(null) }
    var tagManagerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is HomeEffect.ValueChanged -> {
                    val result = snackbarHostState.showSnackbar(
                        message = context.getString(R.string.snackbar_value, effect.newValue),
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) {
                        viewModel.undo(effect.counterId, effect.eventId)
                    }
                }
                is HomeEffect.Message -> {
                    val args = effect.args.toTypedArray()
                    snackbarHostState.showSnackbar(context.getString(effect.messageRes, *args))
                }
            }
        }
    }

    // Local drag order for the normal (non pinned) section.
    val orderedIds = remember { mutableStateListOf<String>() }
    val heights = remember { mutableStateMapOf<String, Float>() }
    var draggingId by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val normalIds = state.normal.map { it.counter.id }
    LaunchedEffect(normalIds) {
        if (draggingId == null) {
            orderedIds.clear()
            orderedIds.addAll(normalIds)
        }
    }
    val byId = state.normal.associateBy { it.counter.id }
    val orderedNormal = orderedIds.mapNotNull { byId[it] }
    // Reordering while a tag filter hides counters would renumber only the visible subset.
    val reorderEnabled = state.selectedTagId == null

    fun beginDrag(id: String) {
        draggingId = id
        dragOffset = 0f
    }

    fun dragBy(id: String, dy: Float) {
        dragOffset += dy
        val index = orderedIds.indexOf(id)
        if (index < 0) return
        val height = (heights[id] ?: 150f).coerceAtLeast(80f)
        if (dragOffset > height * 0.55f && index < orderedIds.lastIndex) {
            orderedIds.add(index + 1, orderedIds.removeAt(index))
            dragOffset -= height
        } else if (dragOffset < -height * 0.55f && index > 0) {
            orderedIds.add(index - 1, orderedIds.removeAt(index))
            dragOffset += height
        }
    }

    fun endDrag() {
        if (draggingId != null) viewModel.reorder(orderedIds.toList())
        draggingId = null
        dragOffset = 0f
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CountSnackbarHost(snackbarHostState) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNewCounter,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.home_new_counter)) },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "header") {
                HomeHeader(onOpenSettings = onOpenSettings)
            }

            if (state.tags.isNotEmpty()) {
                item(key = "filters") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Pill(
                                label = stringResource(R.string.home_tag_filter_all),
                                selected = state.selectedTagId == null,
                                onClick = { viewModel.selectTag(null) },
                            )
                            state.tags.forEach { tag ->
                                Pill(
                                    label = tag.name,
                                    selected = state.selectedTagId == tag.id,
                                    onClick = {
                                        viewModel.selectTag(if (state.selectedTagId == tag.id) null else tag.id)
                                    },
                                )
                            }
                        }
                        TextButton(
                            onClick = { tagManagerOpen = true },
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.home_tag_manage),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
            }

            if (state.loading) {
                item(key = "loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 64.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                }
            } else if (state.pinned.isEmpty() && state.normal.isEmpty()) {
                item(key = "empty") {
                    if (state.selectedTagId != null) {
                        EmptyState(
                            title = stringResource(R.string.home_counters_empty_filtered),
                            body = stringResource(R.string.home_empty_body),
                        )
                    } else {
                        EmptyState(
                            title = stringResource(R.string.home_empty_title),
                            body = stringResource(R.string.home_empty_body),
                        )
                    }
                }
            } else {
                if (state.pinned.isNotEmpty()) {
                    item(key = "pinned-label") {
                        SectionLabel(
                            text = stringResource(R.string.home_pinned_section),
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                    items(state.pinned, key = { "pinned-${it.counter.id}" }) { summary ->
                        PinnedRow(
                            summary = summary,
                            step = state.sessionSteps[summary.counter.id] ?: summary.counter.defaultStep,
                            cardSize = cardSize,
                            menuOpen = menuTarget?.counter?.id == summary.counter.id,
                            onOpen = { onOpenCounter(summary.counter.id) },
                            onLongPress = { menuTarget = summary },
                            onDismissMenu = { menuTarget = null },
                            onEdit = {
                                menuTarget = null
                                onEditCounter(summary.counter.id)
                            },
                            onTogglePin = {
                                menuTarget = null
                                viewModel.togglePin(summary)
                            },
                            onTags = {
                                menuTarget = null
                                tagTarget = summary
                            },
                            onShortcut = {
                                menuTarget = null
                                val requested = ShortcutHelper.requestPin(
                                    context,
                                    summary.counter.id,
                                    summary.counter.name,
                                )
                                scope.launch {
                                    snackbarHostState.showSnackbar(
                                        context.getString(
                                            if (requested) {
                                                R.string.home_shortcut_created
                                            } else {
                                                R.string.home_shortcut_failed
                                            },
                                        ),
                                    )
                                }
                            },
                            onDelete = {
                                menuTarget = null
                                deleteTarget = summary
                            },
                            onAdjust = { direction -> viewModel.adjust(summary, direction) },
                            onStepSelected = { step -> viewModel.setSessionStep(summary.counter.id, step) },
                            onCustomStep = { customStepTarget = summary },
                            onQuickCount = {
                                menuTarget = null
                                onQuickCount(summary.counter.id)
                            },
                        )
                    }
                }

                if (state.normal.isNotEmpty()) {
                    if (state.pinned.isNotEmpty() || state.normal.size > 1) {
                        item(key = "all-label") {
                            Column(modifier = Modifier.padding(top = 8.dp)) {
                                if (state.pinned.isNotEmpty()) {
                                    SectionLabel(text = stringResource(R.string.home_all_section))
                                }
                                if (state.normal.size > 1 && reorderEnabled) {
                                    Text(
                                        text = stringResource(R.string.home_reorder_hint),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        modifier = Modifier.padding(top = 2.dp),
                                    )
                                }
                            }
                        }
                    }
                    items(orderedNormal, key = { it.counter.id }) { summary ->
                        val id = summary.counter.id
                        val isDragging = draggingId == id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                }
                                .onGloballyPositioned { coords ->
                                    heights[id] = coords.size.height.toFloat()
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(36.dp)
                                    .height(72.dp)
                                    .then(
                                        if (reorderEnabled) {
                                            Modifier.pointerInput(id) {
                                                detectDragGesturesAfterLongPress(
                                                    onDragStart = { beginDrag(id) },
                                                    onDragEnd = { endDrag() },
                                                    onDragCancel = { endDrag() },
                                                    onDrag = { change, amount ->
                                                        change.consume()
                                                        dragBy(id, amount.y)
                                                    },
                                                )
                                            }
                                        } else {
                                            Modifier
                                        },
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (reorderEnabled) {
                                    Icon(
                                        imageVector = Icons.Rounded.DragIndicator,
                                        contentDescription = stringResource(R.string.home_drag_handle),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                            Spacer(Modifier.width(2.dp))
                            CounterCard(
                                summary = summary,
                                step = state.sessionSteps[id] ?: summary.counter.defaultStep,
                                cardSize = cardSize,
                                modifier = Modifier.weight(1f),
                                menuOpen = menuTarget?.counter?.id == id,
                                onOpen = { onOpenCounter(id) },
                                onLongPress = { menuTarget = summary },
                                onDismissMenu = { menuTarget = null },
                                onEdit = {
                                    menuTarget = null
                                    onEditCounter(id)
                                },
                                onTogglePin = {
                                    menuTarget = null
                                    viewModel.togglePin(summary)
                                },
                                onTags = {
                                    menuTarget = null
                                    tagTarget = summary
                                },
                                onShortcut = {
                                    menuTarget = null
                                    val requested = ShortcutHelper.requestPin(context, id, summary.counter.name)
                                    scope.launch {
                                        snackbarHostState.showSnackbar(
                                            context.getString(
                                                if (requested) {
                                                    R.string.home_shortcut_created
                                                } else {
                                                    R.string.home_shortcut_failed
                                                },
                                            ),
                                        )
                                    }
                                },
                                onDelete = {
                                    menuTarget = null
                                    deleteTarget = summary
                                },
                                onAdjust = { direction -> viewModel.adjust(summary, direction) },
                                onStepSelected = { step -> viewModel.setSessionStep(id, step) },
                                onCustomStep = { customStepTarget = summary },
                                onQuickCount = {
                                    menuTarget = null
                                    onQuickCount(id)
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    deleteTarget?.let { target ->
        ConfirmDialog(
            title = stringResource(R.string.home_delete_title, target.counter.name),
            body = stringResource(R.string.home_delete_body),
            confirmLabel = stringResource(R.string.action_delete),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                viewModel.delete(target.counter.id)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }

    tagTarget?.let { target ->
        TagPickerDialog(
            allTags = state.tags,
            initialSelected = target.tags.map { it.id },
            onDismiss = { tagTarget = null },
            onCreateTag = { name, onResult -> viewModel.createTag(name, onResult) },
            onSave = { ids ->
                viewModel.setTags(target.counter.id, ids)
                tagTarget = null
            },
        )
    }

    customStepTarget?.let { target ->
        StepEntryDialog(
            initialStep = state.sessionSteps[target.counter.id] ?: target.counter.defaultStep,
            onDismiss = { customStepTarget = null },
            onConfirm = { step ->
                viewModel.setSessionStep(target.counter.id, step)
                customStepTarget = null
            },
        )
    }

    if (tagManagerOpen) {
        TagManagerDialog(
            tags = state.tags,
            onDismiss = { tagManagerOpen = false },
            onDelete = { tag ->
                viewModel.deleteTag(tag)
                tagManagerOpen = false
            },
        )
    }
}

@Composable
private fun HomeHeader(onOpenSettings: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = stringResource(R.string.brand_tagline),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
        CircleIconButton(
            icon = Icons.Rounded.Settings,
            contentDescription = stringResource(R.string.a11y_settings),
            onClick = onOpenSettings,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun PinnedRow(
    summary: CounterSummary,
    step: Int,
    cardSize: CardSize,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onTags: () -> Unit,
    onShortcut: () -> Unit,
    onDelete: () -> Unit,
    onAdjust: (Int) -> Unit,
    onStepSelected: (Int) -> Unit,
    onCustomStep: () -> Unit,
    onQuickCount: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .width(36.dp)
                .height(72.dp),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.PushPin,
                contentDescription = stringResource(R.string.a11y_pinned),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                modifier = Modifier.size(16.dp),
            )
        }
        Spacer(Modifier.width(2.dp))
        CounterCard(
            summary = summary,
            step = step,
            cardSize = cardSize,
            modifier = Modifier.weight(1f),
            menuOpen = menuOpen,
            onOpen = onOpen,
            onLongPress = onLongPress,
            onDismissMenu = onDismissMenu,
            onEdit = onEdit,
            onTogglePin = onTogglePin,
            onTags = onTags,
            onShortcut = onShortcut,
            onDelete = onDelete,
            onAdjust = onAdjust,
            onStepSelected = onStepSelected,
            onCustomStep = onCustomStep,
            onQuickCount = onQuickCount,
        )
    }
}

private data class CardMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val nameStyle: TextStyle,
    val valueStyle: TextStyle,
    val nameValueGap: Dp,
    val metadataGap: Dp,
    val actionsGap: Dp,
    val buttonSize: Dp,
    val buttonIconSize: Dp,
    val buttonGap: Dp,
)

@Composable
private fun cardMetrics(size: CardSize): CardMetrics {
    val typography = MaterialTheme.typography
    return when (size) {
        CardSize.COMPACT -> CardMetrics(
            horizontalPadding = 12.dp,
            verticalPadding = 10.dp,
            nameStyle = typography.titleMedium,
            valueStyle = typography.headlineLarge,
            nameValueGap = 0.dp,
            metadataGap = 2.dp,
            actionsGap = 0.dp,
            buttonSize = 48.dp,
            buttonIconSize = 18.dp,
            buttonGap = 8.dp,
        )
        CardSize.STANDARD -> CardMetrics(
            horizontalPadding = 18.dp,
            verticalPadding = 16.dp,
            nameStyle = typography.titleLarge,
            valueStyle = typography.displayMedium,
            nameValueGap = 2.dp,
            metadataGap = 4.dp,
            actionsGap = 12.dp,
            buttonSize = 48.dp,
            buttonIconSize = 20.dp,
            buttonGap = 10.dp,
        )
        CardSize.COMFORTABLE -> CardMetrics(
            horizontalPadding = 22.dp,
            verticalPadding = 20.dp,
            nameStyle = typography.titleLarge,
            valueStyle = typography.displayLarge,
            nameValueGap = 4.dp,
            metadataGap = 6.dp,
            actionsGap = 16.dp,
            buttonSize = 56.dp,
            buttonIconSize = 24.dp,
            buttonGap = 12.dp,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CounterCard(
    summary: CounterSummary,
    step: Int,
    cardSize: CardSize,
    modifier: Modifier = Modifier,
    menuOpen: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onDismissMenu: () -> Unit,
    onEdit: () -> Unit,
    onTogglePin: () -> Unit,
    onTags: () -> Unit,
    onShortcut: () -> Unit,
    onDelete: () -> Unit,
    onAdjust: (Int) -> Unit,
    onStepSelected: (Int) -> Unit,
    onCustomStep: () -> Unit,
    onQuickCount: () -> Unit,
) {
    val extras = MaterialTheme.extras
    val metrics = cardMetrics(cardSize)
    val name = summary.counter.name
    val context = LocalContext.current
    Box(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(
                    elevation = 10.dp,
                    shape = MaterialTheme.shapes.large,
                    ambientColor = extras.cardShadowAmbient,
                    spotColor = extras.cardShadowSpot,
                    clip = false,
                )
                .clip(MaterialTheme.shapes.large)
                .background(MaterialTheme.colorScheme.surfaceContainer)
                .combinedClickable(
                    onClick = onOpen,
                    onLongClick = onLongPress,
                    role = Role.Button,
                    onClickLabel = stringResource(R.string.home_card_open, name),
                    onLongClickLabel = stringResource(R.string.action_more),
                )
                .padding(
                    horizontal = metrics.horizontalPadding,
                    vertical = metrics.verticalPadding,
                ),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = name,
                    style = metrics.nameStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Box {
                    CircleIconButton(
                        icon = Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.home_card_more, name),
                        onClick = onLongPress,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    DropdownMenu(
                        expanded = menuOpen,
                        onDismissRequest = onDismissMenu,
                        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_card_menu_edit)) },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = onEdit,
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        if (summary.counter.pinned) {
                                            R.string.home_card_menu_unpin
                                        } else {
                                            R.string.home_card_menu_pin
                                        },
                                    ),
                                )
                            },
                            leadingIcon = { Icon(Icons.Rounded.PushPin, contentDescription = null) },
                            onClick = onTogglePin,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_card_menu_tags)) },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Label, contentDescription = null) },
                            onClick = onTags,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.home_card_menu_shortcut)) },
                            leadingIcon = { Icon(Icons.Rounded.Share, contentDescription = null) },
                            onClick = onShortcut,
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.a11y_quick_count)) },
                            leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                            onClick = onQuickCount,
                        )
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(R.string.home_card_menu_delete),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Rounded.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                )
                            },
                            onClick = onDelete,
                        )
                    }
                }
            }
            Spacer(Modifier.height(metrics.nameValueGap))
            if (cardSize == CardSize.COMPACT) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AnimatedValueText(
                        value = summary.currentValue,
                        style = metrics.valueStyle,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.weight(1f),
                        contentDescription = context.getString(
                            R.string.a11y_current_value,
                            summary.currentValue,
                        ),
                    )
                    AdjustButtons(
                        name = name,
                        onAdjust = onAdjust,
                        onStepSelected = onStepSelected,
                        onCustomStep = onCustomStep,
                        metrics = metrics,
                    )
                }
            } else {
                AnimatedValueText(
                    value = summary.currentValue,
                    style = metrics.valueStyle,
                    color = MaterialTheme.colorScheme.primary,
                    contentDescription = context.getString(
                        R.string.a11y_current_value,
                        summary.currentValue,
                    ),
                )
            }
            Spacer(Modifier.height(metrics.metadataGap))
            Column {
                if (summary.tags.isNotEmpty()) {
                    TagChips(tagNames = summary.tags.map { it.name })
                    Spacer(Modifier.height(metrics.metadataGap))
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.count_events, summary.eventCount),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (cardSize == CardSize.COMPACT && step != summary.counter.defaultStep) {
                        Text(
                            text = " · " + stringResource(R.string.quick_step_label, step),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (cardSize != CardSize.COMPACT) {
                Spacer(Modifier.height(metrics.actionsGap))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (step != summary.counter.defaultStep) {
                        Text(
                            text = stringResource(R.string.quick_step_label, step),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    AdjustButtons(
                        name = name,
                        onAdjust = onAdjust,
                        onStepSelected = onStepSelected,
                        onCustomStep = onCustomStep,
                        metrics = metrics,
                    )
                }
            }
        }
    }
}

@Composable
private fun AdjustButtons(
    name: String,
    onAdjust: (Int) -> Unit,
    onStepSelected: (Int) -> Unit,
    onCustomStep: () -> Unit,
    metrics: CardMetrics,
    modifier: Modifier = Modifier,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        AdjustButton(
            icon = Icons.Rounded.Remove,
            contentDescription = stringResource(R.string.home_card_subtract, name),
            onTap = { onAdjust(-1) },
            onStepSelected = onStepSelected,
            onCustomStep = onCustomStep,
            buttonSize = metrics.buttonSize,
            iconSize = metrics.buttonIconSize,
        )
        Spacer(Modifier.width(metrics.buttonGap))
        AdjustButton(
            icon = Icons.Rounded.Add,
            contentDescription = stringResource(R.string.home_card_add, name),
            onTap = { onAdjust(1) },
            onStepSelected = onStepSelected,
            onCustomStep = onCustomStep,
            buttonSize = metrics.buttonSize,
            iconSize = metrics.buttonIconSize,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AdjustButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onTap: () -> Unit,
    onStepSelected: (Int) -> Unit,
    onCustomStep: () -> Unit,
    buttonSize: Dp,
    iconSize: Dp,
) {
    var menuOpen by remember { mutableStateOf(false) }
    Box {
        Box(
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .combinedClickable(
                    onClick = onTap,
                    onLongClick = { menuOpen = true },
                    role = Role.Button,
                    onLongClickLabel = stringResource(R.string.a11y_step_button),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(iconSize),
            )
        }
        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            listOf(1, 5, 10).forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_step_option, option)) },
                    onClick = {
                        menuOpen = false
                        onStepSelected(option)
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.home_step_custom_title)) },
                onClick = {
                    menuOpen = false
                    onCustomStep()
                },
            )
        }
    }
}

@Composable
private fun rememberCoroutineScopeCompat() = rememberCoroutineScope()
