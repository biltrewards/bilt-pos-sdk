import com.github.gradle.node.npm.task.NpxTask
import com.github.gradle.node.task.NodeTask

plugins {
    id("com.github.node-gradle.node") version "7.1.0"
}

node {
    download.set(true)
    version.set("22.13.0")
    nodeProjectDir.set(projectDir)
}

val schemaFile = layout.projectDirectory.file("nexo_sale_to_poi_v3_0_schema.json")

val kotlinRawOutputFile = layout.buildDirectory.file("generated/NexoTerminalAPI.kt")
val kotlinOutputFile = rootProject.layout.projectDirectory.file(
    "kotlin/src/main/kotlin/com/bilt/pos/nexo/model/NexoTerminalAPI.kt",
)
val javaOutputDir = rootProject.layout.projectDirectory.dir(
    "java/src/main/java/com/bilt/pos/nexo/model",
)

val generateNexoKotlinRaw = tasks.register<NpxTask>("generateNexoKotlinRaw") {
    group = "codegen"
    description = "Regenerate Kotlin types from the Nexo JSON Schema (without header)."
    dependsOn(tasks.named("npmInstall"))

    inputs.file(schemaFile)
    outputs.file(kotlinRawOutputFile)

    command.set("quicktype")
    args.set(
        listOf(
            "--src", schemaFile.asFile.absolutePath,
            "--src-lang", "schema",
            "--lang", "kotlin",
            "--framework", "kotlinx",
            "--out", kotlinRawOutputFile.get().asFile.absolutePath,
            "--package", "com.bilt.pos.nexo.model",
        ),
    )
}

tasks.register<AddHeaderTask>("generateNexoKotlin") {
    group = "codegen"
    description = "Regenerate Kotlin types from the Nexo JSON Schema. Commit the result."
    dependsOn(generateNexoKotlinRaw)
    inputFile.set(kotlinRawOutputFile)
    outputFile.set(kotlinOutputFile)
}

tasks.register<NodeTask>("generateNexoJava") {
    group = "codegen"
    description = "Regenerate Java types with builders from the Nexo JSON Schema. Commit the result."
    dependsOn(tasks.named("npmInstall"))

    inputs.file(schemaFile)
    inputs.file(layout.projectDirectory.file("generate-nexo-java.js"))
    outputs.dir(javaOutputDir)

    script.set(layout.projectDirectory.file("generate-nexo-java.js"))
    args.set(
        listOf(
            schemaFile.asFile.absolutePath,
            javaOutputDir.asFile.absolutePath,
        ),
    )
}

tasks.register("generateNexo") {
    group = "codegen"
    description = "Regenerate both Kotlin and Java types from the Nexo JSON Schema. Commit the result."
    dependsOn("generateNexoKotlin", "generateNexoJava")
}

@CacheableTask
abstract class AddHeaderTask : DefaultTask() {
    @get:InputFile
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val inputFile: RegularFileProperty

    @get:OutputFile
    abstract val outputFile: RegularFileProperty

    @TaskAction
    fun run() {
        val header = """
            |/*
            | *    ____  _ _ _
            | *   | __ )(_) | |_
            | *   |  _ \| | | __|
            | *   | |_) | | | |_
            | *   |____/|_|_|\__|
            | *
            | *   Bilt POS SDK
            | *
            | *   This file is auto-generated from the Nexo Sale to POI v3.0 JSON Schema.
            | *   Do not modify manually — re-run code generation instead.
            | */
            |""".trimMargin()
        outputFile.get().asFile.writeText(header + inputFile.get().asFile.readText())
    }
}
