package com.ltq.count.ui.util

import android.content.Context
import com.ltq.count.R
import com.ltq.count.domain.CounterMath
import com.ltq.count.domain.RuleViolation
import com.ltq.count.domain.backup.BackupField

fun RuleViolation.tagMessage(context: Context): String = when (this) {
    RuleViolation.NameBlank -> context.getString(R.string.edit_tag_name_blank)
    RuleViolation.NameTooLong -> context.getString(R.string.edit_tag_name_long, CounterMath.MAX_TAG_NAME_LENGTH)
    else -> context.getString(R.string.error_generic)
}

fun RuleViolation.counterMessage(context: Context): String = when (this) {
    RuleViolation.NameBlank -> context.getString(R.string.edit_error_name_blank)
    RuleViolation.NameTooLong -> context.getString(R.string.edit_error_name_long, CounterMath.MAX_NAME_LENGTH)
    RuleViolation.InitialValueOutOfRange -> context.getString(
        R.string.edit_error_initial_range,
        -CounterMath.MAX_ABS_INITIAL,
        CounterMath.MAX_ABS_INITIAL,
    )
    RuleViolation.InitialValueNegativeNotAllowed -> context.getString(R.string.edit_error_initial_negative)
    RuleViolation.StepOutOfRange -> context.getString(R.string.edit_error_step, CounterMath.MAX_STEP)
    RuleViolation.DeltaZero -> context.getString(R.string.value_invalid)
    RuleViolation.DeltaOutOfRange -> context.getString(R.string.value_delta_out_of_range)
    RuleViolation.NoteTooLong -> context.getString(R.string.note_too_long, CounterMath.MAX_NOTE_LENGTH)
    RuleViolation.HistoryNegative -> context.getString(R.string.edit_error_initial_negative)
}

fun backupFieldName(context: Context, field: String): String = when (field) {
    "id" -> context.getString(R.string.field_id)
    "name" -> context.getString(R.string.field_name)
    "initialValue" -> context.getString(R.string.field_initial_value)
    "allowNegative" -> context.getString(R.string.field_allow_negative)
    "createdAt" -> context.getString(R.string.field_created_at)
    "sortOrder" -> context.getString(R.string.field_sort_order)
    "pinned" -> context.getString(R.string.field_pinned)
    "defaultStep" -> context.getString(R.string.field_default_step)
    "delta" -> context.getString(R.string.field_delta)
    "timestamp" -> context.getString(R.string.field_timestamp)
    "source" -> context.getString(R.string.field_source)
    "note" -> context.getString(R.string.field_note)
    "schemaVersion" -> context.getString(R.string.field_schema_version)
    "tagIds" -> context.getString(R.string.field_tag_id)
    "counters" -> context.getString(R.string.field_counter_list)
    else -> context.getString(R.string.backup_field_fallback)
}

fun backupFieldLabel(context: Context, field: BackupField): String = when (field) {
    BackupField.Id -> context.getString(R.string.field_id)
    BackupField.Name -> context.getString(R.string.field_name)
    BackupField.InitialValue -> context.getString(R.string.field_initial_value)
    BackupField.AllowNegative -> context.getString(R.string.field_allow_negative)
    BackupField.CreatedAt -> context.getString(R.string.field_created_at)
    BackupField.SortOrder -> context.getString(R.string.field_sort_order)
    BackupField.Pinned -> context.getString(R.string.field_pinned)
    BackupField.DefaultStep -> context.getString(R.string.field_default_step)
    BackupField.Delta -> context.getString(R.string.field_delta)
    BackupField.Timestamp -> context.getString(R.string.field_timestamp)
    BackupField.Source -> context.getString(R.string.field_source)
    BackupField.Note -> context.getString(R.string.field_note)
    BackupField.SchemaVersion -> context.getString(R.string.field_schema_version)
    BackupField.TagId -> context.getString(R.string.field_tag_id)
    BackupField.CounterList -> context.getString(R.string.field_counter_list)
}
