/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "track")
data class Track(
    @ColumnInfo(name="uriString") @PrimaryKey
    val uriString: String,
    @ColumnInfo(name="name")
    val name: String,
    @ColumnInfo(name="isActive", defaultValue = "0")
    val isActive: Boolean = false,
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="volume", defaultValue = "1.0")
    val volume: Float = 1f,
    @ColumnInfo(name="hasError", defaultValue = "0")
    val hasError: Boolean = false,
) {
    enum class Sort { NameAsc, NameDesc, OrderAdded;

        companion object {
            @Composable fun stringValues() = with(LocalContext.current) {
                remember { arrayOf(getString(R.string.name_ascending),
                    getString(R.string.name_descending),
                    getString(R.string.order_added)) }
            }
        }
    }
}

data class ActiveTrack(
    val uriString: String,
    val volume: Float)

fun Track.toActiveTrack() = ActiveTrack(uriString, volume)

@Dao abstract class TrackDao {
    @Insert abstract suspend fun insert(track: Track): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(track: List<Track>): List<Long>

    @Query("DELETE FROM track WHERE uriString = :uriString")
    abstract suspend fun delete(uriString: String)

    @Query("DELETE FROM track WHERE uriString in (:uriStrings)")
    abstract suspend fun delete(uriStrings: List<String>)

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY name COLLATE NOCASE ASC")
    protected abstract fun getAllTracksSortedByNameAsc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY name COLLATE NOCASE DESC")
    protected abstract fun getAllTracksSortedByNameDesc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter")
    protected abstract fun getAllTracksSortedByOrderAdded(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    protected abstract fun getAllTracksSortedByActiveThenNameAsc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY isActive DESC, name COLLATE NOCASE DESC")
    protected abstract fun getAllTracksSortedByActiveThenNameDesc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY isActive DESC")
    protected abstract fun getAllTracksSortedByActiveThenOrderAdded(filter: String): Flow<List<Track>>

    fun getAllTracks(sort: Track.Sort, showActiveFirst: Boolean, searchFilter: String?): Flow<List<Track>> {
        val filter = "%${searchFilter ?: ""}%"
        return if (showActiveFirst) when (sort) {
            Track.Sort.NameAsc ->    getAllTracksSortedByActiveThenNameAsc(filter)
            Track.Sort.NameDesc ->   getAllTracksSortedByActiveThenNameDesc(filter)
            Track.Sort.OrderAdded -> getAllTracksSortedByActiveThenOrderAdded(filter)
        } else when (sort) {
            Track.Sort.NameAsc ->    getAllTracksSortedByNameAsc(filter)
            Track.Sort.NameDesc ->   getAllTracksSortedByNameDesc(filter)
            Track.Sort.OrderAdded -> getAllTracksSortedByOrderAdded(filter)
        }
    }

    @Query("SELECT uriString, volume FROM track WHERE isActive")
    abstract fun getActiveTracks(): Flow<List<ActiveTrack>>

    @Query("UPDATE track set hasError = 1 WHERE uriString = :uri")
    abstract suspend fun notifyOfError(uri: String)

    @Query("UPDATE track set isActive = 1 - isActive WHERE uriString = :uri")
    abstract suspend fun toggleIsActive(uri: String)

    @Query("UPDATE track SET volume = :volume WHERE uriString = :uri")
    abstract suspend fun setVolume(uri: String, volume: Float)

    @Query("UPDATE track SET name = :name WHERE uriString = :uri")
    abstract suspend fun setName(uri: String, name: String)
}