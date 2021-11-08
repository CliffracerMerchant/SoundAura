/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import android.app.Application
import android.content.Context
import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.*
import androidx.room.Database
import androidx.room.RoomDatabase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.File

@Entity(tableName = "track")
class Track(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name="id")     val id: Long = 0,
    @ColumnInfo(name="path")   val path: String,
    @ColumnInfo(name="name")   val name: String = File(path).name,
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="volume") val volume: Float = 1f
) {
    enum class Sort { NameAsc, NameDesc, OrderAdded }
}

@Composable fun string(sort: Track.Sort) = when (sort) {
    Track.Sort.NameAsc ->    stringResource(R.string.name_ascending_description)
    Track.Sort.NameDesc ->   stringResource(R.string.name_descending_description)
    Track.Sort.OrderAdded -> stringResource(R.string.order_added_description)
}

@Dao abstract class TrackDao() {
    @Insert abstract suspend fun insert(track: Track): Long
    @Insert abstract suspend fun insert(track: List<Track>)

    @Query("DELETE FROM track WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM track WHERE id in (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Query("SELECT * FROM track ORDER BY name COLLATE NOCASE ASC")
    abstract fun getAllTracksSortedByNameAsc(): Flow<List<Track>>

    @Query("SELECT * FROM track ORDER BY name COLLATE NOCASE DESC")
    abstract fun getAllTracksSortedByNameDesc(): Flow<List<Track>>

    @Query("SELECT * FROM track ORDER BY id")
    abstract fun getAllTracks(): Flow<List<Track>>

    @Query("UPDATE track SET name = :name WHERE id = :id")
    abstract suspend fun updateName(id: Long, name: String)

    @Query("UPDATE track SET volume = :volume WHERE id = :id")
    abstract suspend fun updateVolume(id: Long, volume: Float)
}

@Database(entities = [Track::class], version = 1, exportSchema = true)
abstract class SoundObservatoryDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        var instance: SoundObservatoryDatabase? = null

        fun get(context: Context) = instance ?:
            Room.databaseBuilder(
                context.applicationContext,
                SoundObservatoryDatabase::class.java,
                "SoundObservatoryDb"
            ).build().also { instance = it }
    }
}

class TrackViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = SoundObservatoryDatabase.get(app).trackDao()

    fun add(track: Track) = viewModelScope.launch { dao.insert(track) }
    fun delete(id: Long) = viewModelScope.launch { dao.delete(id) }
    fun delete(ids: List<Long>) = viewModelScope.launch { dao.delete(ids) }

    val trackSort = MutableStateFlow(Track.Sort.NameAsc)

    @ExperimentalCoroutinesApi
    val tracks = trackSort.transformLatest<Track.Sort, List<Track>> {
        when (it) { Track.Sort.NameAsc ->    dao.getAllTracksSortedByNameAsc()
                    Track.Sort.NameDesc ->   dao.getAllTracksSortedByNameDesc()
                    Track.Sort.OrderAdded -> dao.getAllTracks() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    fun updateName(id: Long, name: String) =
        viewModelScope.launch { dao.updateName(id, name) }

    fun updateVolume(id: Long, volume: Float) =
        viewModelScope.launch { dao.updateVolume(id, volume) }
}
