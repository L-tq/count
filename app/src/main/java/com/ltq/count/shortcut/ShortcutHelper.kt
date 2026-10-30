package com.ltq.count.shortcut

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import com.ltq.count.MainActivity
import com.ltq.count.R

/** Pinned launcher shortcut that opens the full screen quick counter. */
object ShortcutHelper {
    fun requestPin(context: Context, counterId: String, counterName: String): Boolean {
        if (!ShortcutManagerCompat.isRequestPinShortcutSupported(context)) return false
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse("count://quick/$counterId")
            putExtra(MainActivity.EXTRA_COUNTER_ID, counterId)
        }
        val shortcut = ShortcutInfoCompat.Builder(context, "quick-$counterId")
            .setShortLabel(context.getString(R.string.shortcut_short_label, counterName.take(12)))
            .setLongLabel(context.getString(R.string.shortcut_long_label, counterName))
            .setIcon(IconCompat.createWithResource(context, R.drawable.ic_shortcut_count))
            .setIntent(intent)
            .build()
        return runCatching {
            ShortcutManagerCompat.requestPinShortcut(context, shortcut, null)
        }.getOrDefault(false)
    }
}
