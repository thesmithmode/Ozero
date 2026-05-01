package ru.ozero.app.ui.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.ozero.app.settings.UserFlagsRepository
import ru.ozero.app.ui.settings.LocaleApplier
import ru.ozero.enginescore.settings.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val userFlags: UserFlagsRepository,
    private val bootstrap: FirstRunBootstrap,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(OnboardingState())
    val state: StateFlow<OnboardingState> = _state.asStateFlow()

    val currentLocale: StateFlow<String?> = settingsRepository.settings
        .map { it.uiLocaleTag }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS),
            initialValue = null,
        )

    fun onLocaleSelect(tag: String?) {
        viewModelScope.launch {
            settingsRepository.setUiLocaleTag(tag)
            LocaleApplier.apply(tag)
        }
    }

    fun onNext() {
        val current = _state.value.pageIndex
        if (current < TOTAL_PAGES - 1) {
            _state.value = _state.value.copy(pageIndex = current + 1)
        }
    }

    fun onSkip() {
        _state.value = _state.value.copy(completed = true)
        viewModelScope.launch {
            userFlags.markOnboardingCompleted()
        }
    }

    fun onFinish() {
        _state.value = _state.value.copy(completed = true)
        viewModelScope.launch {
            userFlags.markOnboardingCompleted()
            bootstrap.runIfFirstStart()
        }
    }

    companion object {
        const val TOTAL_PAGES: Int = 4
        private const val STOP_TIMEOUT_MS = 5_000L
    }
}

data class OnboardingState(
    val pageIndex: Int = 0,
    val completed: Boolean = false,
)
