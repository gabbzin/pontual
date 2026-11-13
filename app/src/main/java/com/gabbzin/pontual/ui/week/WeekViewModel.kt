package com.gabbzin.pontual.ui.week

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.calc.summarizeDays
import com.gabbzin.pontual.calc.totalBalance
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

/**
 * A semana exibida é guardada como deslocamento em semanas a partir da corrente (0 = corrente),
 * porque a semana corrente depende do fuso das configurações, só conhecido depois de carregá-las.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WeekViewModel(private val container: AppContainer) : ViewModel() {
    private val repo = container.repository
    private val offset = MutableStateFlow(0L)

    val atCurrentWeek: StateFlow<Boolean> = offset.map { it == 0L }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val state: StateFlow<WeekState?> = combine(repo.observeSettings(), offset) { s, off -> s to off }
        .flatMapLatest { (s, off) ->
            val today = LocalDate.now(container.clock.withZone(s.zoneId))
            val monday = weekStart(today).plusWeeks(off)
            val sunday = monday.plusDays(6)
            combine(repo.observeSessions(monday, sunday, s.zoneId), repo.observeDaysOff()) { sessions, daysOff ->
                val days = summarizeDays(monday, sunday, sessions, daysOff, container.clock.instant(), s)
                WeekState(monday, days, totalBalance(days))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun previous() {
        offset.value -= 1
    }

    fun next() {
        if (offset.value < 0) offset.value += 1
    }
}
