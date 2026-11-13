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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.data.PunchException
import com.gabbzin.pontual.model.Settings
import com.gabbzin.pontual.model.Source
import com.gabbzin.pontual.model.WorkSession
import com.gabbzin.pontual.ui.formatLongDay
import com.gabbzin.pontual.ui.formatTime
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

data class DayDetailState(val settings: Settings, val sessions: List<WorkSession>)

class DayDetailViewModel(private val container: AppContainer, private val date: LocalDate) : ViewModel() {
    private val repo = container.repository

    val state: StateFlow<DayDetailState?> = repo.observeSettings().flatMapLatest { s ->
        repo.observeSessions(date, date, s.zoneId).map { DayDetailState(s, it) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** [onResult] recebe a mensagem de erro, ou null se o lançamento foi salvo. */
    fun addManual(startedAt: Instant, endedAt: Instant, note: String, onResult: (String?) -> Unit) {
        viewModelScope.launch {
            val error = try {
                repo.addManual(startedAt, endedAt, note)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureMessage(e)
            }
            onResult(error)
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            val error = try {
                repo.deleteManual(id)
                null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failureMessage(e)
            }
            error?.let { _messages.tryEmit(it) }
        }
    }

    private fun failureMessage(e: Exception): String = when (e) {
        is PunchException -> e.error.message
        else -> "Erro inesperado: ${e.message}"
    }
}

@Composable
fun DayDetailScreen(container: AppContainer, date: LocalDate, onBack: () -> Unit) {
    val vm: DayDetailViewModel = viewModel(
        factory = viewModelFactory { initializer { DayDetailViewModel(container, date) } },
    )
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    var manualOpen by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(vm) { vm.messages.collect { snackbar.showSnackbar(it) } }

    Scaffold(snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        val st = state ?: return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                }
                Text(formatLongDay(date), style = MaterialTheme.typography.headlineSmall)
            }
            if (st.sessions.isEmpty()) {
                Text("Nenhuma sessão neste dia.", style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(Modifier.weight(1f)) {
                items(st.sessions, key = { it.id }) { session ->
                    SessionRow(
                        session = session,
                        zone = st.settings.zoneId,
                        onDelete = if (session.isManualOnly) ({ pendingDelete = session.id }) else null,
                    )
                }
            }
            Button(onClick = { manualOpen = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Lançar período esquecido")
            }
        }

        if (manualOpen) {
            ManualEntryDialog(
                date = date,
                settings = st.settings,
                onDismiss = { manualOpen = false },
                onSave = { startedAt, endedAt, note, onResult ->
                    vm.addManual(startedAt, endedAt, note) { error ->
                        if (error == null) manualOpen = false
                        onResult(error)
                    }
                },
            )
        }
    }

    pendingDelete?.let { id ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Apagar sessão?") },
            text = { Text("Esse lançamento manual será removido.") },
            confirmButton = {
                TextButton(onClick = { vm.delete(id); pendingDelete = null }) { Text("Apagar") }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun SessionRow(session: WorkSession, zone: ZoneId, onDelete: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(sessionText(session, zone), style = MaterialTheme.typography.bodyLarge)
            session.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
        onDelete?.let { OutlinedButton(onClick = it) { Text("Apagar") } }
    }
}

private fun sessionText(s: WorkSession, zone: ZoneId): String {
    val start = formatTime(s.startedAt, zone) + if (s.startSource == Source.MANUAL) " manual" else ""
    val end = s.endedAt?.let { formatTime(it, zone) + if (s.endSource == Source.MANUAL) " manual" else "" }
        ?: "em aberto"
    return "$start – $end"
}
