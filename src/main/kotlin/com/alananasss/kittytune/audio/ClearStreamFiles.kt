package com.alananasss.kittytune.audio

import com.alananasss.kittytune.core.AtomicFiles
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.security.MessageDigest

internal class ClearStreamFiles(private val directory: () -> File) {
    private val locks = Array(32) { Mutex() }

    fun cached(url: String): File? = fileFor(url).takeIf { it.isFile && it.length() > MIN_BYTES }

    suspend fun ensure(url: String, write: suspend (File) -> Unit): File? =
        locks[url.hashCode().ushr(1) % locks.size].withLock {
            cached(url)?.let { return@withLock it }
            val target = fileFor(url)
            val partial = File.createTempFile(target.name, ".part", target.parentFile)
            try {
                write(partial)
                currentCoroutineContext().ensureActive()
                if (partial.length() <= MIN_BYTES) return@withLock null
                AtomicFiles.replace(partial, target)
                target
            } finally {
                partial.delete()
            }
        }

    private fun fileFor(url: String): File {
        val hash = MessageDigest.getInstance("SHA-1").digest(url.toByteArray())
            .joinToString("") { "%02x".format(it) }.take(16)
        return File(directory().apply { mkdirs() }, "clear_$hash.m4a")
    }

    private companion object {
        const val MIN_BYTES = 64L * 1024
    }
}
