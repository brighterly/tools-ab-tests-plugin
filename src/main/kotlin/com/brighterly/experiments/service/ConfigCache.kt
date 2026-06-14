package com.brighterly.experiments.service

import java.io.File

/** Transport result for a remote config fetch (defined here so cache tests need no network code). */
sealed interface FetchResult {
    data class Success(val rawJson: String) : FetchResult
    data class Failure(val message: String, val cause: Throwable? = null) : FetchResult
}

/**
 * Stores each environment's fetched experiment JSON as a local file so existing
 * file-based navigation (Ctrl+click / Find Usages) works against a real PSI file.
 * A failed fetch never clobbers a previously-good cache.
 */
class ConfigCache(private val baseDir: File) {

    fun cacheFile(envLabel: String): File = File(baseDir, "${sanitize(envLabel)}.json")

    fun exists(envLabel: String): Boolean = cacheFile(envLabel).isFile

    fun read(envLabel: String): String? = cacheFile(envLabel).takeIf { it.isFile }?.readText()

    fun write(envLabel: String, rawJson: String): File {
        val file = cacheFile(envLabel)
        file.parentFile?.mkdirs()
        file.writeText(rawJson)
        return file
    }

    /**
     * On success, writes and returns the cache file. On failure, returns the
     * existing cache file if present (kept intact), or null if there is none.
     */
    fun applyFetch(envLabel: String, result: FetchResult): File? = when (result) {
        is FetchResult.Success -> write(envLabel, result.rawJson)
        is FetchResult.Failure -> cacheFile(envLabel).takeIf { it.isFile }
    }

    private fun sanitize(label: String): String =
        label.lowercase().replace(Regex("[^a-z0-9._-]"), "_").ifBlank { "default" }
}
