import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.KModifier
import com.squareup.kotlinpoet.PropertySpec
import com.squareup.kotlinpoet.TypeSpec
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

abstract class GenerateVersionTask : DefaultTask() {
    @get:Input
    abstract val projectVersion: Property<String>

    @get:Input
    abstract val packageName: Property<String>

    @get:OutputDirectory
    abstract val outputDirectory: DirectoryProperty

    init {
        group = "build"
        description = "Generate Version.kt file with project version using KotlinPoet"
    }

    @TaskAction
    fun generate() {
        val version = projectVersion.get()
        val pkg = packageName.get()
        val outputDir = outputDirectory.get().asFile

        logger.info("Generating Version.kt with version: $version")

        val versionObject = TypeSpec.objectBuilder("Version")
            .addKdoc(
                """
                Project version information.
                This file is auto-generated during build time using KotlinPoet.
                """.trimIndent()
            )
            .addProperty(
                PropertySpec.builder("PROJECT_VERSION", String::class, KModifier.CONST)
                    .initializer("%S", version)
                    .addKdoc("The current project version")
                    .build()
            )
            .build()

        val file = FileSpec.builder(pkg, "Version")
            .addType(versionObject)
            .indent("    ")
            .build()

        file.writeTo(outputDir)

        logger.info("Version.kt generated successfully at: ${outputDir.absolutePath}")
    }
}
