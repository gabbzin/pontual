package com.gabbzin.pontual.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gabbzin.pontual.AppContainer
import com.gabbzin.pontual.backup.InvalidBackupException
import com.gabbzin.pontual.calc.settingsValidationError
import com.gabbzin.pontual.model.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Formulário editável; só vira [Settings] ao salvar. */
data class SettingsForm(
    val settings: Settings = Settings(),
    val dailyMinutesText: String = settings.dailyMinutes.toString(),
    val toleranceText: String = settings.toleranceMinutes.toString(),
) {
    fun validate(): Result<Settings> {
        val daily = dailyMinutesText.toIntOrNull()
        val tol = toleranceText.toIntOrNull()
        if (daily == null || daily !in 1..1440) return Result.failure(IllegalArgumentException("Jornada inválida."))
        if (tol == null || tol !in 0..120) return Result.failure(IllegalArgumentException("Tolerância inválida."))
        val candidate = settings.copy(dailyMinutes = daily, toleranceMinutes = tol)
        settingsValidationError(candidate)?.let { return Result.failure(IllegalArgumentException(it)) }
        return when {
            settings.internshipStart == null -> Result.failure(IllegalArgumentException("Informe o início do estágio."))
            settings.balanceStart == null -> Result.failure(IllegalArgumentException("Informe o início do saldo."))
            else -> Result.success(candidate)
        }
    }
}

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _form = MutableStateFlow<SettingsForm?>(null)
    val form: StateFlow<SettingsForm?> = _form

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val messages: SharedFlow<String> = _messages

    init {
        viewModelScope.launch { _form.value = SettingsForm(container.repository.observeSettings().first()) }
    }

    fun update(transform: (SettingsForm) -> SettingsForm) {
        _form.value?.let { _form.value = transform(it) }
    }

    fun save() {
        val current = _form.value ?: return
        current.validate()
            .onFailure { _messages.tryEmit(it.message ?: "Configuração inválida.") }
            .onSuccess { s ->
                viewModelScope.launch {
                    try {
                        container.repository.saveSettings(s)
                        _form.value = SettingsForm(s)
                        _messages.tryEmit("Configuração salva.")
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        _messages.tryEmit("Erro inesperado: ${e.message ?: "erro desconhecido"}")
                    }
                }
            }
    }

    fun backupFileName(): String {
        val zone = _form.value?.settings?.zoneId ?: Settings().zoneId
        return "pontual-backup-${LocalDate.now(container.clock.withZone(zone))}.json"
    }

    fun export(uri: Uri, resolver: android.content.ContentResolver) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                resolver.openOutputStream(uri)?.use { container.backup.export(it) }
                    ?: error("Não foi possível abrir o arquivo.")
            }
            _messages.tryEmit("Backup exportado.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            _messages.tryEmit("Falha ao exportar: ${e.message ?: "erro desconhecido"}")
        }
    }

    fun import(uri: Uri, resolver: android.content.ContentResolver) = viewModelScope.launch {
        try {
            withContext(Dispatchers.IO) {
                resolver.openInputStream(uri)?.use { container.backup.import(it) }
                    ?: error("Não foi possível abrir o arquivo.")
            }
            _form.value = SettingsForm(container.repository.settings())
            _messages.tryEmit("Backup importado.")
        } catch (e: CancellationException) {
            throw e
        } catch (e: InvalidBackupException) {
            _messages.tryEmit(e.message.orEmpty())
        } catch (e: Exception) {
            _messages.tryEmit("Falha ao importar: ${e.message ?: "erro desconhecido"}")
        }
    }
}
