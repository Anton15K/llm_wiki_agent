package com.wiki.agent.extract

import com.wiki.agent.model.SourceDocument
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.nameWithoutExtension

@Component
class VideoExtractor(private val audioExtractor: AudioExtractor) : Extractor {

    private val log = LoggerFactory.getLogger(VideoExtractor::class.java)

    override fun extract(source: String): SourceDocument {
        return transcribe(Path.of(source), null)
    }

    fun transcribe(videoPath: Path, language: String?): SourceDocument {
        log.info("Extracting audio from video: {}", videoPath)

        val tempAudio = Files.createTempFile("wiki-audio-", ".mp3")
        try {
            val process = ProcessBuilder(
                "ffmpeg", "-i", videoPath.toString(),
                "-vn", "-acodec", "libmp3lame", "-q:a", "4",
                "-y", tempAudio.toString()
            )
                .redirectErrorStream(true)
                .start()

            val output = process.inputStream.bufferedReader().readText()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                throw RuntimeException("ffmpeg failed (exit $exitCode): $output")
            }

            val doc = audioExtractor.transcribe(tempAudio, language)
            return doc.copy(
                title = videoPath.nameWithoutExtension,
                source = videoPath.toAbsolutePath().toString(),
                type = "video",
                metadata = doc.metadata + mapOf("filename" to videoPath.fileName.toString()),
            )
        } finally {
            Files.deleteIfExists(tempAudio)
        }
    }
}
