package com.gabbzin.pontual.ui.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.calc.localDateOf
import com.gabbzin.pontual.data.PunchException
import com.gabbzin.pontual.model.PunchAction
import com.gabbzin.pontual.model.Settings
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Mensagem para o snackbar; [retry] preenchido quando a batida pode ser repetida. */
data class TodayMessage(val text: String, val retry: PunchAction?)

class TodayViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository

    private val ticker: Flow<Instant> = flow {
        while (true) {
            emit(container.clock.instant())
            delay(1000)
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val state: StateFlow<TodayState?> = repo.observeSettings()
        .flatMapLatest { s ->
            ticker
                .map { localDateOf(it, s) }
                .distinctUntilChanged()
                .flatMapLatest { date ->
                    combine(
                        ticker,
                        repo.observeSessions(date, date, s.zoneId),
                        repo.observeOpenSession(),
                        repo.observeDaysOff(),
                    ) { now, sessions, open, daysOff ->
                        buildTodayState(now, s, sessions, open, daysOff)
                    }
                }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _messages = MutableSharedFlow<TodayMessage>(extraBufferCapacity = 4)
    val messages: SharedFlow<TodayMessage> = _messages

    // Batida: um toque por vez, e um intervalo depois dela para o segundo toque não virar a ação oposta.
    private val punchGate = ActionGate({ container.clock.instant() }, PUNCH_COOLDOWN)
    private val _punchBusy = MutableStateFlow(false)
    val punchBusy: StateFlow<Boolean> = _punchBusy.asStateFlow()

    // Fechamento da sessão esquecida: só enquanto está em andamento.
    private val closeGate = ActionGate({ container.clock.instant() })
    private val _closeBusy = MutableStateFlow(false)
    val closeBusy: StateFlow<Boolean> = _closeBusy.asStateFlow()

    /** Manda a entrada ou a saída conforme o botão atual; ignora se desabilitado ou ocupado. */
    fun punch() {
        val action = when (state.value?.button) {
            PunchButton.ENTER -> PunchAction.IN
            PunchButton.EXIT -> PunchAction.OUT
            PunchButton.DISABLED, null -> return
        }
        submit(action)
    }

    fun retry(action: PunchAction) = submit(action)

    private fun submit(action: PunchAction) {
        runGuarded(punchGate, _punchBusy) {
            try {
                repo.punch(action)
            } catch (e: PunchException) {
                _messages.tryEmit(TodayMessage(e.error.message, action))
            }
        }
    }

    /** Fecha a sessão esquecida no horário [endedAt] do dia em que ela começou. */
    fun closeForgotten(endedAt: LocalTime, note: String) {
        val forgotten = state.value?.forgotten ?: return
        runGuarded(closeGate, _closeBusy) {
            val s: Settings = repo.settings()
            val day = localDateOf(forgotten.startedAt, s)
            val instant = day.atTime(endedAt).atZone(s.zoneId).toInstant()
            try {
                repo.closeForgotten(forgotten.id, instant, note)
            } catch (e: PunchException) {
                _messages.tryEmit(TodayMessage(e.error.message, null))
            }
        }
    }

    /**
     * Executa [block] se o [gate] estiver livre. [busy] fica true durante a execução e o
     * cooldown do gate, para a UI desabilitar o botão.
     */
    private fun runGuarded(gate: ActionGate, busy: MutableStateFlow<Boolean>, block: suspend () -> Unit) {
        if (!gate.tryAcquire()) return
        busy.value = true
        viewModelScope.launch {
            try {
                block()
            } finally {
                gate.release()
                delay(gate.cooldown.toMillis())
                busy.value = false
            }
        }
    }

    private companion object {
        val PUNCH_COOLDOWN: Duration = Duration.ofMillis(1500)
    }
}
