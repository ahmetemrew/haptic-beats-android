package com.basitce.hapticbeats.core.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SongDao {
    @Query("SELECT * FROM songs ORDER BY title COLLATE NOCASE ASC")
    fun getAllSongs(): Flow<List<Song>>

    @Query("SELECT * FROM songs")
    suspend fun getAllSongsSnapshot(): List<Song>

    @Query("SELECT * FROM songs WHERE uri = :uri")
    suspend fun getSong(uri: String): Song?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSong(song: Song)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSongs(songs: List<Song>)

    @Delete
    suspend fun deleteSong(song: Song)

    @Query("DELETE FROM songs WHERE uri = :uri")
    suspend fun deleteSongByUri(uri: String)

    @Query("DELETE FROM songs WHERE uri IN (:uris)")
    suspend fun deleteSongsByUri(uris: List<String>)

    @Query(
        """
        UPDATE songs
        SET analysisState = :analysisState,
            analysisVersion = :analysisVersion,
            patternKey = NULL
        """
    )
    suspend fun markAllAnalysisState(
        analysisState: String,
        analysisVersion: Int
    )
}
