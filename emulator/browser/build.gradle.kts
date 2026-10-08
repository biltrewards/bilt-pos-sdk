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

// The root and member manifests (pnpm-workspace.yaml: packages/*, examples/*, apps/*).
val manifests =
    fileTree(jsDir) {
        include("package.json", "pnpm-workspace.yaml", "*/*/package.json")
        exclude("**/node_modules/**")
    }

val install by
    tasks.registering(Exec::class) {
        group = "emulator"
        description = "Installs the js/ pnpm workspace (pnpm install --frozen-lockfile)"
        workingDir = jsDir.asFile
        commandLine(pnpm + listOf("install", "--frozen-lockfile"))
        inputs.file(lockFile)
        inputs.files(manifests)
        outputs.file(modulesMarker)
        // Up to date only while the install is newer than every manifest and no member has lost
        // its node_modules; a member without dependencies has none, and just reinstalls each time.
        val marker = modulesMarker.asFile
        val root = jsDir.asFile
        val lock = lockFile.asFile
        val tree: FileCollection = manifests
        outputs.upToDateWhen {
            val files = tree.files
            marker.isFile &&
                (files + lock).all { marker.lastModified() >= it.lastModified() } &&
                files
                    .filter { it.parentFile != root }
                    .all { File(it.parentFile, "node_modules").isDirectory }
        }
        // Locals only: the configuration cache cannot serialize an action that reaches back into
        // this script.
        val node = nodeVersion
        val corepack = corepackVersion
        val viaCorepack = pnpm.first() == "corepack"
        doFirst {
            val version = node.standardOutput.asText.get().trim()
            val major = Regex("""^v(\d+)""").find(version)?.groupValues?.get(1)?.toIntOrNull()
            check(major != null) {
                "Node.js was not found on PATH. The browser emulator needs Node 20 or newer " +
                    "with pnpm or corepack: https://nodejs.org/"
            }
            check(major >= 20) {
                "Node.js $version is too old; the browser emulator needs Node 20 or newer."
            }
            if (viaCorepack) {
                check(corepack.result.get().exitValue == 0) {
                    "Neither pnpm nor corepack is on PATH. Install pnpm " +
                        "(https://pnpm.io/installation) or enable corepack (`corepack enable`), " +
                        "then rerun."
                }
            }
        }
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

// The bridge is started by scripts/browser-emulator.sh, not as a Gradle
// JavaExec: a daemon-run bridge cannot reach a LAN terminal on macOS 15+
// (no Local Network grant), and the script owns startup ordering and cleanup.
val runWithBridge by
    tasks.registering(Exec::class) {
        group = "emulator"
        description =
            "Starts a Terminal Bridge and the browser emulator " +
                "(-PterminalHost=<ip> [-PterminalPort=8443] or -Plocal; " +
                "-Pport=5173 -PbridgePort=48333 -PnoOpen)"
        // Build here and let the script skip its own build: a nested gradlew on
        // the same project would wait on this build's lock.
        dependsOn(":bridge:installDist", buildPackages)
        environment("SKIP_BUILD", "1")
        workingDir = rootProject.layout.projectDirectory.asFile
        standardInput = System.`in`
        val terminalHost = providers.gradleProperty("terminalHost")
        val terminalPort = providers.gradleProperty("terminalPort").orElse("8443")
        val local = providers.gradleProperty("local").isPresent
        val noOpen = providers.gradleProperty("noOpen").isPresent
        val args = mutableListOf("scripts/browser-emulator.sh")
        if (local) {
            args += "--local"
        } else if (terminalHost.isPresent) {
            args += listOf("--terminal", "${terminalHost.get()}:${terminalPort.get()}")
        }
        args += listOf("--port", bridgePort.get(), "--web-port", port.get())
        if (noOpen) args += "--no-open"
        commandLine(args)
        doFirst {
            check(local || terminalHost.isPresent) {
                "Pass -PterminalHost=<ip> for a LAN terminal, or -Plocal for local sessions only."
            }
        }
    }
