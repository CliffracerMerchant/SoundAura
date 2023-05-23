/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model.database

import android.content.ContentValues
import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase.Companion.addAllMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

@Database(version = 5, exportSchema = true,
          entities = [Playlist::class, Preset::class, PresetPlaylist::class])
@TypeConverters(Playlist.TracksConverter::class)
abstract class SoundAuraDatabase : RoomDatabase() {
    abstract fun playlistDao(): PlaylistDao
    abstract fun presetDao(): PresetDao

    companion object {
        fun addAllMigrations(builder: Builder<SoundAuraDatabase>) =
            builder.addMigrations(migration1to2, migration2to3,
                                  migration3to4, migration4to5)

        private val migration1to2 = Migration(1,2) { db ->
            db.execSQL("PRAGMA foreign_keys=off")
            db.execSQL("BEGIN TRANSACTION")
            try {
                db.execSQL("""CREATE TABLE temp_table (
                    `uriString` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `isActive` INTEGER NOT NULL DEFAULT 0,
                    `volume` REAL NOT NULL DEFAULT 1.0)""")
                db.execSQL("""INSERT INTO temp_table (uriString, name, isActive, volume)
                              SELECT uriString, name, playing, volume FROM track;""")
                db.execSQL("DROP TABLE track;")
                db.execSQL("ALTER TABLE temp_table RENAME TO track;")
            } finally {
                db.execSQL("COMMIT;")
                db.execSQL("PRAGMA foreign_keys=on;")
            }
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
                FOREIGN KEY(`presetName`) REFERENCES `preset`(`name`) ON UPDATE CASCADE ON DELETE CASCADE,
                FOREIGN KEY(`trackUriString`) REFERENCES `track`(`uriString`) ON UPDATE CASCADE ON DELETE CASCADE)""")
        }

        private val migration4to5 = Migration(4,5) { db ->
            db.execSQL("""CREATE TABLE playlist (" +
                `name` TEXT NOT NULL PRIMARY KEY,
                `tracks` TEXT NOT NULL,
                `shuffleEnabled` INTEGER NOT NULL DEFAULT 0,
                `isActive` INTEGER NOT NULL DEFAULT 0,
                `volume` REAL NOT NULL DEFAULT 1.0)""")

            // Since the name field was previously not unique,
            // we need to append non unique names with a number
            val playlists: List<ContentValues>
            db.query("SELECT name, uriString, isActive, volume FROM track").use { cursor ->
                val usedNames = mutableSetOf<String>()
                playlists = List(cursor.count) {
                    cursor.moveToPosition(it)

                    val name = cursor.getString(0)
                    val newName = if (name !in usedNames) name else {
                        var counter = 2
                        while ("$name $counter" in usedNames)
                            counter++
                        "$name $counter"
                    }
                    usedNames.add(newName)

                    ContentValues(4).apply {
                        put("name", newName)
                        put("tracks", cursor.getString(1))
                        put("isActive", cursor.getInt(2))
                        put("volume", cursor.getFloat(3))
                    }
                }
            }

            db.beginTransaction()
            try { for (playlist in playlists)
                db.insert("playlist", OnConflictStrategy.NONE, playlist)
            } finally { db.endTransaction() }

            db.execSQL("""CREATE TABLE presetPlaylist (
                        `presetName` TEXT NOT NULL,
                        `playlistName` TEXT NOT NULL,
                        `playlistVolume` REAL NOT NULL,
                    PRIMARY KEY (presetName, playlistName),
                    FOREIGN KEY(`presetName`) REFERENCES `preset`(`name`) ON UPDATE CASCADE ON DELETE CASCADE,
                    FOREIGN KEY(`playlistName`) REFERENCES `playlist`(`name`) ON UPDATE CASCADE ON DELETE CASCADE
                )""")
            val trackNameSelector = "(SELECT name FROM track WHERE uriString = presetTrack.trackUriString LIMIT 1)"
            db.execSQL("INSERT INTO presetPlaylist (presetName, playlistName, playlistVolume) " +
                       "SELECT presetName, $trackNameSelector, trackVolume FROM presetTrack")

            db.execSQL("DROP TABLE track")
            db.execSQL("DROP TABLE presetTrack")
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

    @Provides fun providePlaylistDao(db: SoundAuraDatabase) = db.playlistDao()
    @Provides fun providePresetDao(db: SoundAuraDatabase) = db.presetDao()
}
