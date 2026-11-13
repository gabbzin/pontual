package com.gabbzin.pontual.ui.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.calc.localDateOf
import com.gabbzin.pontual.calc.punchWindow
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import com.gabbzin.pontual.ui.formatDayMonth
import com.gabbzin.pontual.ui.formatDuration
import com.gabbzin.pontual.ui.formatLongDay
import com.gabbzin.pontual.ui.formatTime
import com.gabbzin.pontual.ui.formatTimer
import com.gabbzin.pontual.ui.settings.TimeField
import java.time.LocalDate
import java.time.LocalTime

private val GREEN = Color(0xFF2E7D32)
private val RED = Color(0xFFC62828)

@Composable
fun TodayScreen(container: AppContainer) {
    val vm: TodayViewModel = viewModel(factory = viewModelFactory { initializer { TodayViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val settings by container.repository.observeSettings().collectAsStateWithLifecycle(initialValue = null)
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.messages.collect { msg ->
            val result = snackbar.showSnackbar(msg.text, actionLabel = msg.retry?.let { "Tentar de novo" })
            if (result == SnackbarResult.ActionPerformed) msg.retry?.let(vm::retry)
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val st = state ?: return@Scaffold
        val s = settings ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            st.forgotten?.let { ForgottenSessionCard(it, s) { t, note -> vm.closeForgotten(t, note) } }

            Text(formatLongDay(st.date), style = MaterialTheme.typography.headlineSmall)

            PunchButtonLarge(st, onClick = vm::punch)

            st.running?.let {
                Text("Em andamento ${formatTimer(it)}", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                "Hoje ${formatDuration(st.worked)} / ${formatDuration(st.expected)}",
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "Saldo do dia ${formatDuration(st.dayBalance, signed = true)}",
                style = MaterialTheme.typography.bodyLarge,
            )

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                st.sessions.forEach { SessionRow(it, s) }
            }
        }
    }
}

@Composable
private fun PunchButtonLarge(state: TodayState, onClick: () -> Unit) {
    val (label, colors) = when (state.button) {
        PunchButton.ENTER -> "ENTRAR" to ButtonDefaults.buttonColors(containerColor = GREEN, contentColor = Color.White)
        PunchButton.EXIT -> "SAIR" to ButtonDefaults.buttonColors(containerColor = RED, contentColor = Color.White)
        PunchButton.DISABLED -> state.windowLabel to ButtonDefaults.buttonColors()
    }
    Button(
        onClick = onClick,
        enabled = state.button != PunchButton.DISABLED,
        colors = colors,
        modifier = Modifier.fillMaxWidth(0.8f).height(120.dp),
    ) {
        Text(label, style = MaterialTheme.typography.headlineSmall)
    }
}

@Composable
private fun SessionRow(session: WorkSession, s: Settings) {
    val manual = session.startSource == Source.MANUAL || session.endSource == Source.MANUAL
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        val end = session.endedAt?.let { formatTime(it, s.zoneId) } ?: "em aberto"
        Text("${formatTime(session.startedAt, s.zoneId)} – $end", style = MaterialTheme.typography.bodyLarge)
        if (manual) {
            Text("manual", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ForgottenSessionCard(session: WorkSession, s: Settings, onClose: (LocalTime, String) -> Unit) {
    var dialogOpen by remember { mutableStateOf(false) }
    val day = localDateOf(session.startedAt, s)

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                "Sessão esquecida de ${formatDayMonth(day)}, aberta desde ${formatTime(session.startedAt, s.zoneId)}.",
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = { dialogOpen = true }) { Text("Fechar sessão esquecida") }
        }
    }

    if (dialogOpen) {
        CloseForgottenDialog(
            day = day,
            defaultEnd = punchWindow(day, s).endInclusive.atZone(s.zoneId).toLocalTime(),
            onDismiss = { dialogOpen = false },
            onConfirm = onClose,
        )
    }
}

@Composable
private fun CloseForgottenDialog(
    day: LocalDate,
    defaultEnd: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime, String) -> Unit,
) {
    var endTime by remember { mutableStateOf(defaultEnd) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Fechar sessão esquecida") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(formatLongDay(day), style = MaterialTheme.typography.bodyMedium)
                TimeField("Saída", endTime) { endTime = it }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Observação") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(endTime, note.trim()) }, enabled = note.isNotBlank()) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
