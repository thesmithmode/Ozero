package ru.ozero.security

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SecurityStateHolder {

    private val _compromised = MutableStateFlow<List<String>>(emptyList())
    val compromised: StateFlow<List<String>> = _compromised.asStateFlow()

    val isCompromised: Boolean get() = _compromised.value.isNotEmpty()

    fun signal(reasons: List<String>) {
        _compromised.value = reasons
    }
}
