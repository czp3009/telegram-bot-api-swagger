# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Kotlin Multiplatform tool that generates OpenAPI/Swagger specifications from the official Telegram Bot API
documentation.

Pipeline:

```text
fetch online HTML -> parse methods/objects -> generate OpenAPI 3.0 JSON -> write swagger file
```

The generator should fetch the online documentation from `https://core.telegram.org/bots/api`. Local HTML snapshots may
be used for validation and comparison, but should not replace the online fetch as the generator input.

## Build Commands

Use the JVM target for parser and swagger-generation work because it is the fastest local feedback path:

```bash
./gradlew :generator:jvmRun
./gradlew :generator:compileKotlinJvm
```

Native executable targets are still part of the project:

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

Environment variables:

- `HTTP_PROXY` / `HTTPS_PROXY`: Configure a proxy for fetching the Telegram documentation when needed.

## Architecture

Linear pipeline: `fetch` -> `parse` -> `generate` -> `write`

### Core Components

Files are under `generator/src/commonMain/kotlin/com/hiczp/telegram/bot/api/generator/`.

| File                  | Purpose                                                                                    |
|-----------------------|--------------------------------------------------------------------------------------------|
| `Main.kt`             | Entry point; orchestrates the pipeline with coroutines.                                    |
| `DocumentFetcher.kt`  | Fetches HTML via Ktor; supports proxy configuration.                                       |
| `DocumentParser.kt`   | Parses HTML with ksoup; performs two-pass parsing of objects and methods.                  |
| `SwaggerGenerator.kt` | Generates OpenAPI 3.0 JSON with schemas, paths, request bodies, responses, and unions.     |
| `Platform.kt`         | Expected declarations for platform-specific file, environment, and HTTP-client operations. |

### Data Models

Defined in `DocumentParser.kt`:

- `Type`: `Simple`, `Generic`, or method-return `Union`
- `Object`: name, description, fields, and union metadata
- `Method`: name, description, parameters, return type, and HTTP method
- `Field` / `Parameter`: name, parsed type, required flag, and description

### Multiplatform Source Sets

```text
commonMain (shared parsing and generation logic)
    +-- jvmMain (CIO HTTP client, JVM logging)
    +-- nativeMain (cURL HTTP client)
        +-- unixMain (Linux + macOS file operations)
        |   +-- linuxX64Main
        |   +-- macosX64Main
        |   +-- macosArm64Main
        +-- mingwX64Main (Windows file operations)
```

## Key Implementation Details

- Objects are parsed before methods because method HTTP detection depends on object references and nested file fields.
- Required object fields are inferred from whether the description starts with `Optional`.
- Required method parameters come from the method table's `Required` column.
- HTTP method logic: methods with `InputFile` or nested file-capable media fields use POST multipart; methods starting
  with `get` use GET; all other methods use POST.
- Nested file detection recursively checks object and union fields for `InputFile`, `InputMedia`, `attach://`, and
  Telegram sending-files documentation links.
- Return type extraction uses description patterns such as `Returns X on success`, `X is returned`, and conditional
  `X is returned, otherwise True is returned`.
- HTML-to-Markdown conversion uses DOM traversal and preserves links with the Telegram Bot API base URL.

## Union And oneOf Rules

Telegram documents several different "one of" patterns. Keep the logic generic; do not add type-name-specific hacks
unless there is no general rule and the exception is documented.

- Object unions with a shared constant branch field and unique values get `oneOf` plus an OpenAPI discriminator.
- If discriminator values are duplicated, keep `oneOf` but omit the OpenAPI discriminator.
- If there is no shared constant discriminator field, keep `oneOf` but omit the OpenAPI discriminator.
- If any branch is not an object schema, such as `String` or `Array<RichText>`, keep `oneOf` but omit the OpenAPI
  discriminator.
- Description-only branches such as `String` and `Array of X` must be included when the HTML describes them as part of
  the allowed value set.
- Parameter type lists such as `Array of A, B and C` should become an array whose item schema is `oneOf`.
- Method return unions such as `Message or Boolean` are represented as inline `oneOf` schemas.

Examples of patterns covered by these rules:

- `RichText`: mixed `String`, `Array<RichText>`, and object branches; no discriminator.
- `RichBlock`: object union with unique `type` values; has discriminator.
- `InlineQueryResult`: object union with duplicate `type` values; no discriminator.
- `InputMessageContent`: object union without a discriminator field; no discriminator.
- `MaybeInaccessibleMessage`: virtual object union; swagger keeps `Message | InaccessibleMessage`.

## Validation Checklist

After changing parser or schema logic, run `:generator:jvmRun` and compare the generated swagger against the current
Telegram Bot API HTML. Check:

- documented object count matches generated schemas, plus the generated `Error` schema
- documented method count matches generated paths
- field names, required flags, and field types match the HTML tables
- method parameter names, required flags, and parameter types match the HTML tables
- every union's `oneOf` branch list matches the HTML subtype list and description-only branches
- discriminators are present only when all object branches share a constant discriminator field with unique values
- mixed primitive/array/object unions do not have discriminators

## Dependencies

- `ksoup`: HTML parsing
- `ktor-client-cio`: JVM HTTP client
- `ktor-client-curl`: native HTTP client
- `kotlinx-openapi-bindings`: OpenAPI 3.0 serialization
- `kotlin-logging`: logging abstraction

## Output

`generator/swagger/telegram-bot-api.json` - OpenAPI 3.0 spec with methods, objects, request bodies, responses, and
union schemas.

## CI/CD

GitHub Actions runs on Ubuntu with JDK 21, generates the swagger file, extracts the API version from generated JSON, and
creates tagged releases when the version changes.
