package com.phoebe.app.updates

import com.phoebe.app.platform.PhoebeBuildInfo
import com.phoebe.app.platform.openExternalUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.security.MessageDigest
import java.time.Duration
import kotlin.system.exitProcess

actual fun createPlatformUpdateInstaller(): PlatformUpdateInstaller = DesktopUpdateInstaller()

private class DesktopUpdateInstaller : PlatformUpdateInstaller {
    override val platform: UpdatePlatform = currentDesktopUpdatePlatform()

    override suspend fun install(
        update: AvailableUpdate,
        onProgress: (UpdateInstallProgress) -> Unit,
        confirmInstall: suspend (AvailableUpdate) -> Boolean,
    ): UpdateInstallResult = withContext(Dispatchers.IO) {
        val asset = update.asset ?: return@withContext openReleasePage(update)
        val downloaded = download(asset, onProgress)
        onProgress(
            UpdateInstallProgress(
                phase = UpdateInstallPhase.ReadyToInstall,
                message = "Verifying update...",
            ),
        )
        verifySha256(downloaded, asset.sha256Digest)
        onProgress(
            UpdateInstallProgress(
                phase = UpdateInstallPhase.ReadyToInstall,
                message = "Ready to install Phoebe ${update.versionName}.",
                fraction = 1f,
            ),
        )
        if (!confirmInstall(update)) {
            return@withContext UpdateInstallResult.RequiresUserAction(
                "Update downloaded. Run it again when you're ready to close Phoebe.",
            )
        }
        onProgress(
            UpdateInstallProgress(
                phase = UpdateInstallPhase.Installing,
                message = "Starting installer...",
                fraction = 1f,
            ),
        )

        when (platform) {
            UpdatePlatform.Windows -> {
                launchWindowsInstaller(downloaded)
                exitProcess(0)
            }
            UpdatePlatform.MacOs -> {
                if (downloaded.extension.equals("pkg", ignoreCase = true)) {
                    launchMacPkgInstaller(downloaded)
                    exitProcess(0)
                } else {
                    ProcessBuilder("open", downloaded.absolutePath).start()
                    UpdateInstallResult.RequiresUserAction("Phoebe opened the update disk image.")
                }
            }
            UpdatePlatform.LinuxFlatpak -> {
                launchLinuxInstaller(downloaded, flatpak = true)
                exitProcess(0)
            }
            UpdatePlatform.LinuxDeb -> {
                launchLinuxInstaller(downloaded, flatpak = false)
                exitProcess(0)
            }
            else -> openReleasePage(update)
        }
    }

    private fun download(
        asset: ReleaseAsset,
        onProgress: (UpdateInstallProgress) -> Unit,
    ): File {
        val target = File(
            File(System.getProperty("java.io.tmpdir"), "phoebe-updates").apply { mkdirs() },
            asset.name.replace(Regex("""[^A-Za-z0-9._-]"""), "_"),
        )
        val request = HttpRequest.newBuilder(URI.create(asset.downloadUrl))
            .timeout(Duration.ofMinutes(10))
            .header("User-Agent", "Phoebe/${PhoebeBuildInfo.versionName}")
            .GET()
            .build()
        val response = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.ALWAYS)
            .connectTimeout(Duration.ofSeconds(20))
            .build()
            .send(request, HttpResponse.BodyHandlers.ofInputStream())
        if (response.statusCode() !in 200..299) {
            target.delete()
            error("Update download failed: HTTP ${response.statusCode()}")
        }
        val totalBytes = response.headers().firstValueAsLong("Content-Length").orElse(asset.sizeBytes)
            .takeIf { it > 0L }
        var downloadedBytes = 0L
        onProgress(
            UpdateInstallProgress(
                phase = UpdateInstallPhase.Downloading,
                message = "Downloading Phoebe update...",
                fraction = progressFraction(downloadedBytes, totalBytes),
            ),
        )
        target.outputStream().use { output ->
            response.body().use { input ->
                val buffer = ByteArray(DownloadBufferSize)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    if (read == 0) continue
                    output.write(buffer, 0, read)
                    downloadedBytes += read
                    onProgress(
                        UpdateInstallProgress(
                            phase = UpdateInstallPhase.Downloading,
                            message = "Downloading Phoebe update...",
                            fraction = progressFraction(downloadedBytes, totalBytes),
                        ),
                    )
                }
            }
        }
        return target
    }

    private fun verifySha256(file: File, expected: String?) {
        if (expected == null) return
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                if (read > 0) digest.update(buffer, 0, read)
            }
        }
        val actual = digest.digest().joinToString("") { byte -> "%02x".format(byte) }
        check(actual == expected) { "Downloaded update failed SHA-256 verification." }
    }

    private fun launchWindowsInstaller(file: File) {
        val helper = helperFile("phoebe-update.cmd")
        helper.writeText(windowsInstallerHelperScript(file.absolutePath, currentLaunchCommand()))
        ProcessBuilder("cmd", "/c", "start", "", helper.absolutePath).start()
    }

    private fun launchMacPkgInstaller(file: File) {
        val helper = helperFile("phoebe-update.sh")
        helper.writeText(macPkgInstallerHelperScript(file.absolutePath))
        helper.setExecutable(true)
        ProcessBuilder("/bin/sh", helper.absolutePath).start()
    }

    private fun launchLinuxInstaller(file: File, flatpak: Boolean) {
        val insideFlatpak = !System.getenv("FLATPAK_ID").isNullOrBlank()
        val installFile = if (insideFlatpak && flatpak) {
            copyFlatpakBundleToHostDownloads(file)
        } else {
            file.absolutePath
        }
        val relaunchCommand = when {
            insideFlatpak && flatpak -> "flatpak run com.phoebe.app"
            else -> currentLaunchCommand()
        }
        val helper = helperFile("phoebe-update.sh")
        helper.writeText(
            linuxInstallerHelperScript(
                filePath = installFile,
                flatpak = flatpak,
                insideFlatpak = insideFlatpak,
                relaunchCommand = relaunchCommand,
            ),
        )
        helper.setExecutable(true)
        ProcessBuilder("/bin/sh", helper.absolutePath).start()
    }

    private fun copyFlatpakBundleToHostDownloads(sandboxFile: File): String {
        val hostFileName = sandboxFile.name.replace(Regex("""[^A-Za-z0-9._-]"""), "_")
        val hostPath = resolveHostDownloadsPath(hostFileName)
        val copy = ProcessBuilder(
            "flatpak-spawn",
            "--host",
            "sh",
            "-c",
            "cat > ${hostPath.shellQuote()}",
        )
            .redirectInput(sandboxFile)
            .redirectErrorStream(true)
            .start()
        val exitCode = copy.waitFor()
        check(exitCode == 0) { "Couldn't copy the Flatpak update to the host Downloads folder." }
        return hostPath
    }

    private fun resolveHostDownloadsPath(fileName: String): String {
        val query = ProcessBuilder(
            "flatpak-spawn",
            "--host",
            "sh",
            "-c",
            "printf '%s' \"\$HOME/Downloads/$fileName\"",
        )
            .redirectErrorStream(true)
            .start()
        val hostPath = query.inputStream.bufferedReader().readText().trim()
        val exitCode = query.waitFor()
        check(exitCode == 0 && hostPath.isNotBlank()) {
            "Couldn't resolve the host Downloads path for the Flatpak update."
        }
        return hostPath
    }

    private fun openReleasePage(update: AvailableUpdate): UpdateInstallResult {
        openExternalUrl(update.releasePageUrl)
        return UpdateInstallResult.OpenedReleasePage("Phoebe opened the latest release page.")
    }

    private fun helperFile(name: String): File =
        File(File(System.getProperty("java.io.tmpdir"), "phoebe-updates").apply { mkdirs() }, name)
}

