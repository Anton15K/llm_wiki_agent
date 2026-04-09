# LLM Wiki Agent

A personal knowledge management system that turns raw sources — URLs, PDFs, audio, video — into an interlinked wiki. Built as an MCP server in Kotlin + Spring Boot, it pairs with Claude Code: the server is the toolkit, Claude is the brain.

Inspired by [Andrej Karpathy's pattern](https://x.com/karpathy/status/1882188384898441488) of using LLMs to incrementally build a personal wiki instead of RAG.

## How It Works

```
Claude Code (LLM — all intelligence)
    │  MCP protocol (STDIO)
    ▼
Wiki Agent Server (toolkit — zero LLM calls)
    ├── Content extraction (URL, PDF, text, audio, video)
    ├── Wiki management (CRUD, search, index, log)
    │
    ▼ filesystem
wiki/  (Obsidian vault)        raw/  (immutable sources)
```

You give Claude Code a URL, PDF, or audio file. It calls the server to extract content, then uses its own intelligence to distill it into wiki pages with proper structure, cross-links, and metadata. The wiki is an Obsidian-compatible vault you can browse and search.

## Prerequisites

- **Java 21+** — [Amazon Corretto](https://aws.amazon.com/corretto/) or any JDK 21+
- **Claude Code** — [Installation guide](https://docs.anthropic.com/en/docs/claude-code/overview)
- **ffmpeg** (optional) — required only for video transcription (`brew install ffmpeg`)
- **OpenAI API key** (optional) — required only for audio/video transcription

## Quick Start

### 1. Build

```bash
git clone <repo-url> && cd llm_wiki_agent
./gradlew bootJar
```

This produces `build/libs/wiki-agent.jar` (~70MB, includes Apache Tika).

### 2. Register with Claude Code

```bash
claude mcp add -s user wiki-agent \
  -- java -jar /absolute/path/to/llm_wiki_agent/build/libs/wiki-agent.jar
```

Use `-s user` to make the server available in all projects. Use `-s project` for the current project only.

### 3. Configure paths (recommended)

By default, the wiki and raw directories are created relative to the working directory. For a persistent setup, set absolute paths in `~/.claude.json` under the `mcpServers` entry:

```json
{
  "mcpServers": {
    "wiki-agent": {
      "type": "stdio",
      "command": "java",
      "args": ["-jar", "/absolute/path/to/wiki-agent.jar"],
      "env": {
        "WIKI_PATH": "/absolute/path/to/wiki",
        "RAW_PATH": "/absolute/path/to/raw",
        "OPENAI_API_KEY": "sk-..."
      }
    }
  }
}
```

### 4. Verify

```bash
claude mcp list
# wiki-agent: java -jar ... - ✓ Connected
```

### 5. Open in Obsidian (optional)

Open your `wiki/` directory as an Obsidian vault to get a graph view of all your pages and their `[[wikilinks]]`.

## MCP Tools

### Content Extraction

| Tool | Description |
|------|-------------|
| `wiki_extract` | Extract content from a URL, PDF, or text file. Auto-detects source type. Saves raw extraction to `raw/`. |
| `wiki_transcribe` | Transcribe audio/video via OpenAI Whisper. For video, extracts audio with ffmpeg first. Requires `OPENAI_API_KEY`. |

### Wiki Management

| Tool | Description |
|------|-------------|
| `wiki_write_page` | Create or overwrite a wiki page. Content should include YAML frontmatter. |
| `wiki_read_page` | Read a wiki page by filename. |
| `wiki_delete_page` | Delete a page. `index.md` and `log.md` are protected. |
| `wiki_list_pages` | List all pages with titles and file sizes. |
| `wiki_update_index` | Overwrite `index.md` (master table of contents). |
| `wiki_append_log` | Append a timestamped entry to `log.md`. |
| `wiki_search` | Case-insensitive keyword search across all pages. Returns matching lines with context. |
| `wiki_status` | Page count, raw source count, last log entry, total wiki size. |

## Usage Examples

Once the server is connected, talk to Claude Code naturally:

**Ingest a URL:**
> "Read this article and add it to the wiki: https://example.com/interesting-article"

**Ingest a PDF:**
> "Extract this paper and create wiki pages from it: /path/to/paper.pdf"

**Transcribe a lecture:**
> "Transcribe this lecture and summarize the key points into wiki pages: /path/to/lecture.mp4"

**Search your knowledge base:**
> "What do I have in my wiki about transformer architectures?"

**Maintain the wiki:**
> "List all pages and check for any that are orphaned or could use cross-links"

## Wiki Conventions

- **Page names:** `kebab-case.md` (e.g., `transformer-architecture.md`)
- **Frontmatter:** YAML between `---` delimiters with at minimum `title`, `created`, `updated`, `tags`
- **Links:** Obsidian-style `[[page-name]]` wikilinks
- **Index:** `wiki/index.md` — master table of contents organized by category
- **Log:** `wiki/log.md` — append-only changelog with timestamps

Example page:

```markdown
---
title: Transformer Architecture
created: 2026-04-09
updated: 2026-04-09
tags: [deep-learning, nlp, architecture]
source: https://arxiv.org/abs/1706.03762
---

# Transformer Architecture

The transformer is a neural network architecture based entirely on
[[attention-mechanism|attention mechanisms]], dispensing with recurrence
and convolutions entirely.

## See Also

- [[self-attention]]
- [[bert]]
- [[gpt]]
```

## Configuration

All configuration is via environment variables or `application.yml` overrides:

| Variable | Default | Description |
|----------|---------|-------------|
| `WIKI_PATH` | `./wiki` | Path to the wiki directory (Obsidian vault) |
| `RAW_PATH` | `./raw` | Path to raw extracted sources |
| `OPENAI_API_KEY` | _(empty)_ | OpenAI API key for Whisper transcription |

## Project Structure

```
src/main/kotlin/com/wiki/agent/
├── WikiAgentApplication.kt       # Spring Boot entry point
├── config/WikiProperties.kt      # Configuration properties
├── tool/
│   ├── ExtractionTools.kt        # wiki_extract, wiki_transcribe
│   └── WikiTools.kt              # Wiki CRUD + search tools
├── service/
│   ├── WikiService.kt            # Filesystem operations
│   └── ExtractionService.kt      # Extraction dispatch
├── extract/
│   ├── Extractor.kt              # Interface
│   ├── UrlExtractor.kt           # Jsoup HTML → text
│   ├── PdfExtractor.kt           # Tika PDF → text
│   ├── TextExtractor.kt          # Passthrough .md/.txt
│   ├── AudioExtractor.kt         # Whisper API
│   └── VideoExtractor.kt         # ffmpeg → Whisper
└── model/SourceDocument.kt       # Extraction result data class
```

## Development

```bash
./gradlew test          # Run tests (11 tests)
./gradlew bootJar       # Build fat JAR
./gradlew compileKotlin # Compile only
```

**Note:** The project requires JDK 21 for both building and running. If your default `java` points to a different version, set `JAVA_HOME` or update `gradle.properties`:

```properties
org.gradle.java.home=/path/to/jdk-21
```

## Architecture Notes

- The MCP server makes **zero LLM calls** — all intelligence lives in Claude Code
- Communication is STDIO only (`spring.main.web-application-type=none`)
- All logging goes to `logs/wiki-agent.log`, never stdout (stdout is reserved for MCP protocol)
- Raw sources in `raw/` are immutable — they preserve the original extraction with metadata
- The wiki in `wiki/` is a standard Obsidian vault and can be opened directly

## License

MIT
