package ru.ozero.app.ui.diag

interface DiagnosticsEngine {
    suspend fun runAll(socksPort: Int): List<DiagResult>
}

class DefaultDiagnosticsEngine : DiagnosticsEngine {
    override suspend fun runAll(socksPort: Int): List<DiagResult> =
        DiagnosticsTester(socksPort = socksPort).runAll()
}
