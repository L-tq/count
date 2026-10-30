package com.ltq.count

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ltq.count.data.prefs.ThemeMode
import com.ltq.count.ui.navigation.CountApp
import com.ltq.count.ui.quick.QuickKeyBus
import com.ltq.count.ui.theme.CountTheme

class MainActivity : ComponentActivity() {
    companion object {
        const val EXTRA_COUNTER_ID = "extra_counter_id"
        private const val DEEP_LINK_SCHEME = "count"
        private const val DEEP_LINK_HOST = "quick"
    }

    private var pendingQuickCounterId by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingQuickCounterId = quickCounterIdFrom(intent)

        val container = countApplication.container
        setContent {
            val themeMode by container.settingsRepository.themeMode
                .collectAsStateWithLifecycle(initialValue = ThemeMode.SYSTEM)

            CountTheme(themeMode = themeMode) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    CountApp(
                        container = container,
                        pendingQuickCounterId = pendingQuickCounterId,
                        onPendingQuickConsumed = { pendingQuickCounterId = null },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        pendingQuickCounterId = quickCounterIdFrom(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        val adjust = QuickKeyBus.adjust
        if (adjust != null && event.action == KeyEvent.ACTION_DOWN) {
            when (event.keyCode) {
                KeyEvent.KEYCODE_VOLUME_UP -> {
                    adjust(1)
                    return true
                }
                KeyEvent.KEYCODE_VOLUME_DOWN -> {
                    adjust(-1)
                    return true
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    private fun quickCounterIdFrom(intent: Intent?): String? {
        val data: Uri? = intent?.data
        if (data != null && data.scheme == DEEP_LINK_SCHEME && data.host == DEEP_LINK_HOST) {
            val segments = data.pathSegments
            if (!segments.isNullOrEmpty()) return segments.first()
        }
        return intent?.getStringExtra(EXTRA_COUNTER_ID)
    }
}
