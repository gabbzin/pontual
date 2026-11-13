package com.gabbzin.pontual.calc

import com.gabbzin.pontual.model.Settings

private const val END_OF_DAY_MINUTES = 23 * 60 + 59

/**
 * Regras de consistência da configuração, compartilhadas pelo formulário e pelo import de backup.
 * Retorna a mensagem em português do primeiro problema, ou null se a configuração é válida.
 * A janela válida [início − tolerância, fim + tolerância] não pode sair do mesmo dia.
 */
fun settingsValidationError(s: Settings): String? {
    val startMinutes = s.windowStart.hour * 60 + s.windowStart.minute
    val endMinutes = s.windowEnd.hour * 60 + s.windowEnd.minute
    return when {
        s.dailyMinutes !in 1..1440 -> "Jornada inválida."
        s.toleranceMinutes < 0 -> "Tolerância inválida."
        startMinutes >= endMinutes -> "O início do horário precisa ser antes do fim."
        startMinutes - s.toleranceMinutes < 0 ->
            "Com essa tolerância, o início do horário passa para o dia anterior."
        endMinutes.toLong() + s.toleranceMinutes > END_OF_DAY_MINUTES ->
            "Com essa tolerância, o fim do horário passa para o dia seguinte."
        s.workdays.isEmpty() -> "Escolha pelo menos um dia útil."
        else -> null
    }
}
