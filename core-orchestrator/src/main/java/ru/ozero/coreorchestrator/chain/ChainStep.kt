package ru.ozero.coreorchestrator.chain

import ru.ozero.coreapi.EngineConfig
import ru.ozero.coreapi.EngineId

data class ChainStep(
    val engineId: EngineId,
    val config: EngineConfig,
)
