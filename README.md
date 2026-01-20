# Telegram Bot API Swagger

A Kotlin Multiplatform tool that automatically generates OpenAPI/Swagger specification from the official Telegram Bot
API documentation.

## Features

- Fetches the latest Telegram Bot API documentation from the official website
- Parses API methods and object types from the HTML documentation
- Generates a complete OpenAPI/Swagger JSON specification
- Cross-platform support (Windows, Linux, macOS)

## Usage

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

The generator will create a `swagger` directory in the `generator` module and generate the OpenAPI specification file:

```
generator/swagger/telegram-bot-api.json
```

This JSON file contains the complete Swagger/OpenAPI specification for the Telegram Bot API, which can be used with
tools like Swagger UI, Postman, or for generating client libraries.

## License

MIT License
