// The browser register emulator (js/apps/emulator, package @bilt/pos-emulator)
// is a pnpm workspace member, not a Gradle build; this module only lends it
// Gradle entry points next to `:emulator:desktop:run`, so one command brings
// up either emulator. Nothing here is wired into `build`, `check` or `test`.

import java.io.File

val jsDir = rootProject.layout.projectDirectory.dir("js")
val lockFile = jsDir.file("pnpm-lock.yaml")
val modulesMarker = jsDir.file("node_modules/.modules.yaml")

// `pnpm` from PATH when installed outright, otherwise through corepack, which
// resolves the version pinned by `packageManager` in js/package.json.
val pnpm: List<String> = run {
    val onPath =
        (System.getenv("PATH") ?: "")
            .split(File.pathSeparator)
            .map { File(it, "pnpm") }
            .firstOrNull { it.isFile && it.canExecute() }
    if (onPath != null) listOf(onPath.path) else listOf("corepack", "pnpm")
}

val nodeVersion = providers.exec {
    commandLine("node", "--version")
    isIgnoreExitValue = true
}

val corepackVersion = providers.exec {
    commandLine("corepack", "--version")
    isIgnoreExitValue = true
}

val port = providers.gradleProperty("port").orElse("5173")
val bridgePort = providers.gradleProperty("bridgePort").orElse("48333")

fun checkNode() {
    val version = nodeVersion.standardOutput.asText.get().trim()
    val major = Regex("""^v(\d+)""").find(version)?.groupValues?.get(1)?.toIntOrNull()
    check(major != null) {
        "Node.js was not found on PATH. The browser emulator needs Node 20 or newer " +
            "with pnpm or corepack: https://nodejs.org/"
    }
    check(major >= 20) {
        "Node.js $version is too old; the browser emulator needs Node 20 or newer."
    }
    if (pnpm.first() == "corepack") {
        check(corepackVersion.result.get().exitValue == 0) {
            "Neither pnpm nor corepack is on PATH. Install pnpm (https://pnpm.io/installation) " +
                "or enable corepack (`corepack enable`), then rerun."
        }
    }
}

val install by
    tasks.registering(Exec::class) {
        group = "emulator"
        description = "Installs the js/ pnpm workspace (pnpm install --frozen-lockfile)"
        workingDir = jsDir.asFile
        commandLine(pnpm + listOf("install", "--frozen-lockfile"))
        inputs.file(lockFile)
        outputs.file(modulesMarker)
        outputs.upToDateWhen {
            val marker = modulesMarker.asFile
            marker.isFile && marker.lastModified() >= lockFile.asFile.lastModified()
        }
        doFirst { checkNode() }
    }

// Vite resolves the emulator's workspace siblings through their dist/, so the
// SDK packages are built once before the dev server starts.
val buildPackages by
    tasks.registering(Exec::class) {
        group = "emulator"
        description = "Builds the workspace packages the emulator imports"
        dependsOn(install)
        workingDir = jsDir.asFile
        commandLine(pnpm + listOf("--filter", "@bilt/pos-emulator^...", "build"))
    }

val run by
    tasks.registering(Exec::class) {
        group = "emulator"
        description =
            "Starts the browser emulator's Vite dev server (-Pport=5173, -PbridgePort=48333)"
        dependsOn(buildPackages)
        workingDir = jsDir.asFile
        standardInput = System.`in`
        val url = port.map { "http://127.0.0.1:$it" }
        val bridge = bridgePort.get()
        // BRIDGE_URL retargets the Vite proxy; VITE_BILT_BRIDGE_PORT the page's
        // default for the direct route (see js/apps/emulator/src/settings.ts).
        environment("BRIDGE_URL", "http://127.0.0.1:$bridge")
        environment("VITE_BILT_BRIDGE_PORT", bridge)
        commandLine(
            pnpm +
                listOf(
                    "--filter",
                    "@bilt/pos-emulator",
                    "dev",
                    "--host",
                    "127.0.0.1",
                    "--port",
                    port.get(),
                    "--strictPort",
                )
        )
        doFirst {
            logger.lifecycle(
                "Browser emulator: ${url.get()} (bridge expected on 127.0.0.1:$bridge; Ctrl-C stops)"
            )
        }
    }
