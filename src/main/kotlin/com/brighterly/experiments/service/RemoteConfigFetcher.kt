package com.brighterly.experiments.service

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.util.io.HttpRequests
import java.io.IOException

/** Fetches raw experiment-config JSON over HTTP. Transport only — no parsing. */
object RemoteConfigFetcher {

    private val logger = thisLogger()
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000

    /** MUST be called off the EDT (network I/O). */
    fun fetch(url: String): FetchResult {
        if (url.isBlank()) return FetchResult.Failure("No URL configured")
        return try {
            val body = HttpRequests.request(url)
                .connectTimeout(CONNECT_TIMEOUT_MS)
                .readTimeout(READ_TIMEOUT_MS)
                .readString()
            FetchResult.Success(body)
        } catch (e: IOException) {
            logger.warn("Failed to fetch experiments config from $url", e)
            FetchResult.Failure(e.message ?: "Network error", e)
        }
    }
}
