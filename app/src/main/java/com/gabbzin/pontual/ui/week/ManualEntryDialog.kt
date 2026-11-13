package com.gabbzin.pontual.ui.week

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.ui.formatLongDay
import com.gabbzin.pontual.ui.settings.TimeField
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime

/**
 * Entrada e saída são instantes no fuso das configurações.
 * [onSave] recebe (entrada, saída, observação, onResult); [onResult] com a mensagem de erro mostra o erro
 * dentro do diálogo, que fica aberto. Qualquer edição limpa o erro.
 */
@Composable
fun ManualEntryDialog(
    date: LocalDate,
    settings: Settings,
    onDismiss: () -> Unit,
    onSave: (Instant, Instant, String, (String?) -> Unit) -> Unit,
) {
    var start by remember { mutableStateOf<LocalTime>(settings.windowStart) }
    var end by remember { mutableStateOf<LocalTime>(settings.windowEnd) }
    var note by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lançar período esquecido") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(formatLongDay(date))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TimeField("Entrada", start, Modifier.weight(1f)) {
                        start = it
                        error = null
                    }
                    TimeField("Saída", end, Modifier.weight(1f)) {
                        end = it
                        error = null
                    }
                }
                OutlinedTextField(
                    value = note,
                    onValueChange = {
                        note = it
                        error = null
                    },
                    label = { Text("Observação (obrigatória)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onSave(
                    date.atTime(start).atZone(settings.zoneId).toInstant(),
                    date.atTime(end).atZone(settings.zoneId).toInstant(),
                    note,
                ) { error = it }
            }) { Text("Salvar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}
