package com.phoebe.app.domain

import kotlinx.serialization.Serializable

@Serializable
data class LocalFolderMediaSourceConfig(
    val id: String,
    val rootUri: String,
    val label: String = "Local folder",
    val enabled: Boolean = true,
)

@Serializable
data class MediaSourcesState(
    val localFolders: List<LocalFolderMediaSourceConfig> = emptyList(),
)

fun LocalFolderMediaSourceConfig.displayPath(): String =
    rootUri.toLocalFolderDisplayPath(label)

internal fun String.toLocalFolderDisplayPath(fallbackLabel: String = "Local folder"): String {
    val uri = trim()
    if (uri.isBlank()) return fallbackLabel
    return when {
        uri.startsWith("file://") -> {
            val withoutScheme = uri.removePrefix("file://").substringBefore('?')
            percentDecode(if (withoutScheme.startsWith("/")) withoutScheme else "/$withoutScheme")
        }
        uri.startsWith("phoebe-web-folder://") -> {
            val encodedLabel = uri.trimEnd('/').substringAfterLast('/', fallbackLabel)
            "Browser folder: ${percentDecode(encodedLabel).ifBlank { fallbackLabel }}"
        }
        uri.startsWith("content://") -> {
            val treePath = uri.substringAfter("/tree/", missingDelimiterValue = "")
                .substringBefore('/')
                .takeIf { it.isNotBlank() }
            if (treePath != null) {
                percentDecode(treePath)
                    .replace(':', '/')
                    .ifBlank { percentDecode(uri) }
            } else {
                percentDecode(uri)
            }
        }
        else -> percentDecode(uri)
    }
}

private fun percentDecode(value: String): String = buildString(value.length) {
    var index = 0
    while (index < value.length) {
        if (value[index] == '%' && index + 2 < value.length) {
            val bytes = mutableListOf<Byte>()
            var scan = index
            while (scan + 2 < value.length && value[scan] == '%') {
                val byte = value.substring(scan + 1, scan + 3).toIntOrNull(16) ?: break
                bytes += byte.toByte()
                scan += 3
            }
            if (bytes.isNotEmpty()) {
                append(bytes.toByteArray().decodeToString())
                index = scan
                continue
            }
        }
        append(value[index])
        index++
    }
}
