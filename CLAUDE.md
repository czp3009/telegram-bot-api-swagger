# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin Multiplatform tool that generates OpenAPI/Swagger specifications from the official Telegram Bot API
documentation. Pipeline: fetch HTML → parse methods/objects → generate OpenAPI 3.0 JSON.

## Build Commands

```bash
# Linux
./gradlew :generator:runReleaseExecutableLinuxX64

# macOS (Intel)
./gradlew :generator:runReleaseExecutableMacosX64

# macOS (Apple Silicon)
./gradlew :generator:runReleaseExecutableMacosArm64

# Windows
gradlew.bat :generator:runReleaseExecutableMingwX64
```

**Environment Variables:**

- `HTTP_PROXY` / `HTTPS_PROXY`: Configure proxy for fetching documentation (e.g., `http://proxy:port`)

## Architecture

Linear pipeline: `fetch` → `parse` → `generate` → `write`

### Core Components (`generator/src/commonMain/kotlin/com/hiczp/telegram/bot/api/generator/`)

| File                    | Purpose                                                                 |
|-------------------------|-------------------------------------------------------------------------|
| **Main.kt**             | Entry point, orchestrates pipeline with coroutines                      |
| **DocumentFetcher.kt**  | Fetches HTML via Ktor/cURL, supports proxy config                       |
| **DocumentParser.kt**   | HTML parsing with ksoup, two-pass parsing (objects first, then methods) |
| **SwaggerGenerator.kt** | Generates OpenAPI 3.0 JSON with `oneOf` for union types                 |
| **Platform.kt**         | Expected functions for platform-specific file operations                |

### Data Models (in DocumentParser.kt)

- `Type` sealed class: `Simple` (e.g., "String") or `Generic` (e.g., "Array<Message>")
- `Object`: name, description, fields, union type info
- `Method`: name, description, parameters, return type, HTTP method
- `Field`/`Parameter`: name, type, required flag, description

### Multiplatform Source Sets

```
commonMain (shared logic)
    └── nativeMain (POSIX file operations)
            ├── unixMain (Linux + macOS with mkdir permissions)
            │     ├── linuxX64Main
            │     ├── macosX64Main
            │     └── macosArm64Main
            └── mingwX64Main (Windows)
```

## Key Implementation Details

- **Two-pass parsing**: Objects parsed first because methods need object references for HTTP method detection
- **Union type detection**: Objects with "can be one of" in description become `oneOf` schemas with discriminators
- **Discriminator detection**: Fields with "always \"value\"" or "must be *value*" patterns become discriminators
- **HTTP method logic**: Methods with `InputFile`/`InputMedia` parameters → POST; methods starting with "get" → GET;
  else POST
- **Nested file detection**: Recursively checks union subtypes for file fields to determine multipart/form-data
- **Return type extraction**: Multiple regex patterns match "Returns X on success", "X is returned", etc.
- **HTML to Markdown**: DOM traversal converts `<a>` to `[text](url)`, preserves links with base URL

## Dependencies

- `ksoup`: HTML parsing (Kotlin Jsoup wrapper)
- `ktor-client-curl`: HTTP client with native cURL engine
- `kotlinx-openapi-bindings`: OpenAPI 3.0 serialization
- `kotlin-logging`: Logging abstraction

## Output

`generator/swagger/telegram-bot-api.json` - Complete OpenAPI 3.0 spec with all methods, objects, and schemas.

## CI/CD

GitHub Actions runs daily at 00:05 UTC on Ubuntu with JDK 21 (GraalVM). Extracts API version from generated JSON and
creates tagged releases when version changes.
