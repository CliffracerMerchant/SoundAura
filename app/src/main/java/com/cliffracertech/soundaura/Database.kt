/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import com.cliffracertech.soundaura.SoundAuraDatabase.Companion.addAllMigrations
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Qualifier
import javax.inject.Singleton

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
