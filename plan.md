# LLM Wiki Agent — Implementation Plan

## Context

Building a personal LLM Wiki service based on Andrej Karpathy's pattern. Instead of RAG, an LLM incrementally builds and maintains a persistent wiki (interlinked markdown files) from raw sources. The service runs as an **MCP server** that Claude Code connects to — Claude Code is the brain, the server is a toolkit for content extraction and wiki file operations.

## Architecture

```
Claude Code (LLM — all intelligence)
    │  STDIO (MCP protocol)
    ▼
Kotlin + Spring Boot MCP Server (toolkit — no LLM calls)
    │
    ├── Extraction tools (URL, PDF, text, audio, video)
    ├── Wiki management tools (CRUD pages, index, log, search)
    └── MCP Resources (schema, index, log)
    │
    ▼ filesystem
wiki/  (Obsidian vault)     raw/  (immutable sources)
```

**Key insight:** The server makes ZERO LLM calls. Claude Code has vision (handles images directly), and Whisper is a simple REST call for audio/video. No Anthropic/OpenAI SDK needed in the server.

## Tech Stack

| Layer | Choice | Why |
|-------|--------|-----|
| Language | Kotlin (JVM 21+) | Type safety, sealed classes, coroutines |
| Build | Gradle Kotlin DSL | Standard for Kotlin + Spring Boot |
| Framework | Spring Boot 3.4+ | DI, auto-config, minimal boilerplate |
| MCP | `spring-ai-starter-mcp-server` (STDIO) | `@Tool` annotations, auto JSON schema |
| URL extraction | Jsoup | HTML fetch + clean text |
| PDF/docs | Apache Tika 3.x | PDF, DOCX, HTML, 1000+ formats |
| Audio transcription | OpenAI Whisper API | Direct HTTP via `java.net.http.HttpClient` |
| Video | ffmpeg (subprocess) | Extract audio track, then Whisper |
| YAML | Jackson YAML + kotlin module | Frontmatter parsing |

## Directory Structure

```
llm_wiki_agent/
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/wrapper/
├── CLAUDE.md                              # Schema for Claude Code
├── src/main/kotlin/com/wiki/agent/
│   ├── WikiAgentApplication.kt            # @SpringBootApplication
│   ├── config/
│   │   └── WikiProperties.kt             # @ConfigurationProperties
│   ├── tool/
│   │   ├── ExtractionTools.kt            # wiki_extract, wiki_transcribe
│   │   └── WikiTools.kt                  # All wiki CRUD + search tools
│   ├── service/
│   │   ├── WikiService.kt                # Filesystem ops, index, log, search
│   │   └── ExtractionService.kt          # Dispatch extraction by type
│   ├── extract/
│   │   ├── Extractor.kt                  # Interface + SourceDocument data class
│   │   ├── UrlExtractor.kt               # Jsoup + Tika fallback
│   │   ├── PdfExtractor.kt               # Tika PDF
│   │   ├── TextExtractor.kt              # Passthrough .md/.txt
│   │   ├── AudioExtractor.kt             # Whisper API via HttpClient
│   │   └── VideoExtractor.kt             # ffmpeg → AudioExtractor
│   └── model/
│       └── SourceDocument.kt             # Data class (title, content, metadata)
├── src/main/resources/
│   └── application.yml                    # MCP STDIO config, paths, logging
├── wiki/                                  # THE WIKI (Obsidian vault)
│   ├── index.md
│   └── log.md
└── raw/                                   # Raw sources (immutable)
    └── assets/
```

## MCP Tools

### Extraction Tools (`ExtractionTools.kt`)
- **`wiki_extract(source: String, type: String?)`** — Auto-detects source type (URL, PDF path, text path). Saves raw source to `raw/`. Returns extracted text content + metadata (title, word count, source URL).
- **`wiki_transcribe(filePath: String, language: String?)`** — Transcribes audio/video via OpenAI Whisper API. For video: ffmpeg extracts audio first. Saves transcript to `raw/`.

### Wiki Tools (`WikiTools.kt`)
- **`wiki_write_page(path: String, content: String)`** — Create/overwrite a wiki page. Validates `.md` extension.
- **`wiki_read_page(path: String)`** — Read a wiki page.
- **`wiki_delete_page(path: String)`** — Delete a page (protects index.md, log.md).
- **`wiki_list_pages()`** — List all pages with frontmatter titles and sizes.
- **`wiki_update_index(content: String)`** — Overwrite index.md.
- **`wiki_append_log(operation: String, subject: String, details: String)`** — Auto-timestamps, appends to log.md.
- **`wiki_search(query: String, limit: Int?)`** — Case-insensitive keyword search across all pages. Returns filenames + matching lines with context.
- **`wiki_status()`** — Page count, source count, last log entry, wiki size.

