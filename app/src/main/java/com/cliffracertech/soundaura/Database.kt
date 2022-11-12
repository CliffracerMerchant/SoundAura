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
import androidx.room.migration.Migration
import com.cliffracertech.soundaura.SoundAuraDatabase.Companion.addAllMigrations
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

    fun getAllTracks(sort: Track.Sort, searchFilter: String?): Flow<List<Track>> {
        val filter = "%${searchFilter ?: ""}%"
        return when (sort) {
            Track.Sort.NameAsc ->    getAllTracksSortedByNameAsc(filter)
            Track.Sort.NameDesc ->   getAllTracksSortedByNameDesc(filter)
            Track.Sort.OrderAdded -> getAllTracksSortedByOrderAdded(filter)
        }
    }

    @Query("SELECT * FROM track WHERE isActive")
    abstract fun getAllActiveTracks(): Flow<List<Track>>

    @Query("UPDATE track set hasError = 1 WHERE uriString = :uri")
    abstract suspend fun notifyOfError(uri: String)

    @Query("UPDATE track set isActive = 1 - isActive WHERE uriString = :uri")
    abstract suspend fun toggleIsActive(uri: String)

    @Query("UPDATE track SET volume = :volume WHERE uriString = :uri")
    abstract suspend fun setVolume(uri: String, volume: Float)

    @Query("UPDATE track SET name = :name WHERE uriString = :uri")
    abstract suspend fun setName(uri: String, name: String)
}

@Entity(tableName = "preset")
data class Preset(
    @ColumnInfo(name = "name") @PrimaryKey
    val name: String)

@Entity(tableName = "presetTrack",
        primaryKeys = ["presetName", "trackUriString"],
        foreignKeys = [ ForeignKey(entity = Track::class,
                                   parentColumns=["uriString"],
                                   childColumns=["trackUriString"],
                                   onDelete=ForeignKey.NO_ACTION),
                        ForeignKey(entity = Preset::class,
                                   parentColumns=["name"],
                                   childColumns=["presetName"],
                                   onDelete=ForeignKey.NO_ACTION)])
data class PresetTrack(
    @ColumnInfo(name = "presetName")
    val presetName: String,

    @ColumnInfo(name = "trackUriString")
    val trackUriString: String,

    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="trackVolume")
    val trackVolume: Float = 1f
)

@Dao abstract class PresetDao {
    @Query("SELECT name FROM preset")
    protected abstract fun getPresetList() : Flow<List<Preset>>

    @Query("WITH presetUriStrings AS " +
                "(SELECT trackUriString FROM presetTrack WHERE presetName = :presetName) " +
           "UPDATE track SET " +
               "isActive = CASE WHEN uriString IN presetUriStrings THEN 1 ELSE 0 END, " +
               "volume = CASE WHEN uriString NOT IN presetUriStrings THEN volume ELSE " +
                   "(SELECT trackVolume FROM presetTrack " +
                    "WHERE presetName = :presetName AND trackUriString = uriString LIMIT 1) END")
    abstract suspend fun loadPreset(presetName: String)

    @Query("DELETE FROM preset WHERE name = :presetName")
    protected abstract suspend fun deletePresetName(presetName: String)

    @Query("DELETE FROM presetTrack WHERE presetName = :presetName")
    protected abstract suspend fun deletePresetContents(presetName: String)

    @Transaction
    open suspend fun deletePreset(presetName: String) {
        deletePresetContents(presetName)
        deletePresetName(presetName)
    }

    @Query("INSERT OR IGNORE INTO preset (name) VALUES (:presetName)")
    protected abstract suspend fun addPresetName(presetName: String)

    @Query("INSERT INTO presetTrack " +
           "SELECT :presetName, uriString, volume FROM track WHERE isActive")
    protected abstract suspend fun addPresetContents(presetName: String)

    @Transaction
    open suspend fun savePreset(presetName: String) {
        addPresetName(presetName)
        deletePresetContents(presetName)
        addPresetContents(presetName)
    }

    @Query("UPDATE preset SET name = :newName WHERE name = :oldName")
    abstract suspend fun renamePreset(oldName: String, newName: String)
}

@Database(version = 4, exportSchema = true,
          entities = [Track::class, Preset::class, PresetTrack::class])
abstract class SoundAuraDatabase : RoomDatabase() {
    abstract fun trackDao(): TrackDao
    abstract fun presetDao(): PresetDao

    companion object {
        fun addAllMigrations(builder: Builder<SoundAuraDatabase>) =
            builder.addMigrations(migration1to2, migration2to3, migration3to4)

        private val migration1to2 = Migration(1,2) { db ->
            db.execSQL("PRAGMA foreign_keys=off")
            db.execSQL("BEGIN TRANSACTION")
            db.execSQL("""CREATE TABLE temp_table (
                `uriString` TEXT NOT NULL PRIMARY KEY,
                `name` TEXT NOT NULL,
                `isActive` INTEGER NOT NULL DEFAULT 0,
                `volume` REAL NOT NULL DEFAULT 1.0)""")
            db.execSQL("""INSERT INTO temp_table (uriString, name, isActive, volume)
                          SELECT uriString, name, playing, volume FROM track;""")
            db.execSQL("DROP TABLE track;")
            db.execSQL("ALTER TABLE temp_table RENAME TO track;")
            db.execSQL("COMMIT;")
            db.execSQL("PRAGMA foreign_keys=on;")
        }

        private val migration2to3 = Migration(2,3) { db ->
            db.execSQL("ALTER TABLE track ADD COLUMN `hasError` INTEGER NOT NULL DEFAULT 0")
        }

        private val migration3to4 = Migration(3,4) { db ->
            db.execSQL("CREATE TABLE preset (`name` TEXT NOT NULL PRIMARY KEY)")
            db.execSQL("""CREATE TABLE presetTrack (
                    `presetName` TEXT NOT NULL,
                    `trackUriString` TEXT NOT NULL,
                    `trackVolume` REAL NOT NULL,
                PRIMARY KEY (presetName, trackUriString),
                FOREIGN KEY(`presetName`) REFERENCES `preset`(`name`) ON UPDATE NO ACTION ON DELETE NO ACTION,
                FOREIGN KEY(`trackUriString`) REFERENCES `track`(`uriString`) ON UPDATE NO ACTION ON DELETE NO ACTION)""")
        }
    }
}

@Module @InstallIn(SingletonComponent::class)
class DatabaseModule {
    @Singleton @Provides
    fun provideDatabase(@ApplicationContext app: Context) =
        Room.databaseBuilder(app, SoundAuraDatabase::class.java, "SoundAuraDb")
            .also(::addAllMigrations).build()

    @Qualifier @Retention(AnnotationRetention.BINARY)
    annotation class InMemoryDatabase

    @InMemoryDatabase @Singleton @Provides
    fun provideInMemoryDatabase(@ApplicationContext app: Context) =
        Room.inMemoryDatabaseBuilder(app, SoundAuraDatabase::class.java)
            .also(::addAllMigrations).build()

    @Provides fun provideTrackDao(db: SoundAuraDatabase) = db.trackDao()
    @Provides fun providePresetDao(db: SoundAuraDatabase) = db.presetDao()
}
