/* This file is part of SoundObservatory, which is released under the Apache License 2.0. See
 * license.md in the project's root directory or use an internet search engine to see the full license. */
package com.cliffracertech.soundobservatory

import androidx.annotation.FloatRange
import androidx.room.*
import kotlinx.coroutines.flow.Flow
import java.io.File

@Entity(tableName = "track")
class Track(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name="id")     val id: Long = 0,
    @ColumnInfo(name="path")   val path: String,
    @ColumnInfo(name="title")  val title: String = File(path).name,
    @FloatRange(from = 0.0, to = 1.0)
    @ColumnInfo(name="volume") val volume: Float = 1f
) {
    enum class Sort {
        NameAsc,
        NameDesc;

        override fun toString() = if (this == NameAsc) "Name Ascending"
                                  else                 "Name Descending"

        fun a() {
            values()
        }
    }
}

@Dao abstract class TrackDao() {

    @Insert abstract suspend fun insert(track: Track): Long
    @Insert abstract suspend fun insert(track: List<Track>)

    @Query("DELETE FROM track WHERE id = :id")
    abstract suspend fun delete(id: Long)

    @Query("DELETE FROM track WHERE id in (:ids)")
    abstract suspend fun delete(ids: List<Long>)

    @Query("SELECT * FROM track ORDER BY title COLLATE NOCASE ASC")
    abstract suspend fun getAllSortedByTitleAsc(): Flow<List<Track>>

    @Query("SELECT * FROM track ORDER BY title COLLATE NOCASE DESC")
    abstract suspend fun getAllSortedByTitleDesc(): Flow<List<Track>>

    @Query("UPDATE track SET title = :title WHERE id = :id")
    abstract suspend fun updateTitle(id: Long, title: String)

    @Query("UPDATE track SET volume = :volume WHERE id = :id")
    abstract suspend fun updateVolume(id: Long, volume: Float)

}
