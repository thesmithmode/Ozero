package ru.ozero.app.ui.settings.engines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.enginemasque.MasqueProfileStore
import ru.ozero.enginemasque.MasqueRuntime
import ru.ozero.enginemasque.MasqueTransport
import javax.inject.Inject

data class MasqueSettingsUiState(
    val registered: Boolean = false,
    val transport: MasqueTransport = MasqueTransport.AUTO,
    val autoEnabled: Boolean = true,
    val busy: Boolean = false,
    val error: Boolean = false,
    val loaded: Boolean = false,
)

@HiltViewModel
class MasqueSettingsViewModel @Inject constructor(
    private val store: MasqueProfileStore,
    private val runtime: MasqueRuntime,
) : ViewModel() {
    private val busy = MutableStateFlow(false)
    private val error = MutableStateFlow(false)

    private val saved = store.settings().map {
        MasqueSettingsUiState(
            registered = it.profile != null,
            transport = it.transport,
            autoEnabled = it.autoEnabled,
            error = it.corrupt,
            loaded = true,
        )
    }.catch { emit(MasqueSettingsUiState(error = true, loaded = true)) }

    val state = combine(saved, busy, error) { current, running, failed ->
        current.copy(busy = running, error = current.error || failed)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MasqueSettingsUiState())

    fun register() = perform { store.save(runtime.register(acceptedTerms = true)) }

    fun clear() = perform { store.clear() }

    fun selectTransport(transport: MasqueTransport) = perform { store.setTransport(transport) }

    fun setAutoEnabled(enabled: Boolean) = perform { store.setAutoEnabled(enabled) }

    private fun perform(action: suspend () -> Unit) {
        if (!busy.compareAndSet(false, true)) return
        error.value = false
        viewModelScope.launch {
            try {
                action()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                error.value = true
            } finally {
                busy.value = false
            }
        }
    }
}
