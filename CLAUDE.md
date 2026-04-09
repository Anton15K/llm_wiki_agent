# LLM Wiki Agent

Personal LLM Wiki service — a Kotlin + Spring Boot MCP server that provides content extraction and wiki management tools. Claude Code is the brain; this server is the toolkit.

## Tech Stack

- **Language:** Kotlin, JVM 21+
- **Build:** Gradle Kotlin DSL
- **Framework:** Spring Boot 3.4+ with `spring-ai-starter-mcp-server` (STDIO)
- **Extraction:** Jsoup (URLs), Apache Tika 3.x (PDF/docs), OpenAI Whisper API (audio/video)
- **Config:** Jackson YAML + Kotlin module for frontmatter parsing

## Build & Run

```bash
./gradlew bootJar                # Build fat JAR
./gradlew test                   # Run tests
java -jar build/libs/wiki-agent.jar  # Run server (STDIO mode)
```

Register as MCP server:
```bash
claude mcp add wiki-agent -- java -jar build/libs/wiki-agent.jar
```

## Project Structure

```
src/main/kotlin/com/wiki/agent/
├── WikiAgentApplication.kt       # @SpringBootApplication
├── config/WikiProperties.kt      # @ConfigurationProperties
├── tool/
│   ├── ExtractionTools.kt        # wiki_extract, wiki_transcribe
│   └── WikiTools.kt              # Wiki CRUD + search tools
├── service/
│   ├── WikiService.kt            # Filesystem ops, index, log, search
│   └── ExtractionService.kt      # Dispatch extraction by type
├── extract/
│   ├── Extractor.kt              # Interface + SourceDocument data class
│   ├── UrlExtractor.kt           # Jsoup + Tika fallback
│   ├── PdfExtractor.kt           # Tika PDF
│   ├── TextExtractor.kt          # Passthrough .md/.txt
│   ├── AudioExtractor.kt         # Whisper API via HttpClient
│   └── VideoExtractor.kt         # ffmpeg → AudioExtractor
└── model/SourceDocument.kt       # Data class (title, content, metadata)
```

## Architecture

- The MCP server makes **zero LLM calls** — all intelligence is in Claude Code
- Communication via STDIO (MCP protocol)
- All logging goes to `logs/wiki-agent.log`, never stdout (would break STDIO)
- `spring.main.web-application-type=none` and `banner-mode=off`

## Key Directories

- `wiki/` — The wiki itself (Obsidian-compatible vault). Contains `index.md` and `log.md`
- `raw/` — Immutable raw sources stored after extraction

## Conventions

- **Page names:** kebab-case.md (e.g., `machine-learning-basics.md`)
- **Frontmatter:** YAML between `---` delimiters with at minimum `title`, `created`, `updated`, `tags`
- **Links:** Obsidian-style `[[page-name]]` wikilinks
- **Index:** `wiki/index.md` — master table of contents, organized by category
- **Log:** `wiki/log.md` — append-only changelog with timestamps

## MCP Tools Reference

| Tool | Purpose |
|------|---------|
| `wiki_extract` | Extract content from URL, PDF, or text file; saves raw to `raw/` |
| `wiki_transcribe` | Transcribe audio/video via Whisper; saves transcript to `raw/` |
| `wiki_write_page` | Create or overwrite a wiki page |
| `wiki_read_page` | Read a wiki page |
| `wiki_delete_page` | Delete a page (index.md and log.md are protected) |
| `wiki_list_pages` | List all pages with titles and sizes |
| `wiki_update_index` | Overwrite index.md |
| `wiki_append_log` | Append timestamped entry to log.md |
| `wiki_search` | Case-insensitive keyword search across all pages |
| `wiki_status` | Page count, source count, last log entry, wiki size |

## Implementation Phases

1. **Scaffolding** — Gradle project, dependencies, application entry point, config, initial wiki/raw dirs
2. **Wiki Service + Tools** — WikiService filesystem CRUD, WikiTools @Tool methods, basic MCP test
3. **Content Extraction** — Extractor interface, Text/URL/PDF extractors, ExtractionService, wiki_extract tool
4. **Audio/Video** — AudioExtractor (Whisper), VideoExtractor (ffmpeg), wiki_transcribe tool
5. **Schema + Polish** — CLAUDE.md refinement, end-to-end testing, MCP registration