### Images
No server-side tool needed. Claude Code reads images directly via vision. The user points Claude Code at an image file, it analyzes it and creates wiki pages.

## Configuration

**`application.yml`** — Critical for MCP STDIO:
```yaml
spring:
  main:
    web-application-type: none    # No web server
    banner-mode: off              # Don't pollute stdout
  ai:
    mcp:
      server:
        stdio: true
        name: wiki-agent
        version: 1.0.0

wiki:
  wiki-path: ${WIKI_PATH:./wiki}
  raw-path: ${RAW_PATH:./raw}
  openai-api-key: ${OPENAI_API_KEY:}

logging:
  file:
    name: logs/wiki-agent.log     # All logging to file, never stdout
  level:
    root: WARN
    com.wiki.agent: INFO
```

## Claude Code Integration

After `./gradlew bootJar`, register as MCP server:
```bash
claude mcp add wiki-agent -- java -jar build/libs/wiki-agent.jar
```

Or in `.claude/settings.json`:
```json
{
  "mcpServers": {
    "wiki-agent": {
      "command": "java",
      "args": ["-jar", "/Users/antonkogun/Desktop/llm_wiki_agent/build/libs/wiki-agent.jar"],
      "env": {
        "WIKI_PATH": "/Users/antonkogun/Desktop/llm_wiki_agent/wiki",
        "RAW_PATH": "/Users/antonkogun/Desktop/llm_wiki_agent/raw",
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

## CLAUDE.md Schema (for Claude Code)

The `CLAUDE.md` file teaches Claude Code how to operate the wiki. It will define:
- Wiki conventions (frontmatter format, linking style, page categories)
- Ingest workflow (extract → discuss → create/update pages → update index → append log)
- Query workflow (search → read relevant pages → synthesize → optionally file answer as page)
- Lint workflow (list all pages → check for orphans, contradictions, gaps → report/fix)
- Page naming conventions (kebab-case.md)
- Index.md format
- Log.md format

## Implementation Order

### Phase 1: Project Scaffolding
1. Initialize Gradle project with Kotlin DSL
2. Add all dependencies (Spring Boot, Spring AI MCP, Tika, Jsoup, Jackson)
3. Create `WikiAgentApplication.kt`, `WikiProperties.kt`, `application.yml`
4. Create `wiki/` and `raw/` directories with initial `index.md` and `log.md`

### Phase 2: Wiki Service + Tools
5. Implement `WikiService.kt` — all filesystem CRUD, search, index/log management
6. Implement `WikiTools.kt` — all `@Tool` annotated methods delegating to WikiService
7. Build and test basic MCP connection

### Phase 3: Content Extraction
8. Define `Extractor` interface and `SourceDocument` data class
9. Implement `TextExtractor` (passthrough for .md/.txt)
10. Implement `UrlExtractor` (Jsoup fetch + clean text extraction)
11. Implement `PdfExtractor` (Tika)
12. Implement `ExtractionService` (auto-detect and dispatch)
13. Implement `ExtractionTools.kt` — `wiki_extract` tool

### Phase 4: Audio/Video
14. Implement `AudioExtractor` (Whisper API via HttpClient)
15. Implement `VideoExtractor` (ffmpeg subprocess → AudioExtractor)
16. Add `wiki_transcribe` tool to ExtractionTools

### Phase 5: Schema + Polish
17. Write comprehensive `CLAUDE.md` schema
18. End-to-end test: ingest a URL, ask a question, lint the wiki
19. Register as MCP server in Claude Code

## Verification

1. **Build:** `./gradlew bootJar` compiles without errors
2. **MCP connection:** `claude mcp add` + verify tools appear in Claude Code
3. **URL ingest:** Give Claude Code a URL, verify it extracts content and creates wiki pages
4. **PDF ingest:** Drop a PDF in, verify extraction and wiki update
5. **Audio ingest:** Provide an audio file, verify Whisper transcription and wiki update
6. **Query:** Ask a question about ingested content, verify Claude Code searches and answers
7. **Obsidian:** Open `wiki/` as an Obsidian vault, verify pages render and link correctly

## Risks & Mitigations

| Risk | Mitigation |
|------|------------|
| JVM startup ~2-3s | One-time per session; consider GraalVM native-image later |
| Tika jar ~50MB | Acceptable for desktop; can slim with specific parsers |
| Whisper 25MB limit | Split large files with ffmpeg (post-MVP) |
| STDIO stdout pollution | `banner-mode=off`, `web-application-type=none`, file-only logging |
