package com.ltq.count.ui.detail

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Widgets
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltq.count.R
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.EventFilter
import com.ltq.count.domain.NoteFilter
import com.ltq.count.domain.SignFilter
import com.ltq.count.domain.TimeRangeFilter
import com.ltq.count.domain.heatmap.HeatmapGranularity
import com.ltq.count.domain.heatmap.HeatmapMetric
import com.ltq.count.domain.model.CountEvent
import com.ltq.count.domain.model.EventSource
import com.ltq.count.ui.components.AnimatedValueText
import com.ltq.count.ui.components.CircleIconButton
import com.ltq.count.ui.components.CountSnackbarHost
import com.ltq.count.ui.components.ConfirmDialog
import com.ltq.count.ui.components.EmptyState
import com.ltq.count.ui.components.Pill
import com.ltq.count.ui.components.SectionLabel
import com.ltq.count.ui.components.TagChips
import com.ltq.count.ui.theme.extras
import com.ltq.count.ui.util.CountFormat
import kotlinx.coroutines.flow.collectLatest
import java.time.ZoneId

@Composable
fun DetailScreen(
    counterId: String,
    repository: CounterRepository,
    onBack: () -> Unit,
    onEdit: (String) -> Unit,
    onQuickCount: (String) -> Unit,
    viewModel: DetailViewModel = viewModel(factory = detailViewModelFactory(counterId, repository)),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var menuOpen by remember { mutableStateOf(false) }
    var showResetConfirm by remember { mutableStateOf(false) }
    var showFilter by remember { mutableStateOf(false) }
    var noteTarget by remember { mutableStateOf<CountEvent?>(null) }
    var actionTarget by remember { mutableStateOf<CountEvent?>(null) }
    var rangeUndoTarget by remember { mutableStateOf<CountEvent?>(null) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is DetailEffect.ValueChanged -> {
                    val message = if (effect.reset) {
                        context.getString(R.string.snackbar_reset)
                    } else {
                        context.getString(R.string.snackbar_value, effect.newValue)
                    }
                    val result = snackbarHostState.showSnackbar(
                        message = message,
                        actionLabel = context.getString(R.string.action_undo),
                        duration = SnackbarDuration.Short,
                    )
                    if (result == SnackbarResult.ActionPerformed) viewModel.undoEvent(effect.eventId)
                }
                is DetailEffect.Message -> {
                    snackbarHostState.showSnackbar(
                        context.getString(effect.messageRes, *effect.args.toTypedArray()),
                    )
                }
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CountSnackbarHost(snackbarHostState) },
        topBar = {
            DetailTopBar(
                name = state.counter?.counter?.name.orEmpty(),
                tagNames = state.counter?.tags?.map { it.name }.orEmpty(),
                pinned = state.counter?.counter?.pinned == true,
                menuOpen = menuOpen,
                onMenuOpenChange = { menuOpen = it },
                onBack = onBack,
                onEdit = { menuOpen = false; onEdit(counterId) },
                onQuickCount = { menuOpen = false; onQuickCount(counterId) },
                onUndoLatest = { menuOpen = false; viewModel.undoLatest() },
            )
        },
    ) { innerPadding ->
        val counter = state.counter
        if (!state.loading && counter == null) {
            EmptyState(
                title = stringResource(R.string.value_counter_missing),
                body = stringResource(R.string.home_empty_body),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }

        LazyColumn(
            state = rememberLazyListState(),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (counter != null) {
                item(key = "value") {
                    ValueBlock(
                        value = counter.currentValue,
                        eventCount = counter.eventCount,
                        onReset = { showResetConfirm = true },
                    )
                }

                state.heatmap?.let { heatmap ->
                    item(key = "heatmap") {
                        HeatmapCard(
                            heatmap = heatmap,
                            onMetricChange = viewModel::setMetric,
                            onGranularityChange = viewModel::setGranularity,
                        )
                    }
                }

                item(key = "events-header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.detail_events_title),
                                style = MaterialTheme.typography.headlineSmall,
                                color = MaterialTheme.colorScheme.onBackground,
                            )
                            Text(
                                text = stringResource(R.string.detail_events_count, state.filteredEvents.size),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        CircleIconButton(
                            icon = Icons.Rounded.FilterList,
                            contentDescription = stringResource(R.string.detail_filter),
                            onClick = { showFilter = true },
                            tint = if (state.filter.isDefault) {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }

                if (state.filteredEvents.isEmpty()) {
                    item(key = "events-empty") {
                        EmptyState(
                            title = stringResource(
                                if (state.allEvents.isEmpty()) R.string.detail_empty_all else R.string.detail_empty_filtered,
                            ),
                            body = stringResource(R.string.detail_events_title),
                        )
                    }
                } else {
                    items(state.filteredEvents, key = { it.id }) { event ->
                        EventRow(
                            event = event,
                            zone = state.zoneId,
                            onClick = { noteTarget = event },
                            onLongClick = { actionTarget = event },
                        )
                    }
                }
            }
        }
    }

    if (showResetConfirm && state.counter != null) {
        val counter = state.counter!!
        val delta = counter.counter.initialValue.toLong() - counter.currentValue
        ConfirmDialog(
            title = stringResource(R.string.detail_reset_confirm_title),
            body = stringResource(
                R.string.detail_reset_confirm_body,
                CountFormat.signedLong(delta),
            ),
            confirmLabel = stringResource(R.string.detail_reset_confirm_action),
            cancelLabel = stringResource(R.string.action_cancel),
            onConfirm = {
                showResetConfirm = false
                viewModel.resetToInitial()
            },
            onDismiss = { showResetConfirm = false },
        )
    }

    if (showFilter) {
        FilterDialog(
            filter = state.filter,
            onApply = {
                viewModel.setFilter(it)
                showFilter = false
            },
            onDismiss = { showFilter = false },
        )
    }

    noteTarget?.let { event ->
        NoteDialog(
            initialNote = event.note.orEmpty(),
            onDismiss = { noteTarget = null },
            onSave = { note ->
                viewModel.updateNote(event.id, note)
                noteTarget = null
            },
        )
    }

    actionTarget?.let { event ->
        val newerCount = state.allEvents.indexOfFirst { it.id == event.id }.coerceAtLeast(0) + 1
        AlertDialog(
            onDismissRequest = { actionTarget = null },
            title = { Text(stringResource(R.string.detail_event_actions_title), style = MaterialTheme.typography.headlineSmall) },
            text = {
                Column {
                    Text(
                        text = CountFormat.fullTime(event.timestamp, state.zoneId) + "  " +
                            CountFormat.signed(event.delta),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            actionTarget = null
                            noteTarget = event
                        },
                    ) { Text(stringResource(R.string.detail_note_dialog_title)) }
                    TextButton(
                        onClick = {
                            actionTarget = null
                            rangeUndoTarget = event
                        },
                    ) {
                        Text(
                            text = stringResource(R.string.detail_event_undo_range, newerCount),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { actionTarget = null }) {
                    Text(stringResource(R.string.action_close))
                }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }

    rangeUndoTarget?.let { event ->
        val newerCount = state.allEvents.indexOfFirst { it.id == event.id }.coerceAtLeast(0) + 1
        ConfirmDialog(
            title = stringResource(R.string.detail_event_undo_range_title, newerCount),
            body = stringResource(R.string.detail_event_undo_range_body),
            confirmLabel = stringResource(R.string.detail_event_undo_range_confirm),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                rangeUndoTarget = null
                viewModel.undoFrom(event.id)
            },
            onDismiss = { rangeUndoTarget = null },
        )
    }
}

@Composable
private fun DetailTopBar(
    name: String,
    tagNames: List<String>,
    pinned: Boolean,
    menuOpen: Boolean,
    onMenuOpenChange: (Boolean) -> Unit,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onQuickCount: () -> Unit,
    onUndoLatest: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleIconButton(
            icon = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = stringResource(R.string.a11y_back),
            onClick = onBack,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 4.dp),
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (pinned || tagNames.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (pinned) {
                        Text(
                            text = stringResource(R.string.detail_pinned_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        if (tagNames.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                        }
                    }
                    TagChips(tagNames = tagNames)
                }
            }
        }
        Box {
            CircleIconButton(
                icon = Icons.Rounded.MoreVert,
                contentDescription = stringResource(R.string.action_more),
                onClick = { onMenuOpenChange(true) },
            )
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { onMenuOpenChange(false) },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.home_card_menu_edit)) },
                    leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                    onClick = onEdit,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.a11y_quick_count)) },
                    leadingIcon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = onQuickCount,
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.detail_undo_latest)) },
                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                    onClick = onUndoLatest,
                )
            }
        }
    }
}

