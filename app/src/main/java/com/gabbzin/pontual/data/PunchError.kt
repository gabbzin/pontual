package com.gabbzin.pontual.data

import java.time.LocalTime

sealed class PunchError(val message: String) {
    data class OutsideWindow(val from: LocalTime, val to: LocalTime) :
        PunchError("Fora do horário: batidas só entre $from e $to.")

    data object ForgottenOpenSession :
        PunchError("Existe uma sessão esquecida aberta. Feche-a manualmente.")

    data object NoteRequired : PunchError("Informe uma observação.")

    data object InvalidRange :
        PunchError("A saída precisa ser depois da entrada, no mesmo dia e dentro do horário.")

    data object InFuture : PunchError("Não dá pra lançar horário no futuro.")

    data object Overlap : PunchError("Esse período se sobrepõe a outra sessão.")

    data object NotOpen : PunchError("Essa sessão já está fechada.")

    data object NotManual : PunchError("Só sessões lançadas manualmente podem ser apagadas.")
}

class PunchException(val error: PunchError) : Exception(error.message)
