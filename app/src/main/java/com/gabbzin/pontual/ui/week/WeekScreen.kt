package com.gabbzin.pontual.ui.week

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.calc.DaySummary
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.ui.formatDayMonth
import com.gabbzin.pontual.ui.formatDuration
import com.gabbzin.pontual.ui.formatShortWeekday
import java.time.LocalDate

@Composable
fun WeekScreen(container: AppContainer, onOpenDay: (LocalDate) -> Unit) {
    val vm: WeekViewModel = viewModel(factory = viewModelFactory { initializer { WeekViewModel(container) } })
    val state by vm.state.collectAsStateWithLifecycle()
    val atCurrentWeek by vm.atCurrentWeek.collectAsStateWithLifecycle()
    val week = state ?: return

    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Semana", style = MaterialTheme.typography.headlineSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = vm::previous) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Semana anterior")
            }
            Text(
                weekLabel(week.monday),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.titleMedium,
            )
            IconButton(onClick = vm::next, enabled = !atCurrentWeek) {
                Icon(Icons.Filled.ChevronRight, contentDescription = "Próxima semana")
            }
        }
        Text("Saldo da semana: ${formatDuration(week.balance, signed = true)}", style = MaterialTheme.typography.bodyLarge)
        LazyColumn(Modifier.weight(1f)) {
            items(week.days, key = { it.date }) { day ->
                DayRow(day, onClick = { onOpenDay(day.date) })
            }
        }
    }
}

@Composable
private fun DayRow(day: DaySummary, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 12.dp)) {
        Text(
            "${formatShortWeekday(day.date)} ${formatDayMonth(day.date)} — " +
                "${formatDuration(day.worked)} / ${formatDuration(day.expected)} " +
                "(${formatDuration(day.balance, signed = true)})",
            style = MaterialTheme.typography.bodyLarge,
        )
        day.dayOff?.let {
            Text(
                if (it == DayOffKind.HOLIDAY) "Feriado" else "Recesso",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}
