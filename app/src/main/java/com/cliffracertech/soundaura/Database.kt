/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.annotation.FloatRange
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.room.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.Flow
import javax.inject.Qualifier
import javax.inject.Singleton

@Entity(tableName = "track")
data class Track(
    @ColumnInfo(name="uriString") @PrimaryKey
    val uriString: String,
    @ColumnInfo(name="name")
    val name: String,
    @ColumnInfo(name="playing", defaultValue = "0")
    val playing: Boolean = false,
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="volume", defaultValue = "1.0")
    val volume: Float = 1f
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

    override fun equals(other: Any?) = when {
        other !is Track -> false
        other === this -> true
        else -> other.uriString == uriString &&
                other.name == name &&
                other.playing == playing &&
                other.volume == volume
    }

    override fun hashCode(): Int {
        var result = uriString.hashCode()
        result = 31 * result + name.hashCode()
        result = 31 * result + playing.hashCode()
        result = 31 * result + volume.hashCode()
        return result
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
    protected abstract fun getAllTracksSortedByNameAsc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter ORDER BY name COLLATE NOCASE DESC")
    protected abstract fun getAllTracksSortedByNameDesc(filter: String): Flow<List<Track>>

    @Query("SELECT * FROM track WHERE name LIKE :filter")
    protected abstract fun getAllTracksSortedByOrderAdded(filter: String): Flow<List<Track>>

    fun getAllTracks(sort: Track.Sort, searchFilter: String?): Flow<List<Track>> {
        val filter = "%${searchFilter ?: ""}%"
        return when (sort) {
            Track.Sort.NameAsc ->    getAllTracksSortedByNameAsc(filter)
            Track.Sort.NameDesc ->   getAllTracksSortedByNameDesc(filter)
            Track.Sort.OrderAdded -> getAllTracksSortedByOrderAdded(filter)
        }
    }

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

@Module @InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Singleton @Provides
    fun provideDatabase(@ApplicationContext app: Context) =
        Room.databaseBuilder(app, SoundAuraDatabase::class.java, "SoundAuraDb").build()

    @Qualifier @Retention(AnnotationRetention.BINARY)
    annotation class InMemoryDatabase

    @InMemoryDatabase @Singleton @Provides
    fun provideInMemoryDatabase(@ApplicationContext app: Context) =
        Room.inMemoryDatabaseBuilder(app, SoundAuraDatabase::class.java).build()

    @Provides fun provideTrackDao(db: SoundAuraDatabase) = db.trackDao()
}
