package com.wiseowlflow.workflow.ai

import io.github.oshai.kotlinlogging.KotlinLogging
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val logger = KotlinLogging.logger {}

@Serializable
data class OllamaGenerateRequest(
    val model: String,
    val prompt: String,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaOptions(
    val temperature: Double? = null,
    val num_predict: Int? = null,
    val top_p: Double? = null,
    val top_k: Int? = null
)

@Serializable
data class OllamaGenerateResponse(
    val model: String,
    val response: String,
    val done: Boolean,
    val context: List<Int>? = null,
    val total_duration: Long? = null,
    val load_duration: Long? = null,
    val prompt_eval_count: Int? = null,
    val eval_count: Int? = null
)

@Serializable
data class OllamaChatRequest(
    val model: String,
    val messages: List<OllamaChatMessage>,
    val stream: Boolean = false,
    val options: OllamaOptions? = null
)

@Serializable
data class OllamaChatMessage(
    val role: String,
    val content: String
)

@Serializable
data class OllamaChatResponse(
    val model: String,
    val message: OllamaChatMessage,
    val done: Boolean
)

class OllamaClient(
    private val baseUrl: String = "http://localhost:11434",
    private val defaultModel: String = "llama3.2"
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        engine {
            requestTimeout = 120_000 // 2 minutes for AI responses
        }
    }

    suspend fun generate(
        prompt: String,
        model: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 1000
    ): String {
        logger.debug { "Generating response with Ollama (model: ${model ?: defaultModel})" }

        val request = OllamaGenerateRequest(
            model = model ?: defaultModel,
            prompt = prompt,
            stream = false,
            options = OllamaOptions(
                temperature = temperature,
                num_predict = maxTokens
            )
        )

        val response = client.post("$baseUrl/api/generate") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throw OllamaException("Ollama request failed with status ${response.status}")
        }

        val result = response.body<OllamaGenerateResponse>()
        return result.response
    }

    suspend fun chat(
        messages: List<OllamaChatMessage>,
        model: String? = null,
        temperature: Double = 0.7,
        maxTokens: Int = 1000
    ): String {
        logger.debug { "Chat with Ollama (model: ${model ?: defaultModel})" }

        val request = OllamaChatRequest(
            model = model ?: defaultModel,
            messages = messages,
            stream = false,
            options = OllamaOptions(
                temperature = temperature,
                num_predict = maxTokens
            )
        )

        val response = client.post("$baseUrl/api/chat") {
            contentType(ContentType.Application.Json)
            setBody(request)
        }

        if (!response.status.isSuccess()) {
            throw OllamaException("Ollama chat request failed with status ${response.status}")
        }

        val result = response.body<OllamaChatResponse>()
        return result.message.content
    }

    suspend fun isAvailable(): Boolean {
        return try {
            val response = client.get("$baseUrl/api/tags")
            response.status.isSuccess()
        } catch (e: Exception) {
            logger.debug { "Ollama not available: ${e.message}" }
            false
        }
    }

    fun close() {
        client.close()
    }

    companion object {
        fun fromEnvironment(): OllamaClient {
            val baseUrl = System.getenv("OLLAMA_BASE_URL") ?: "http://localhost:11434"
            val model = System.getenv("OLLAMA_MODEL") ?: "llama3.2"
            return OllamaClient(baseUrl, model)
        }
    }
}

class OllamaException(message: String, cause: Throwable? = null) : Exception(message, cause)
