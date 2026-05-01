package ru.ozero.enginewarp

interface HttpClient {
    suspend fun postJson(url: String, body: String, userAgent: String): Result<String>
}
