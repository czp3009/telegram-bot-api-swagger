# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

This is a Kotlin Multiplatform tool that automatically generates OpenAPI/Swagger specifications from the official
Telegram Bot API documentation. It fetches HTML from `https://core.telegram.org/bots/api`, parses methods and objects,
and outputs a complete OpenAPI 3.0 JSON specification.

## Build Commands

Run the generator on your platform:

**Environment Variables:**

- `HTTP_PROXY` / `HTTPS_PROXY`: Configure proxy for fetching documentation (e.g., `http://proxy:port`)

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

## Architecture

The codebase is organized as a linear pipeline: `fetch` → `parse` → `generate` → `write`.

### Core Components (in `generator/src/commonMain/kotlin/com/hiczp/telegram/bot/api/generator/`)

- **Main.kt**: Entry point orchestrating the pipeline. Uses coroutines (Dispatchers.Default) to execute the flow.
- **DocumentFetcher.kt**: Fetches HTML from Telegram's API. Supports proxy configuration via `HTTP_PROXY`/`HTTPS_PROXY`
  environment variables. Uses Ktor client with cURL engine and disables SSL verification.
- **DocumentParser.kt**: Complex HTML parsing logic using ksoup. Parses:
    - API version from "Recent changes" section
    - API methods (lowercase names like `sendMessage`) with parameters
    - API objects (uppercase names like `Message`) with fields
    - Union types with automatic discriminator detection
    - Extracts return types from HTML descriptions using regex patterns
    - Converts HTML to Markdown while preserving links
  - Uses two-pass parsing: first pass collects all objects, second pass parses methods (which need object references)
- **SwaggerGenerator.kt**: Generates OpenAPI 3.0 compliant JSON. Handles union types with `oneOf`, determines HTTP
  method (GET vs POST) based on file parameters, and includes error response schemas.
- **Platform.kt**: Platform-specific implementations for file operations (Unix vs Windows directory creation with
  permissions).
- **Data Models**: Defined inline within `DocumentParser.kt` as sealed classes (`Type`, `Object`, `Method`, `Field`,
  `Parameter`)
  supporting nested arrays and union types.

### Multiplatform Structure

Single common source code (`commonMain`) with platform-specific implementations for:

- `unixMain`: Shared between Linux and macOS
- `nativeMain`: Base for all native targets
- Platform-specific `linuxX64Main`, `macosX64Main`, `macosArm64Main`, `mingwX64Main`

### Output

Generates `generator/swagger/telegram-bot-api.json` - OpenAPI 3.0 specification with all API methods, data objects,
proper endpoints, parameters, and response schemas.

## Key Patterns

- **HTML Parsing**: Uses ksoup (Kotlin Jsoup wrapper) for robust DOM manipulation, not fragile regex. Table parsing has
  flexible header detection.
- **Type System**: Supports nested arrays (`Array<Array<Message>>`), union types with discriminators, and complex return
  type extraction.
- **Union Types**: Automatically detected from field descriptions (e.g., "This object contains one of the following
  types"). Discriminator fields are identified from descriptions mentioning "This field determines which of the
  following..." patterns.
- **HTTP Method Detection**: Uses `file` parameters to determine POST vs GET for API methods.
- **Error Handling**: Comprehensive logging with kotlinLogging throughout the pipeline.

## CI/CD

GitHub Actions workflow (`.github/workflows/generate-swagger.yml`) runs daily at 00:05 UTC, or on manual trigger. Uses
Ubuntu with JDK 21 (GraalVM), caches Gradle and Kotlin Native builds, extracts API version from generated JSON, and
creates tagged releases (`v7.2`, etc.) when the version changes. Releases attach the generated `telegram-bot-api.json`
file.
