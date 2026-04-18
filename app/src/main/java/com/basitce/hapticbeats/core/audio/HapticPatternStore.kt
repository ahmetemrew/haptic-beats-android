package com.basitce.hapticbeats.core.audio

import android.content.Context
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

class HapticPatternStore(context: Context) {

    private val patternDirectory = File(context.filesDir, "patterns").apply { mkdirs() }
    private val memoryCache = object : LruCache<String, HapticTimeline>(12) {
        override fun sizeOf(key: String, value: HapticTimeline): Int {
            return value.amplitudes.size.coerceAtLeast(1)
        }
    }

    fun buildPatternKey(
        uri: String,
        dateModified: Long,
        fileSize: Long,
        analysisVersion: Int = CURRENT_ANALYSIS_VERSION
    ): String {
        val payload = "$uri|$dateModified|$fileSize|$analysisVersion"
        return MessageDigest.getInstance("SHA-256")
            .digest(payload.toByteArray())
            .joinToString(separator = "") { "%02x".format(it.toInt() and 0xff) }
    }

    fun exists(patternKey: String?): Boolean {
        if (patternKey.isNullOrBlank()) return false
        if (memoryCache.get(patternKey) != null) return true
        return patternFile(patternKey).exists()
    }

    suspend fun load(patternKey: String?): HapticTimeline? = withContext(Dispatchers.IO) {
        if (patternKey.isNullOrBlank()) return@withContext null
        memoryCache.get(patternKey)?.let { return@withContext it }
        val file = patternFile(patternKey)
        if (!file.exists()) return@withContext null

        runCatching {
            DataInputStream(FileInputStream(file)).use { input ->
                val magic = input.readUTF()
                require(magic == "HBTL") { "Invalid pattern file" }
                val version = input.readInt()
                require(version == 1) { "Unsupported pattern version" }
                val stepMs = input.readInt()
                val durationMs = input.readLong()
                val count = input.readInt()
                val amplitudes = IntArray(count) { input.readUnsignedByte() }
                HapticTimeline(durationMs = durationMs, amplitudes = amplitudes, stepMs = stepMs)
            }
        }.getOrNull()?.also { memoryCache.put(patternKey, it) }
    }

    suspend fun save(patternKey: String, timeline: HapticTimeline) = withContext(Dispatchers.IO) {
        val file = patternFile(patternKey)
        DataOutputStream(FileOutputStream(file)).use { output ->
            output.writeUTF("HBTL")
            output.writeInt(1)
            output.writeInt(timeline.stepMs)
            output.writeLong(timeline.durationMs)
            output.writeInt(timeline.amplitudes.size)
            timeline.amplitudes.forEach { output.writeByte(it.coerceIn(0, 255)) }
        }
        memoryCache.put(patternKey, timeline)
    }

    suspend fun delete(patternKey: String?) = withContext(Dispatchers.IO) {
        if (patternKey.isNullOrBlank()) return@withContext
        memoryCache.remove(patternKey)
        patternFile(patternKey).delete()
    }

    suspend fun clear() = withContext(Dispatchers.IO) {
        memoryCache.evictAll()
        if (patternDirectory.exists()) {
            patternDirectory.listFiles()?.forEach { it.delete() }
        }
    }

    private fun patternFile(patternKey: String): File = File(patternDirectory, "$patternKey.hbt")
}
