package com.basitce.hapticbeats.core.data

import com.basitce.hapticbeats.core.audio.CURRENT_ANALYSIS_VERSION
import com.basitce.hapticbeats.core.audio.HapticPatternStore
import com.basitce.hapticbeats.core.audio.HapticTimeline
import kotlinx.coroutines.flow.Flow

data class ScannedSong(
    val uri: String,
    val mediaStoreId: Long?,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumArtUri: String?,
    val dateAdded: Long,
    val dateModified: Long,
    val fileSize: Long
)

class SongRepository(
    private val songDao: SongDao,
    private val patternStore: HapticPatternStore
) {
    val allSongs: Flow<List<Song>> = songDao.getAllSongs()

    suspend fun getSong(uri: String): Song? {
        return songDao.getSong(uri)
    }

    suspend fun getAllSongsSnapshot(): List<Song> = songDao.getAllSongsSnapshot()

    suspend fun insertSong(song: Song) {
        songDao.insertSong(song)
    }

    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song)
    }

    fun buildPatternKey(song: Song): String {
        return patternStore.buildPatternKey(
            uri = song.uri,
            dateModified = song.dateModified,
            fileSize = song.fileSize,
            analysisVersion = CURRENT_ANALYSIS_VERSION
        )
    }

    suspend fun loadTimeline(song: Song): HapticTimeline? {
        val patternKey = song.patternKey ?: return null
        return patternStore.load(patternKey)
    }

    suspend fun markAnalyzing(song: Song): Song {
        val updatedSong = song.copy(
            analysisVersion = CURRENT_ANALYSIS_VERSION,
            analysisState = SongAnalysisState.ANALYZING,
            patternKey = null
        )
        songDao.insertSong(updatedSong)
        return updatedSong
    }

    suspend fun markFailed(song: Song): Song {
        val updatedSong = song.copy(
            analysisVersion = CURRENT_ANALYSIS_VERSION,
            analysisState = SongAnalysisState.FAILED,
            patternKey = null
        )
        songDao.insertSong(updatedSong)
        return updatedSong
    }

    suspend fun saveTimeline(song: Song, timeline: HapticTimeline): Song {
        val patternKey = buildPatternKey(song)
        patternStore.save(patternKey, timeline)
        val updatedSong = song.copy(
            analysisVersion = CURRENT_ANALYSIS_VERSION,
            analysisState = SongAnalysisState.READY,
            patternKey = patternKey
        )
        songDao.insertSong(updatedSong)
        return updatedSong
    }

    suspend fun clearPatterns() {
        patternStore.clear()
        songDao.markAllAnalysisState(
            analysisState = SongAnalysisState.MISSING,
            analysisVersion = CURRENT_ANALYSIS_VERSION
        )
    }

    suspend fun markAllForReanalysis() {
        patternStore.clear()
        songDao.markAllAnalysisState(
            analysisState = SongAnalysisState.STALE,
            analysisVersion = CURRENT_ANALYSIS_VERSION
        )
    }

    suspend fun syncSongs(scannedSongs: List<ScannedSong>) {
        val existingSongs = songDao.getAllSongsSnapshot().associateBy { it.uri }
        val scannedUris = scannedSongs.map { it.uri }.toSet()
        val stalePatternKeys = mutableListOf<String>()

        val mergedSongs = scannedSongs.map { scannedSong ->
            val currentSong = existingSongs[scannedSong.uri]
            val fingerprintChanged = currentSong == null ||
                currentSong.dateModified != scannedSong.dateModified ||
                currentSong.fileSize != scannedSong.fileSize ||
                currentSong.analysisVersion != CURRENT_ANALYSIS_VERSION

            val canReusePattern = currentSong != null &&
                !fingerprintChanged &&
                currentSong.analysisState == SongAnalysisState.READY &&
                patternStore.exists(currentSong.patternKey)

            if (fingerprintChanged && currentSong?.patternKey != null) {
                stalePatternKeys += currentSong.patternKey
            }

            Song(
                uri = scannedSong.uri,
                mediaStoreId = scannedSong.mediaStoreId,
                title = scannedSong.title,
                artist = scannedSong.artist,
                duration = scannedSong.duration,
                albumArtUri = scannedSong.albumArtUri,
                dateAdded = scannedSong.dateAdded,
                dateModified = scannedSong.dateModified,
                fileSize = scannedSong.fileSize,
                analysisVersion = CURRENT_ANALYSIS_VERSION,
                analysisState = when {
                    canReusePattern -> SongAnalysisState.READY
                    currentSong != null && fingerprintChanged -> SongAnalysisState.STALE
                    currentSong?.analysisState == SongAnalysisState.FAILED -> SongAnalysisState.FAILED
                    else -> SongAnalysisState.MISSING
                },
                patternKey = if (canReusePattern) currentSong?.patternKey else null
            )
        }

        val removedSongs = existingSongs.values.filterNot { scannedUris.contains(it.uri) }
        songDao.insertSongs(mergedSongs)
        if (removedSongs.isNotEmpty()) {
            songDao.deleteSongsByUri(removedSongs.map { it.uri })
        }

        for (patternKey in stalePatternKeys.distinct()) {
            patternStore.delete(patternKey)
        }

        for (patternKey in removedSongs.mapNotNull { it.patternKey }.distinct()) {
            patternStore.delete(patternKey)
        }
    }
}
