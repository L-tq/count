package com.ltq.count.ui.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.ltq.count.R
import com.ltq.count.data.repo.CounterRepository
import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.CounterRules
import com.ltq.count.ui.components.CircleIconButton
import com.ltq.count.ui.components.CountSnackbarHost
import com.ltq.count.ui.components.ConfirmDialog
import com.ltq.count.ui.components.EmptyState
import com.ltq.count.ui.components.TagEditor
import kotlinx.coroutines.flow.collectLatest

@Composable
fun CounterEditScreen(
    counterId: String?,
    repository: CounterRepository,
    onBack: () -> Unit,
    onSaved: (String) -> Unit,
    viewModel: CounterEditViewModel = viewModel(
        key = counterId ?: "new-counter",
        factory = counterEditViewModelFactory(repository, counterId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val tags by viewModel.allTags.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showErrors by remember { mutableStateOf(false) }
    var showDiscard by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.effects.collectLatest { effect ->
            when (effect) {
                is EditEffect.Saved -> onSaved(effect.counterId)
                is EditEffect.Error -> snackbarHostState.showSnackbar(
                    context.getString(effect.messageRes, *effect.args.toTypedArray()),
                )
            }
        }
    }

    BackHandler(enabled = state.dirty) { showDiscard = true }

    val initialValue = state.initialText.toIntOrNull()
    val stepValue = state.stepText.toIntOrNull()
    val nameInvalid = CounterRules.validateName(state.name) != null
    val initialInvalid = initialValue == null ||
        !CounterMath.isValidInitialValue(initialValue) ||
        (!state.allowNegative && initialValue < 0)
    val stepInvalid = stepValue == null || !CounterMath.isValidStep(stepValue)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { CountSnackbarHost(snackbarHostState) },
        topBar = {
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
                    onClick = { if (state.dirty) showDiscard = true else onBack() },
                )
                Text(
                    text = stringResource(
                        if (state.isNew) R.string.edit_title_new else R.string.edit_title_edit,
                    ),
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 4.dp),
                )
                TextButton(
                    onClick = {
                        showErrors = true
                        if (!nameInvalid && !initialInvalid && !stepInvalid) viewModel.save()
                    },
                    enabled = !state.saving,
                ) {
                    Text(stringResource(R.string.edit_save))
                }
            }
        },
    ) { innerPadding ->
        if (state.notFound) {
            EmptyState(
                title = stringResource(R.string.value_counter_missing),
                body = stringResource(R.string.error_generic),
                modifier = Modifier.padding(innerPadding),
            )
            return@Scaffold
        }
        if (state.loading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::setName,
                label = { Text(stringResource(R.string.edit_name)) },
                placeholder = { Text(stringResource(R.string.edit_name_hint)) },
                singleLine = true,
                isError = showErrors && nameInvalid,
                supportingText = if (showErrors && nameInvalid) {
                    {
                        Text(
                            stringResource(
                                if (state.name.isBlank()) R.string.edit_error_name_blank else R.string.edit_error_name_long,
                                CounterMath.MAX_NAME_LENGTH,
                            ),
                        )
                    }
                } else {
                    null
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.initialText,
                onValueChange = viewModel::setInitialText,
                label = { Text(stringResource(R.string.edit_initial_value)) },
                placeholder = { Text(stringResource(R.string.edit_initial_hint)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                isError = showErrors && initialInvalid,
                supportingText = {
                    val message = when {
                        showErrors && initialInvalid -> stringResource(
                            if (state.allowNegative) R.string.edit_error_initial_range else R.string.edit_error_initial_negative,
                            -CounterMath.MAX_ABS_INITIAL,
                            CounterMath.MAX_ABS_INITIAL,
                        )
                        !state.isNew -> stringResource(R.string.a11y_current_value, state.currentValue)
                        else -> stringResource(R.string.edit_initial_helper)
                    }
                    Text(message)
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.edit_allow_negative),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.edit_allow_negative_helper),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(0.dp))
                Switch(
                    checked = state.allowNegative,
                    onCheckedChange = viewModel::setAllowNegative,
                )
            }

            OutlinedTextField(
                value = state.stepText,
                onValueChange = viewModel::setStepText,
                label = { Text(stringResource(R.string.edit_default_step)) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = showErrors && stepInvalid,
                supportingText = {
                    Text(
                        if (showErrors && stepInvalid) {
                            stringResource(R.string.edit_error_step, CounterMath.MAX_STEP)
                        } else {
                            stringResource(R.string.edit_default_step_helper)
                        },
                    )
                },
                shape = MaterialTheme.shapes.small,
                modifier = Modifier.fillMaxWidth(),
            )

            Text(
                text = stringResource(R.string.edit_tags),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.edit_tags_helper),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TagEditor(
                allTags = tags,
                selectedIds = state.selectedTagIds,
                onSelectionChange = viewModel::setTags,
                onCreateTag = { name, onResult -> viewModel.createTag(name, onResult) },
            )

            Spacer(Modifier.height(6.dp))
            Button(
                onClick = {
                    showErrors = true
                    if (!nameInvalid && !initialInvalid && !stepInvalid) viewModel.save()
                },
                enabled = !state.saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
            ) {
                Text(stringResource(R.string.edit_save))
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDiscard) {
        ConfirmDialog(
            title = stringResource(R.string.edit_unsaved_title),
            body = stringResource(R.string.edit_unsaved_body),
            confirmLabel = stringResource(R.string.action_done),
            cancelLabel = stringResource(R.string.action_cancel),
            destructive = true,
            onConfirm = {
                showDiscard = false
                onBack()
            },
            onDismiss = { showDiscard = false },
        )
    }
}
