/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.*
import com.cliffracertech.soundaura.R
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName = "playable")
data class DbPlayable(
    @ColumnInfo(name="name") @PrimaryKey
    val name: String,

    @ColumnInfo(name="uriStrings")
    val uriStrings: String,

    @ColumnInfo(name="shuffle")
    val shuffleEnabled: Boolean = false,

    @ColumnInfo(name="isActive", defaultValue = "0")
    val isActive: Boolean = false,

    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="volume", defaultValue = "1.0")
    val volume: Float = 1f,

    @ColumnInfo(name="hasError", defaultValue = "0")
    val hasError: Boolean = false)

/** An entity that represents a piece of media that is playable,
 * i.e. either an individual track or a playlist of tracks. */
class Playable(
    /** The name of the [Playable] */
    val name: String,
    /** Whether or not the [Playable] is toggled on */
    val isActive: Boolean,
    /** The volume (in the range [0f, 1f]) of the [Playable] */
    val volume: Float,
    /** Whether or not there was an IO error accessing the [Playable] */
    val hasError: Boolean,
    /** Whether or not the [Playable] is a playlist. If false,
     * the [Playable] represents an individual track instead. */
    val isPlaylist: Boolean,
) {
    constructor(dbPlayable: DbPlayable): this(
        dbPlayable.name, dbPlayable.isActive, dbPlayable.volume, dbPlayable.hasError,
        isPlaylist = dbPlayable.uriStrings.split(File.pathSeparatorChar).size > 1
    )
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

@Dao abstract class PlayableDao {
    @Insert abstract suspend fun insert(playable: DbPlayable): Long

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    abstract suspend fun insert(playables: List<DbPlayable>): List<Long>

    @Query("DELETE FROM playable WHERE name = :name")
    abstract suspend fun delete(name: String)

    @Query("DELETE FROM playable WHERE name in (:names)")
    abstract suspend fun delete(names: List<String>)

    @Query("SELECT * FROM playable WHERE name LIKE :filter ORDER BY name COLLATE NOCASE ASC")
    protected abstract fun getAllPlayablesSortedByNameAsc(filter: String): Flow<List<Playable>>

    @Query("SELECT * FROM playable WHERE name LIKE :filter ORDER BY name COLLATE NOCASE DESC")
    protected abstract fun getAllPlayablesSortedByNameDesc(filter: String): Flow<List<Playable>>

    @Query("SELECT * FROM playable WHERE name LIKE :filter")
    protected abstract fun getAllPlayablesSortedByOrderAdded(filter: String): Flow<List<Playable>>

    @Query("SELECT * FROM playable WHERE name LIKE :filter ORDER BY isActive DESC, name COLLATE NOCASE ASC")
    protected abstract fun getAllPlayablesSortedByActiveThenNameAsc(filter: String): Flow<List<Playable>>

    @Query("SELECT * FROM playable WHERE name LIKE :filter ORDER BY isActive DESC, name COLLATE NOCASE DESC")
    protected abstract fun getAllPlayablesSortedByActiveThenNameDesc(filter: String): Flow<List<Playable>>

    @Query("SELECT * FROM playable WHERE name LIKE :filter ORDER BY isActive DESC")
    protected abstract fun getAllPlayablesSortedByActiveThenOrderAdded(filter: String): Flow<List<Playable>>

    fun getAllPlayables(
        sort: Playable.Sort,
        showActiveFirst: Boolean,
        searchFilter: String?
    ): Flow<List<Playable>> {
        val filter = "%${searchFilter ?: ""}%"
        return if (showActiveFirst) when (sort) {
            Playable.Sort.NameAsc ->    getAllPlayablesSortedByActiveThenNameAsc(filter)
            Playable.Sort.NameDesc ->   getAllPlayablesSortedByActiveThenNameDesc(filter)
            Playable.Sort.OrderAdded -> getAllPlayablesSortedByActiveThenOrderAdded(filter)
        } else when (sort) {
            Playable.Sort.NameAsc ->    getAllPlayablesSortedByNameAsc(filter)
            Playable.Sort.NameDesc ->   getAllPlayablesSortedByNameDesc(filter)
            Playable.Sort.OrderAdded -> getAllPlayablesSortedByOrderAdded(filter)
        }
    }

    @Query("SELECT * FROM playable WHERE isActive")
    abstract fun getServicePlayables():
        Flow<List<com.cliffracertech.soundaura.service.Playable>>

    @Query("SELECT name, volume FROM playable WHERE isActive")
    abstract fun getCurrentPresetPlayables():
        Flow<List<com.cliffracertech.soundaura.model.PresetPlayable>>

    @Query("UPDATE playable set hasError = 1 WHERE name = :name")
    abstract suspend fun notifyOfError(name: String)

    @Query("UPDATE playable SET name = :newName WHERE name = :oldName")
    abstract suspend fun setName(oldName: String, newName: String)

    @Query("UPDATE playable set isActive = 1 - isActive WHERE name = :name")
    abstract suspend fun toggleIsActive(name: String)

    @Query("UPDATE playable SET volume = :volume WHERE name = :name")
    abstract suspend fun setVolume(name: String, volume: Float)
}