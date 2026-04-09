package com.wiki.agent.extract

import com.wiki.agent.config.WikiProperties
import com.wiki.agent.model.SourceDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Path
import java.time.Duration
import java.util.UUID
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readBytes

@Component
class AudioExtractor(private val props: WikiProperties) : Extractor {

    private val log = LoggerFactory.getLogger(AudioExtractor::class.java)
    private val client = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(30))
        .build()

    companion object {
        private val REQUEST_TIMEOUT: Duration = Duration.ofMinutes(5)
    }

    override fun extract(source: String): SourceDocument {
        return transcribe(Path.of(source), null)
    }

    fun transcribe(audioPath: Path, language: String?): SourceDocument {
        val apiKey = props.openaiApiKey
        require(apiKey.isNotBlank()) { "OPENAI_API_KEY is required for audio transcription" }

        log.info("Transcribing audio: {}", audioPath)

        val boundary = UUID.randomUUID().toString()
        val audioBytes = audioPath.readBytes()
        val body = buildMultipartBody(boundary, audioPath.name, audioBytes, language)

        val request = HttpRequest.newBuilder()
            .uri(URI.create("https://api.openai.com/v1/audio/transcriptions"))
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "multipart/form-data; boundary=$boundary")
            .timeout(REQUEST_TIMEOUT)
            .POST(HttpRequest.BodyPublishers.ofByteArray(body))
            .build()

        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) {
            throw RuntimeException("Whisper API error ${response.statusCode()}: ${response.body()}")
        }

        // Parse JSON response - extract "text" field
        val text = extractJsonText(response.body())

        return SourceDocument(
            title = audioPath.nameWithoutExtension,
            content = text,
            source = audioPath.toAbsolutePath().toString(),
            type = "audio",
            metadata = buildMap {
                put("filename", audioPath.name)
                if (language != null) put("language", language)
            },
        )
    }

    private fun buildMultipartBody(boundary: String, filename: String, fileBytes: ByteArray, language: String?): ByteArray {
        val parts = mutableListOf<ByteArray>()

        // File part
        parts.add("--$boundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"$filename\"\r\nContent-Type: application/octet-stream\r\n\r\n".toByteArray())
        parts.add(fileBytes)
        parts.add("\r\n".toByteArray())

        // Model part
        parts.add("--$boundary\r\nContent-Disposition: form-data; name=\"model\"\r\n\r\nwhisper-1\r\n".toByteArray())

        // Language part (optional)
        if (language != null) {
            parts.add("--$boundary\r\nContent-Disposition: form-data; name=\"language\"\r\n\r\n$language\r\n".toByteArray())
        }

        // Response format
        parts.add("--$boundary\r\nContent-Disposition: form-data; name=\"response_format\"\r\n\r\ntext\r\n".toByteArray())

        parts.add("--$boundary--\r\n".toByteArray())

        return parts.fold(byteArrayOf()) { acc, bytes -> acc + bytes }
    }

    private fun extractJsonText(body: String): String {
        // When response_format=text, Whisper returns plain text directly
        return body.trim()
    }
}