private fun currentDesktopUpdatePlatform(): UpdatePlatform {
    val os = System.getProperty("os.name").orEmpty().lowercase()
    return when {
        "win" in os -> UpdatePlatform.Windows
        "mac" in os -> UpdatePlatform.MacOs
        System.getenv("FLATPAK_ID") == "com.phoebe.app" -> UpdatePlatform.LinuxFlatpak
        "linux" in os -> UpdatePlatform.LinuxDeb
        else -> UpdatePlatform.Other
    }
}

private fun currentLaunchCommand(): String? =
    ProcessHandle.current().info().command().orElse(null)?.takeIf { it.isNotBlank() }

private fun progressFraction(downloadedBytes: Long, totalBytes: Long?): Float? =
    totalBytes
        ?.takeIf { it > 0L }
        ?.let { total -> (downloadedBytes.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f) }

private const val DownloadBufferSize = 256 * 1024

internal fun windowsInstallerHelperScript(
    msiPath: String,
    relaunchCommand: String?,
): String {
    val relaunch = relaunchCommand?.windowsCmdLine()
    val relaunchLine = if (relaunch == null) "  rem No relaunch command found." else "  start \"\" $relaunch"
    return """
        @echo off
        timeout /t 1 /nobreak >NUL
        msiexec /i ${msiPath.windowsCmdLine()} /passive /norestart
        if %errorlevel%==0 (
        $relaunchLine
        )
    """.trimIndent()
}

internal fun macPkgInstallerHelperScript(pkgPath: String): String {
    return """
        #!/bin/sh
        sleep 1
        open -W ${pkgPath.shellQuote()}
        open -b com.phoebe.app || open -a Phoebe
    """.trimIndent() + "\n"
}

internal fun linuxInstallerHelperScript(
    filePath: String,
    flatpak: Boolean,
    insideFlatpak: Boolean,
    relaunchCommand: String?,
): String {
    val installCommand = if (flatpak) {
        val fileArg = filePath.shellQuote()
        "flatpak install --user -y $fileArg || flatpak install -y $fileArg"
    } else {
        "pkexec sh -c ${(("dpkg -i " + filePath.shellQuote()) + " || apt-get install -f -y").shellQuote()}"
    }
    val relaunchBackground = relaunchCommand?.let { command ->
        "(sh -c ${command.shellQuote()} >/dev/null 2>&1 &)"
    } ?: "(sh -c 'phoebe' >/dev/null 2>&1 &)"
    val hostPrefix = if (insideFlatpak) "flatpak-spawn --host " else ""
    return """
        #!/bin/sh
        sleep 1
        ${hostPrefix}sh -c ${(installCommand + " && " + relaunchBackground).shellQuote()}
    """.trimIndent() + "\n"
}

private fun String.shellQuote(): String =
    "'" + replace("'", "'\"'\"'") + "'"

private fun String.windowsCmdLine(): String =
    "\"" + replace("\"", "\\\"") + "\""
