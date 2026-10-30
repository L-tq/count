package com.ltq.count.ui.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalConfiguration
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ltq.count.AppContainer
import com.ltq.count.data.prefs.CardSize
import com.ltq.count.ui.detail.DetailScreen
import com.ltq.count.ui.edit.CounterEditScreen
import com.ltq.count.ui.home.HomeScreen
import com.ltq.count.ui.quick.QuickCountScreen
import com.ltq.count.ui.settings.SettingsScreen
import kotlinx.coroutines.delay

private const val TAG = "CountNav"

// ComposeNavigator's name is internal in navigation-compose 2.8.9.
private const val COMPOSABLE_NAVIGATOR = "composable"

object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{counterId}"
    const val EDIT = "edit?counterId={counterId}"
    const val QUICK = "quick/{counterId}?shortcut={shortcut}"
    const val SETTINGS = "settings"

    fun detail(counterId: String): String = "detail/$counterId"
    fun edit(counterId: String?): String = if (counterId.isNullOrBlank()) "edit" else "edit?counterId=$counterId"
    fun quick(counterId: String, fromShortcut: Boolean = false): String =
        "quick/$counterId?shortcut=$fromShortcut"
}

@Composable
fun CountApp(
    container: AppContainer,
    pendingQuickCounterId: String?,
    onPendingQuickConsumed: () -> Unit,
) {
    val navController = rememberNavController()
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val cardSize by container.settingsRepository.cardSize
        .collectAsStateWithLifecycle(initialValue = CardSize.STANDARD)

    // NavHost draws nothing at all when its back stack has no Compose destination. A relaunch
    // or a window-level configuration change can leave it empty, which otherwise shows the
    // window background forever. Check both after a configuration change and after each resume.
    LaunchedEffect(navController, configuration) {
        delay(200)
        navController.recoverHomeIfEmpty()
    }
    LaunchedEffect(navController, lifecycleOwner) {
        lifecycleOwner.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            delay(300)
            navController.recoverHomeIfEmpty()
        }
    }

    LaunchedEffect(pendingQuickCounterId) {
        val id = pendingQuickCounterId ?: return@LaunchedEffect
        navController.navigate(Routes.quick(id, fromShortcut = true)) { launchSingleTop = true }
        onPendingQuickConsumed()
    }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                repository = container.counterRepository,
                cardSize = cardSize,
                onOpenCounter = { navController.navigate(Routes.detail(it)) },
                onEditCounter = { navController.navigate(Routes.edit(it)) },
                onNewCounter = { navController.navigate(Routes.edit(null)) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onQuickCount = { navController.navigate(Routes.quick(it)) },
            )
        }
        composable(
            route = Routes.DETAIL,
            arguments = listOf(navArgument("counterId") { type = NavType.StringType }),
        ) { entry ->
            val counterId = entry.arguments?.getString("counterId") ?: return@composable
            DetailScreen(
                counterId = counterId,
                repository = container.counterRepository,
                onBack = { navController.popBackStack() },
                onEdit = { navController.navigate(Routes.edit(it)) },
                onQuickCount = { navController.navigate(Routes.quick(it)) },
            )
        }
        composable(
            route = Routes.EDIT,
            arguments = listOf(
                navArgument("counterId") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
        ) { entry ->
            CounterEditScreen(
                counterId = entry.arguments?.getString("counterId"),
                repository = container.counterRepository,
                onBack = { navController.popBackStack() },
                onSaved = { navController.popBackStack() },
            )
        }
        composable(
            route = Routes.QUICK,
            arguments = listOf(
                navArgument("counterId") { type = NavType.StringType },
                navArgument("shortcut") {
                    type = NavType.BoolType
                    defaultValue = false
                },
            ),
        ) { entry ->
            val counterId = entry.arguments?.getString("counterId") ?: return@composable
            val fromShortcut = entry.arguments?.getBoolean("shortcut") ?: false
            QuickCountScreen(
                counterId = counterId,
                fromShortcut = fromShortcut,
                repository = container.counterRepository,
                onExit = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = { navController.popBackStack() },
            )
        }
    }
}

private fun NavHostController.recoverHomeIfEmpty() {
    if (visibleEntries.value.any { it.destination.navigatorName == COMPOSABLE_NAVIGATOR }) {
        return
    }

    Log.w(
        TAG,
        "No Compose destination (entries=" +
            visibleEntries.value.map { it.destination.route } +
            "); navigating back to home",
    )
    val graph = runCatching { graph }.getOrNull() ?: return
    runCatching {
        navigate(Routes.HOME) {
            popUpTo(graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }.onFailure { error ->
        Log.e(TAG, "Could not recover navigation back stack", error)
    }
}
