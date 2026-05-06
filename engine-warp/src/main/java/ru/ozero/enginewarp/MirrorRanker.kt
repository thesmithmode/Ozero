package ru.ozero.enginewarp

interface MirrorRanker {
    fun order(mirrors: List<String>): List<String>
    fun recordSuccess(mirror: String)
    fun recordFailure(mirror: String)
}

object NoopMirrorRanker : MirrorRanker {
    override fun order(mirrors: List<String>): List<String> = mirrors.shuffled()
    override fun recordSuccess(mirror: String) = Unit
    override fun recordFailure(mirror: String) = Unit
}
