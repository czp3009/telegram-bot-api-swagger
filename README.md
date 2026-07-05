# Telegram Bot API Swagger

A Kotlin Multiplatform tool that generates an OpenAPI/Swagger specification from the
[official Telegram Bot API documentation](https://core.telegram.org/bots/api).

## Features

- Fetches the latest Telegram Bot API documentation from the official website
- Parses API methods and object types from the HTML documentation
- Generates an OpenAPI 3.0 JSON specification
- Supports JVM and native targets for Windows, Linux, and macOS

## Usage

For local generation, the JVM target is usually the quickest option:

```bash
./gradlew :generator:jvmRun
```

On Windows:

```powershell
.\gradlew.bat :generator:jvmRun
```

Native executable targets are also available.

### Linux

```bash
./gradlew :generator:runReleaseExecutableLinuxX64
```

### macOS (Intel)

```bash
./gradlew :generator:runReleaseExecutableMacosX64
```

### macOS (Apple Silicon)

```bash
./gradlew :generator:runReleaseExecutableMacosArm64
```

### Windows

```cmd
gradlew.bat :generator:runReleaseExecutableMingwX64
```

## Output

The generator creates a `swagger` directory in the `generator` module and writes:

```text
generator/swagger/telegram-bot-api.json
```

This JSON file contains Telegram Bot API methods, request parameters, object schemas, response wrappers, file-upload
request bodies, and documented union types. It can be used with tools like Swagger UI, Postman, or client generators.

## License

[MIT License](https://opensource.org/license/mit)
