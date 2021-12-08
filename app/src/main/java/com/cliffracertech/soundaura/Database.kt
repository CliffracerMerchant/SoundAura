/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.app.Application
import android.content.Context
import androidx.annotation.FloatRange
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
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
    enum class Sort { NameAsc, NameDesc, OrderAdded;

        companion object {
            @Composable fun stringValues() = with(LocalContext.current) {
                remember { arrayOf(getString(R.string.name_ascending_description),
                                   getString(R.string.name_descending_description),
                                   getString(R.string.order_added_description)) }
            }
        }
    }
}

@Dao abstract class TrackDao {
    @Insert abstract suspend fun insert(track: Track): Long
    @Insert abstract suspend fun insert(track: List<Track>)

    @Query("DELETE FROM track WHERE uriString = :uriString")
    abstract suspend fun delete(uriString: String)

    @Query("DELETE FROM track WHERE uriString in (:uriStrings)")
    abstract suspend fun delete(uriStrings: List<String>)

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY name COLLATE NOCASE ASC")
    abstract fun getAllTracksSortedByNameAsc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY name COLLATE NOCASE DESC")
    abstract fun getAllTracksSortedByNameDesc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter")
    abstract fun getAllTracks(filter: String): Flow<List<Track>>

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
abstract class SoundAuraDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao

    companion object {
        var instance: SoundAuraDatabase? = null

        fun get(context: Context) = instance ?:
            Room.databaseBuilder(
                context.applicationContext,
                SoundAuraDatabase::class.java,
                "SoundAuraDb"
            ).build().also { instance = it }
    }
}

class TrackViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = SoundAuraDatabase.get(app).trackDao()

    var trackSort by mutableStateOf(Track.Sort.NameAsc)
    var searchFilter by mutableStateOf<String?>(null)
    var tracks by mutableStateOf<List<Track>>(emptyList())
        private set

    init {
        snapshotFlow { trackSort to searchFilter }.flatMapLatest {
            val filter = "%${it.second ?: ""}%"
            when (it.first) {
                Track.Sort.NameAsc ->    dao.getAllTracksSortedByNameAsc(filter)
                Track.Sort.NameDesc ->   dao.getAllTracksSortedByNameDesc(filter)
                Track.Sort.OrderAdded -> dao.getAllTracks(filter)
            }
        }.onEach { tracks = it }.launchIn(viewModelScope)
    }

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
