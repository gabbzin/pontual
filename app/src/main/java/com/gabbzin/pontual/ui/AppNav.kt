package com.gabbzin.pontual.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.ui.settings.SettingsScreen

object Routes {
    const val TODAY = "today"
    const val WEEK = "week"
    const val DAY = "day/{date}"
    const val BANK = "bank"
    const val SETTINGS = "settings"
    fun day(date: java.time.LocalDate) = "day/$date"
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Hoje", Icons.Filled.Schedule),
    Tab(Routes.WEEK, "Semana", Icons.Filled.DateRange),
    Tab(Routes.BANK, "Banco", Icons.Filled.AccountBalance),
    Tab(Routes.SETTINGS, "Config", Icons.Filled.Settings),
)

@Composable
fun AppNav(container: AppContainer) {
    val settings by container.repository.observeSettings().collectAsStateWithLifecycle(initialValue = null)
    val loaded = settings ?: return
    val startConfigured = remember { loaded.isConfigured }
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val current = backStack?.destination?.route

    fun goTo(route: String) = nav.navigate(route) {
        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }

    Scaffold(
        bottomBar = {
            if (loaded.isConfigured) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = current == tab.route,
                            onClick = { goTo(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = tab.label) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = if (startConfigured) Routes.TODAY else Routes.SETTINGS,
            modifier = Modifier.padding(padding),
        ) {
            composable(Routes.TODAY) { Placeholder("Hoje") }
            composable(Routes.WEEK) { Placeholder("Semana") }
            composable(Routes.DAY) { Placeholder("Dia") }
            composable(Routes.BANK) { Placeholder("Banco") }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    container = container,
                    onFirstSave = {
                        nav.navigate(Routes.TODAY) {
                            popUpTo(Routes.SETTINGS) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun Placeholder(name: String) {
    Box(Modifier.fillMaxSize()) { Text(name, Modifier.padding(24.dp)) }
}
