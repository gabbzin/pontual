package com.gabbzin.pontual.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.ui.PT_BR
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle

private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy")

@Composable
fun SettingsScreen(container: AppContainer) {
    val vm: SettingsViewModel = viewModel(factory = viewModelFactory { initializer { SettingsViewModel(container) } })
    val form by vm.form.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resolver = LocalContext.current.contentResolver
    var confirmImport by remember { mutableStateOf<android.net.Uri?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri?.let { vm.export(it, resolver) }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        confirmImport = uri
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val f = form ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Configuração", style = MaterialTheme.typography.headlineSmall)
            if (!f.settings.isConfigured) {
                Text("Preencha o início do estágio e o início do saldo para começar.", style = MaterialTheme.typography.bodyMedium)
            }

            OutlinedTextField(
                value = f.dailyMinutesText,
                onValueChange = { v -> vm.update { it.copy(dailyMinutesText = v) } },
                label = { Text("Jornada diária (minutos)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Dias úteis", style = MaterialTheme.typography.titleSmall)
            WorkdayChips(f.settings.workdays) { days -> vm.update { it.copy(settings = it.settings.copy(workdays = days)) } }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TimeField("Início", f.settings.windowStart, Modifier.weight(1f)) { t ->
                    vm.update { it.copy(settings = it.settings.copy(windowStart = t)) }
                }
                TimeField("Fim", f.settings.windowEnd, Modifier.weight(1f)) { t ->
                    vm.update { it.copy(settings = it.settings.copy(windowEnd = t)) }
                }
            }
            OutlinedTextField(
                value = f.toleranceText,
                onValueChange = { v -> vm.update { it.copy(toleranceText = v) } },
                label = { Text("Tolerância (minutos)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth(),
            )

            DateField("Início do estágio", f.settings.internshipStart) { d ->
                vm.update { it.copy(settings = it.settings.copy(internshipStart = d)) }
            }
            DateField("Início do saldo", f.settings.balanceStart) { d ->
                vm.update { it.copy(settings = it.settings.copy(balanceStart = d)) }
            }

            Button(
                onClick = vm::save,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Salvar") }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("Backup", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { exportLauncher.launch(vm.backupFileName()) }, modifier = Modifier.weight(1f)) {
                    Text("Exportar backup")
                }
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) },
                    modifier = Modifier.weight(1f),
                ) { Text("Importar backup") }
            }
        }
    }

    confirmImport?.let { uri ->
        AlertDialog(
            onDismissRequest = { confirmImport = null },
            title = { Text("Importar backup?") },
            text = { Text("Isso substitui todos os dados atuais.") },
            confirmButton = {
                TextButton(onClick = { vm.import(uri, resolver); confirmImport = null }) { Text("Substituir") }
            },
            dismissButton = { TextButton(onClick = { confirmImport = null }) { Text("Cancelar") } },
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WorkdayChips(selected: Set<DayOfWeek>, onChange: (Set<DayOfWeek>) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        DayOfWeek.entries.forEach { day ->
            FilterChip(
                selected = day in selected,
                onClick = { onChange(if (day in selected) selected - day else selected + day) },
                label = { Text(day.getDisplayName(TextStyle.SHORT, PT_BR).removeSuffix(".")) },
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: LocalTime, modifier: Modifier = Modifier, onPick: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = modifier) { Text("$label: $value") }
    if (open) {
        val state = rememberTimePickerState(value.hour, value.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)); open = false }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
            text = { TimePicker(state) },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateField(label: String, value: LocalDate?, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth()) {
        Text("$label: ${value?.format(DATE) ?: "—"}")
    }
    if (open) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = value?.atStartOfDay(ZoneOffset.UTC)?.toInstant()?.toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let {
                        onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate())
                    }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancelar") } },
        ) { DatePicker(state) }
    }
}
