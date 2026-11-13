package com.gabbzin.pontual.ui.bank

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.ui.formatDuration
import com.gabbzin.pontual.ui.formatLongDay
import com.gabbzin.pontual.ui.settings.DateField
import java.time.LocalDate

@Composable
fun BankScreen(container: AppContainer) {
    val vm: BankViewModel = viewModel(factory = viewModelFactory { initializer { BankViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var adding by remember { mutableStateOf(false) }
    var pendingRemove by remember { mutableStateOf<DayOff?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val bank = state ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Banco", style = MaterialTheme.typography.headlineSmall)
            Text(
                "Saldo acumulado ${formatDuration(bank.accumulated, signed = true)}",
                style = MaterialTheme.typography.titleMedium,
            )
            Text("Hoje ${formatDuration(bank.today, signed = true)}", style = MaterialTheme.typography.bodyLarge)
            Text(
                "Recesso: ${bank.recess.accruedDays} acumulados · ${bank.recess.takenDays} usados · " +
                    "${bank.recess.availableDays} disponíveis",
                style = MaterialTheme.typography.bodyLarge,
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Feriados e recessos",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall,
                )
                Button(onClick = { adding = true }) { Text("Adicionar") }
            }

            if (bank.daysOff.isEmpty()) {
                Text("Nenhum feriado ou recesso cadastrado.", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(bank.daysOff, key = { it.date }) { day ->
                    DayOffRow(day, onRemove = { pendingRemove = day })
                }
            }
        }
    }

    if (adding) {
        AddDayOffDialog(
            onDismiss = { adding = false },
            onAdd = { date, kind, note ->
                vm.addDayOff(date, kind, note)
                adding = false
            },
        )
    }

    pendingRemove?.let { day ->
        AlertDialog(
            onDismissRequest = { pendingRemove = null },
            title = { Text("Remover ${formatLongDay(day.date)}?") },
            text = { Text("Esse dia volta a ser tratado como dia normal.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.removeDayOff(day.date)
                    pendingRemove = null
                }) { Text("Remover") }
            },
            dismissButton = { TextButton(onClick = { pendingRemove = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun DayOffRow(day: DayOff, onRemove: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 8.dp)) {
            Text(formatLongDay(day.date), style = MaterialTheme.typography.bodyLarge)
            val kind = if (day.kind == DayOffKind.HOLIDAY) "Feriado" else "Recesso"
            Text(
                listOfNotNull(kind, day.note).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
            )
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remover ${formatLongDay(day.date)}")
        }
    }
}

@Composable
private fun AddDayOffDialog(onDismiss: () -> Unit, onAdd: (LocalDate, DayOffKind, String) -> Unit) {
    var date by remember { mutableStateOf<LocalDate?>(null) }
    var kind by remember { mutableStateOf(DayOffKind.HOLIDAY) }
    var note by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adicionar feriado ou recesso") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                DateField("Data", date) { date = it }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = kind == DayOffKind.HOLIDAY,
                        onClick = { kind = DayOffKind.HOLIDAY },
                        label = { Text("Feriado") },
                    )
                    FilterChip(
                        selected = kind == DayOffKind.RECESS,
                        onClick = { kind = DayOffKind.RECESS },
                        label = { Text("Recesso") },
                    )
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("Observação (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { date?.let { onAdd(it, kind, note) } },
                enabled = date != null,
            ) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
