package ru.ozero.app.ui.strategy

data class DomainList(
    val id: String,
    val name: String,
    val domains: List<String>,
    val isActive: Boolean = true,
    val isBuiltIn: Boolean = false,
)
