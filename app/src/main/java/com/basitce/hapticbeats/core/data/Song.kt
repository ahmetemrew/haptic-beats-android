package com.basitce.hapticbeats.core.data

import androidx.room.Entity
import androidx.room.PrimaryKey

object SongAnalysisState {
    const val MISSING = "missing"
    const val ANALYZING = "analyzing"
    const val READY = "ready"
    const val STALE = "stale"
    const val FAILED = "failed"
}

@Entity(tableName = "songs")
data class Song(
    @PrimaryKey val uri: String,
    val mediaStoreId: Long?,
    val title: String,
    val artist: String,
    val duration: Long,
    val albumArtUri: String?,
    val dateAdded: Long = System.currentTimeMillis(),
    val dateModified: Long = 0L,
    val fileSize: Long = 0L,
    val analysisVersion: Int = 0,
    val analysisState: String = SongAnalysisState.MISSING,
    val patternKey: String? = null
)
