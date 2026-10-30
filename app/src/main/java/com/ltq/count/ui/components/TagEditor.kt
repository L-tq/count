package com.ltq.count.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.ltq.count.R
import com.ltq.count.data.repo.TagWriteResult
import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.model.Tag
import com.ltq.count.ui.util.tagMessage

/**
 * Tag selection plus inline creation. Shared by the counter form and the home card menu so tags
 * are created in exactly one place. Duplicate tag names resolve to the existing tag.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagEditor(
    allTags: List<Tag>,
    selectedIds: List<String>,
    onSelectionChange: (List<String>) -> Unit,
    onCreateTag: (String, (TagWriteResult) -> Unit) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val createdTags = remember { mutableStateListOf<Tag>() }
    var newTagName by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }

    val visibleTags = remember(allTags, createdTags.size) {
        allTags + createdTags.filter { created -> allTags.none { it.id == created.id } }
    }

    fun toggle(tagId: String) {
        message = null
        onSelectionChange(if (tagId in selectedIds) selectedIds - tagId else selectedIds + tagId)
    }

    fun selectCreated(tagId: String) {
        if (tagId !in selectedIds) {
            onSelectionChange(selectedIds + tagId)
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (visibleTags.isEmpty()) {
            Text(
                text = stringResource(R.string.edit_tag_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            visibleTags.forEach { tag ->
                Pill(
                    label = tag.name,
                    selected = tag.id in selectedIds,
                    onClick = { toggle(tag.id) },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newTagName,
                onValueChange = {
                    newTagName = it.take(CounterMath.MAX_TAG_NAME_LENGTH + 1)
                    message = null
                },
                singleLine = true,
                placeholder = { Text(stringResource(R.string.edit_tag_new_hint)) },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val name = newTagName.trim()
                    if (name.isEmpty()) {
                        message = context.getString(R.string.edit_tag_name_blank)
                        return@TextButton
                    }
                    onCreateTag(name) { result ->
                        when (result) {
                            is TagWriteResult.Saved -> {
                                if (createdTags.none { it.id == result.tag.id }) createdTags += result.tag
                                selectCreated(result.tag.id)
                                newTagName = ""
                                message = null
                            }
                            is TagWriteResult.Existing -> {
                                selectCreated(result.tag.id)
                                newTagName = ""
                                message = context.getString(R.string.edit_tag_exists)
                            }
                            is TagWriteResult.Invalid -> message = result.violation.tagMessage(context)
                        }
                    }
                },
            ) {
                Text(stringResource(R.string.edit_tag_add))
            }
        }
        message?.let { text ->
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
fun TagPickerDialog(
    allTags: List<Tag>,
    initialSelected: List<String>,
    onDismiss: () -> Unit,
    onCreateTag: (String, (TagWriteResult) -> Unit) -> Unit,
    onSave: (List<String>) -> Unit,
) {
    var selected by remember { mutableStateOf(initialSelected) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_menu_tags_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                TagEditor(
                    allTags = allTags,
                    selectedIds = selected,
                    onSelectionChange = { selected = it },
                    onCreateTag = onCreateTag,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun TagManagerDialog(
    tags: List<Tag>,
    onDismiss: () -> Unit,
    onDelete: (Tag) -> Unit,
) {
    var pending by remember { mutableStateOf<Tag?>(null) }
    val target = pending

    AlertDialog(
        onDismissRequest = {
            if (target == null) onDismiss() else pending = null
        },
        title = {
            Text(
                text = if (target == null) {
                    stringResource(R.string.tag_manage_title)
                } else {
                    stringResource(R.string.tag_delete_title, target.name)
                },
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = {
            if (target == null) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        text = stringResource(R.string.tag_manage_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                    if (tags.isEmpty()) {
                        Text(
                            text = stringResource(R.string.edit_tag_empty),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        tags.forEachIndexed { index, tag ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = tag.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.weight(1f),
                                )
                                IconButton(onClick = { pending = tag }) {
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = stringResource(
                                            R.string.tag_delete_content_description,
                                            tag.name,
                                        ),
                                        tint = MaterialTheme.colorScheme.error,
                                    )
                                }
                            }
                            if (index != tags.lastIndex) {
                                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = stringResource(R.string.tag_delete_body, target.name),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            if (target == null) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close)) }
            } else {
                TextButton(
                    onClick = {
                        pending = null
                        onDelete(target)
                    },
                ) {
                    Text(
                        text = stringResource(R.string.action_delete),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        dismissButton = {
            if (target != null) {
                TextButton(onClick = { pending = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}

@Composable
fun StepEntryDialog(
    initialStep: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    val context = LocalContext.current
    var text by remember { mutableStateOf(initialStep.toString()) }
    var error by remember { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_step_custom_title), style = MaterialTheme.typography.headlineSmall) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it.filter { ch -> ch.isDigit() }.take(4)
                        error = null
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = MaterialTheme.shapes.small,
                    supportingText = {
                        Text(stringResource(R.string.home_step_hint), style = MaterialTheme.typography.bodySmall)
                    },
                )
                error?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val value = text.toIntOrNull()
                    if (value == null || value !in 1..CounterMath.MAX_STEP) {
                        error = context.getString(R.string.edit_error_step, CounterMath.MAX_STEP)
                    } else {
                        onConfirm(value)
                    }
                },
            ) { Text(stringResource(R.string.action_confirm)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
    )
}
