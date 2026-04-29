package ru.ozero.app.ui.diag

interface DiagnosticsEngine {
    suspend fun runAll(socksPort: Int, onTestDone: (DiagResult) -> Unit = {}): List<DiagResult>
}

class DefaultDiagnosticsEngine : DiagnosticsEngine {
    override suspend fun runAll(socksPort: Int, onTestDone: (DiagResult) -> Unit): List<DiagResult> =
        DiagnosticsTester(socksPort = socksPort).runAll(onTestDone = onTestDone)
}
