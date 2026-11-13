package com.gabbzin.pontual.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.navigation.NavGraph
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
import androidx.test.core.app.ApplicationProvider
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.PontualApp
import com.gabbzin.pontual.model.Settings
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AppNavTest {
    @get:Rule val compose = createComposeRule()

    private val container: AppContainer =
        ApplicationProvider.getApplicationContext<PontualApp>().container

    private fun waitForText(text: String) =
        compose.waitUntil(timeoutMillis = 5_000) {
            compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
        }

    @Test
    fun firstRun_opensSettingsWithoutBottomBar() {
        compose.setContent { AppNav(container) }
        waitForText("Configuração")
        compose.onNodeWithText("Configuração").assertIsDisplayed()
        compose.onNodeWithText("Preencha o início do estágio e o início do saldo para começar.").assertIsDisplayed()
        compose.onNodeWithText("Semana").assertDoesNotExist()
    }

    @Test
    fun configured_opensTodayWithBottomBar() {
        runBlocking {
            container.repository.saveSettings(
                Settings(internshipStart = LocalDate.of(2026, 5, 13), balanceStart = LocalDate.of(2026, 11, 2)),
            )
        }
        compose.setContent { AppNav(container) }
        waitForText("Semana")
        compose.onNodeWithText("Semana").assertIsDisplayed()
        compose.onNodeWithText("Config").assertIsDisplayed()
    }

    @Test
    fun firstRun_saveThenTabs_keepsSingleTodayEntryOnBackStack() {
        lateinit var nav: NavHostController
        compose.setContent {
            AppNav(container, navController = rememberNavController().also { nav = it })
        }
        waitForText("Configuração")
        runBlocking {
            container.repository.saveSettings(
                Settings(internshipStart = LocalDate.of(2026, 5, 13), balanceStart = LocalDate.of(2026, 11, 2)),
            )
        }
        waitForText("Semana")
        compose.onNodeWithText("Semana").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Hoje").performClick()
        compose.waitForIdle()

        val entries = nav.currentBackStack.value.filter { it.destination !is NavGraph }
        assertEquals(listOf("today"), entries.map { it.destination.route })
    }
}
