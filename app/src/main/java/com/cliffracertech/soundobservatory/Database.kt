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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@Entity(tableName = "track")
class Track(
    @ColumnInfo(name="uriString") @PrimaryKey        val uriString: String,
    @ColumnInfo(name="name")                         val name: String,
    @ColumnInfo(name="playing", defaultValue = "0")  val playing: Boolean = false,
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="volume", defaultValue = "1.0") val volume: Float = 1f
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

    @Query("DELETE FROM track WHERE uriString = :uriString")
    abstract suspend fun delete(uriString: String)

    @Query("DELETE FROM track WHERE uriString in (:uriStrings)")
    abstract suspend fun delete(uriStrings: List<String>)

    @Query("SELECT * FROM track ORDER BY name COLLATE NOCASE ASC")
    abstract fun getAllTracksSortedByNameAsc(): Flow<List<Track>>

    @Query("SELECT * FROM track ORDER BY name COLLATE NOCASE DESC")
    abstract fun getAllTracksSortedByNameDesc(): Flow<List<Track>>

    @Query("SELECT * FROM track")
    abstract fun getAllTracks(): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE playing")
    abstract fun getAllPlayingTracks(): Flow<List<Track>>

    @Query("UPDATE track set playing = :playing WHERE uriString = :uri")
    abstract suspend fun updatePlaying(uri: String, playing: Boolean)

    @Query("UPDATE track SET volume = :volume WHERE uriString = :uri")
    abstract suspend fun updateVolume(uri: String, volume: Float)

    @Query("UPDATE track SET name = :name WHERE uriString = :uri")
    abstract suspend fun updateName(uri: String, name: String)
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

class ViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = SoundObservatoryDatabase.get(app).trackDao()

    val trackSort = MutableStateFlow(Track.Sort.NameAsc)

    val tracks = trackSort.flatMapLatest {
        when (it) { Track.Sort.NameAsc ->    dao.getAllTracksSortedByNameAsc()
                    Track.Sort.NameDesc ->   dao.getAllTracksSortedByNameDesc()
                    Track.Sort.OrderAdded -> dao.getAllTracks() }
    }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val playingTracks = dao.getAllPlayingTracks()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun add(track: Track) {
        viewModelScope.launch { dao.insert(track) }
    }

    fun delete(uriString: String) {
        viewModelScope.launch { dao.delete(uriString) }
    }

    fun delete(uriStrings: List<String>) {
        viewModelScope.launch { dao.delete(uriStrings) }
    }

    fun updatePlaying(uriString: String, playing: Boolean) {
        viewModelScope.launch { dao.updatePlaying(uriString, playing) }
    }

    fun updateVolume(uriString: String, volume: Float) {
        viewModelScope.launch { dao.updateVolume(uriString, volume) }
    }

    fun updateName(uriString: String, name: String) {
        viewModelScope.launch { dao.updateName(uriString, name) }
    }
}
