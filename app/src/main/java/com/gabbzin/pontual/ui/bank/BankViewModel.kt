package com.gabbzin.pontual.ui.bank

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.calc.localDateOf
import com.gabbzin.pontual.model.DayOff
import com.gabbzin.pontual.model.DayOffKind
import com.gabbzin.pontual.model.WorkSession
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class BankViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    /** Tique por minuto: atualiza o saldo de hoje (sessão aberta) e detecta a virada do dia. */
    private val minuteTicker: Flow<Instant> = flow {
        while (true) {
            emit(container.clock.instant())
            delay(60_000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<BankState?> = repo.observeSettings()
        .flatMapLatest { s ->
            minuteTicker
                .map { localDateOf(it, s) }
                .distinctUntilChanged()
                .flatMapLatest { today ->
                    val start = s.balanceStart
                    val sessions: Flow<List<WorkSession>> =
                        if (start == null) flowOf(emptyList()) else repo.observeSessions(start, today, s.zoneId)
                    combine(sessions, repo.observeDaysOff(), minuteTicker) { sessionList, daysOff, now ->
                        buildBankState(now, s, sessionList, daysOff)
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    /** Adiciona ou substitui (mesma data) um feriado ou recesso. */
    fun addDayOff(date: LocalDate, kind: DayOffKind, note: String) =
        guarded("Não foi possível salvar") {
            repo.addDayOff(DayOff(date, kind, note.trim().ifEmpty { null }))
        }

    fun removeDayOff(date: LocalDate) =
        guarded("Não foi possível remover") { repo.removeDayOff(date) }

    private fun guarded(failure: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.tryEmit("$failure: ${e.message}")
            }
        }
    }
}
