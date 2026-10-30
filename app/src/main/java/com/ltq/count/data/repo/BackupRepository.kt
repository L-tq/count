package com.ltq.count.data.repo

import android.content.ContentResolver
import android.net.Uri
import com.ltq.count.domain.backup.ExistingSnapshot
import com.ltq.count.domain.backup.ImportMode
import com.ltq.count.domain.backup.ImportPlan
import com.ltq.count.domain.backup.ImportPlanner
import com.ltq.count.domain.backup.ImportedData
import com.ltq.count.domain.backup.NegativeImportPolicy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Storage Access Framework boundary. Reads and writes happen off the main thread. */
class BackupRepository(
    private val resolver: ContentResolver,
    private val repository: CounterRepository,
) {
    suspend fun writeText(uri: Uri, text: String): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openOutputStream(uri)?.use { output ->
                output.write(text.toByteArray(Charsets.UTF_8))
                output.flush()
            } != null
        }.getOrDefault(false)
    }

    suspend fun readText(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            resolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
        }.getOrNull()
    }

    suspend fun snapshot(): ExistingSnapshot = repository.snapshot()

    suspend fun planImport(
        data: ImportedData,
        mode: ImportMode,
        policy: NegativeImportPolicy,
    ): ImportPlan {
        val existing = repository.snapshot()
        return ImportPlanner.plan(existing, data, mode, policy)
    }

    suspend fun apply(plan: ImportPlan) = repository.applyImport(plan)
}
