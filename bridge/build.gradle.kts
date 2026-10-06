import org.gradle.internal.os.OperatingSystem

plugins {
    java
    application
    alias(libs.plugins.badass.runtime)
}

val appName = "Bilt Terminal Bridge"
val bundleId = "com.bilt.pos.bridge"
val bridgeVersion = providers.gradleProperty("VERSION")

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    implementation(projects.java)

    testImplementation(libs.junit.jupiter)
    testRuntimeOnly(libs.junit.platform.launcher)
}

tasks.test { useJUnitPlatform() }

// JVM options shared by `run` and the packaged launcher. The Apple ones are
// inert elsewhere: UIElement keeps the bridge out of the Dock so it lives in
// the menu bar only, and template images let the monochrome tray icon follow
// the light/dark menu bar.
val jvmOptions =
    listOf(
        "-Djava.net.preferIPv4Stack=true",
        "-Dapple.awt.UIElement=true",
        "-Dapple.awt.enableTemplateImages=true",
        "-Xmx256m",
    )

application {
    mainClass.set("$bundleId.BridgeMain")
    applicationDefaultJvmArgs = jvmOptions
}

tasks.processResources {
    val version = providers.gradleProperty("VERSION")
    inputs.property("bridgeVersion", version)
    filesMatching("bridge-version.properties") { expand(mapOf("bridgeVersion" to version.get())) }
}

// macOS rejects a CFBundleVersion whose first number is zero, which every
// pre-1.0 SDK release is. The bundle gets the version without its leading
// "0." as its build number (0.30.0 -> 30.0, still monotone) and the real
// version as CFBundleShortVersionString, patched in after jpackage below.
val macBuildVersion =
    bridgeVersion.get().let { if (it.startsWith("0.")) it.removePrefix("0.") else it }

runtime {
    options.set(
        listOf("--strip-debug", "--compress", "zip-6", "--no-header-files", "--no-man-pages")
    )
    // Checked against `jdeps --print-module-deps` over the runtime classpath
    // (see docs/terminal-bridge.md): java.desktop is the tray, jdk.httpserver
    // the skeleton listener, java.sql Jackson's optional date types,
    // jdk.crypto.ec the terminal TLS handshake, jdk.unsupported the
    // sun.misc.Unsafe fast paths Okio and Jackson probe for.
    modules.set(
        listOf(
            "java.base",
            "java.logging",
            "java.net.http",
            "java.xml",
            "java.desktop",
            "java.naming",
            "java.management",
            "java.sql",
            "jdk.httpserver",
            "jdk.crypto.ec",
            "jdk.unsupported",
        )
    )

    jpackage {
        imageName = appName
        installerName = appName
        appVersion =
            if (OperatingSystem.current().isMacOsX) macBuildVersion else bridgeVersion.get()
        jvmArgs = jvmOptions
        val icons = layout.projectDirectory.dir("src/main/resources/icons")
        if (OperatingSystem.current().isMacOsX) {
            // jpackage's own dmg step drives Finder through AppleScript to lay
            // out the window, which hangs without an interactive, automation-
            // approved session. packageDmg below builds the dmg with hdiutil.
            skipInstaller = true
            imageOptions =
                listOf(
                    "--icon",
                    icons.file("bridge.icns").asFile.path,
                    "--mac-package-identifier",
                    bundleId,
                    "--mac-package-name",
                    "Bilt Bridge",
                )
        }
    }
}

// The runtime plugin's tasks read Project at execution time, which the
// configuration cache forbids. Flagging them lets Gradle skip the cache for
// packaging builds instead of failing them; ordinary `build` runs stay cached.
tasks
    .matching {
        it.name in
            setOf(
                "jre",
                "runtime",
                "runtimeZip",
                "suggestModules",
                "jpackageImage",
                "jpackage",
                "packageDmg",
            )
    }
    .configureEach {
        notCompatibleWithConfigurationCache(
            "org.beryx.runtime does not support the configuration cache"
        )
    }

// Restores the real version string in the bundle, then applies an ad-hoc
// signature so the app launches on Apple silicon without a Developer ID.
// Distribution needs --mac-sign with a Developer ID plus notarization.
if (OperatingSystem.current().isMacOsX) {
    tasks.named("jpackageImage") {
        val app = layout.buildDirectory.dir("jpackage/$appName.app")
        val version = bridgeVersion
        doLast {
            val appPath = app.get().asFile.path
            run(
                "/usr/libexec/PlistBuddy",
                "-c",
                "Set :CFBundleShortVersionString ${version.get()}",
                "$appPath/Contents/Info.plist",
            )
            run("codesign", "--force", "--deep", "--sign", "-", appPath)
        }
    }

    val packageDmg by tasks.registering {
        group = "distribution"
        description = "Wraps the signed app image in a compressed dmg with an Applications link"
        dependsOn("jpackageImage")
        val app = layout.buildDirectory.dir("jpackage/$appName.app")
        val staging = layout.buildDirectory.dir("dmg-staging")
        val dmg = layout.buildDirectory.file("jpackage/$appName-${bridgeVersion.get()}.dmg")
        inputs.dir(app)
        outputs.file(dmg)
        doLast {
            val root = staging.get().asFile
            root.deleteRecursively()
            root.mkdirs()
            run("cp", "-R", app.get().asFile.path, root.path)
            run("ln", "-s", "/Applications", "${root.path}/Applications")
            run(
                "hdiutil",
                "create",
                "-volname",
                appName,
                "-srcfolder",
                root.path,
                "-ov",
                "-format",
                "UDZO",
                "-fs",
                "HFS+",
                dmg.get().asFile.path,
            )
        }
    }

    tasks.named("jpackage") { finalizedBy(packageDmg) }
}

fun run(vararg command: String) {
    val exit = ProcessBuilder(*command).inheritIO().start().waitFor()
    check(exit == 0) { "${command.first()} failed with exit code $exit" }
}