@Composable
private fun ValueBlock(
    value: Long,
    eventCount: Int,
    onReset: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionLabel(text = stringResource(R.string.detail_value_label))
        Row(verticalAlignment = Alignment.Bottom) {
            AnimatedValueText(
                value = value,
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            TextButton(
                onClick = onReset,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Icon(
                    Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = stringResource(R.string.detail_reset),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = stringResource(R.string.count_events, eventCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun EventRow(
    event: CountEvent,
    zone: ZoneId,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val extras = MaterialTheme.extras
    val deltaColor = if (event.delta > 0) extras.positive else extras.negative
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
                onLongClickLabel = stringResource(R.string.detail_event_actions_title),
            )
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(modifier = Modifier.width(58.dp)) {
            Text(
                text = CountFormat.eventDate(event.timestamp, zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = CountFormat.eventClock(event.timestamp, zone),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.width(6.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = CountFormat.signed(event.delta),
                    style = MaterialTheme.typography.titleMedium,
                    color = deltaColor,
                )
                Spacer(Modifier.width(8.dp))
                EventSourceBadge(source = event.source)
            }
            event.note?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    text = note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun EventSourceBadge(source: EventSource) {
    val icon: ImageVector = when (source) {
        EventSource.MANUAL -> Icons.Rounded.TouchApp
        EventSource.WIDGET -> Icons.Rounded.Widgets
        EventSource.SHORTCUT -> Icons.Rounded.Bolt
        EventSource.RESET -> Icons.Rounded.RestartAlt
        EventSource.UNDO -> Icons.AutoMirrored.Rounded.Undo
    }
    val label = when (source) {
        EventSource.MANUAL -> R.string.detail_event_source_manual
        EventSource.WIDGET -> R.string.detail_event_source_widget
        EventSource.SHORTCUT -> R.string.detail_event_source_shortcut
        EventSource.RESET -> R.string.detail_event_source_reset
        EventSource.UNDO -> R.string.detail_event_source_undo
    }
    Icon(
        imageVector = icon,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.size(13.dp),
    )
    Spacer(Modifier.width(3.dp))
    Text(
        text = stringResource(label),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun NoteDialog(
    initialNote: String,
    onDismiss: () -> Unit,
    onSave: (String?) -> Unit,
) {
    var text by rememberSaveable(initialNote) { mutableStateOf(initialNote) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_note_dialog_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(CounterMath.MAX_NOTE_LENGTH + 1) },
                placeholder = { Text(stringResource(R.string.detail_note_hint)) },
                minLines = 3,
                maxLines = 6,
                shape = MaterialTheme.shapes.small,
                supportingText = {
                    Text(
                        text = "${text.length}/${CounterMath.MAX_NOTE_LENGTH}",
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(text.trim().takeIf { it.isNotEmpty() }) },
                enabled = text.length <= CounterMath.MAX_NOTE_LENGTH,
            ) { Text(stringResource(R.string.detail_note_save)) }
        },
        dismissButton = {
            Row {
                if (initialNote.isNotBlank()) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.detail_note_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FilterDialog(
    filter: EventFilter,
    onApply: (EventFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by remember { mutableStateOf(filter) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.detail_filter_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionLabel(text = stringResource(R.string.detail_filter_time))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    FilterPill(
                        label = stringResource(R.string.detail_time_all),
                        selected = draft.timeRange == TimeRangeFilter.ALL,
                    ) { draft = draft.copy(timeRange = TimeRangeFilter.ALL) }
                    FilterPill(
                        label = stringResource(R.string.detail_time_7),
                        selected = draft.timeRange == TimeRangeFilter.LAST_7_DAYS,
                    ) { draft = draft.copy(timeRange = TimeRangeFilter.LAST_7_DAYS) }
                    FilterPill(
                        label = stringResource(R.string.detail_time_30),
                        selected = draft.timeRange == TimeRangeFilter.LAST_30_DAYS,
                    ) { draft = draft.copy(timeRange = TimeRangeFilter.LAST_30_DAYS) }
                    FilterPill(
                        label = stringResource(R.string.detail_time_90),
                        selected = draft.timeRange == TimeRangeFilter.LAST_90_DAYS,
                    ) { draft = draft.copy(timeRange = TimeRangeFilter.LAST_90_DAYS) }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel(text = stringResource(R.string.detail_filter_sign))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    FilterPill(
                        label = stringResource(R.string.detail_sign_any),
                        selected = draft.sign == SignFilter.ANY,
                    ) { draft = draft.copy(sign = SignFilter.ANY) }
                    FilterPill(
                        label = stringResource(R.string.detail_sign_positive),
                        selected = draft.sign == SignFilter.POSITIVE,
                    ) { draft = draft.copy(sign = SignFilter.POSITIVE) }
                    FilterPill(
                        label = stringResource(R.string.detail_sign_negative),
                        selected = draft.sign == SignFilter.NEGATIVE,
                    ) { draft = draft.copy(sign = SignFilter.NEGATIVE) }
                }
                Spacer(Modifier.height(14.dp))
                SectionLabel(text = stringResource(R.string.detail_filter_note))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.padding(top = 6.dp),
                ) {
                    FilterPill(
                        label = stringResource(R.string.detail_note_any),
                        selected = draft.note == NoteFilter.ANY,
                    ) { draft = draft.copy(note = NoteFilter.ANY) }
                    FilterPill(
                        label = stringResource(R.string.detail_note_with),
                        selected = draft.note == NoteFilter.WITH_NOTE,
                    ) { draft = draft.copy(note = NoteFilter.WITH_NOTE) }
                    FilterPill(
                        label = stringResource(R.string.detail_note_without),
                        selected = draft.note == NoteFilter.WITHOUT_NOTE,
                    ) { draft = draft.copy(note = NoteFilter.WITHOUT_NOTE) }
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = { draft = EventFilter() }) {
                    Text(stringResource(R.string.detail_filter_reset))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onApply(draft) }) {
                Text(stringResource(R.string.action_apply))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
private fun FilterPill(label: String, selected: Boolean, onClick: () -> Unit) {
    Pill(label = label, selected = selected, onClick = onClick)
}
