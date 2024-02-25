/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException

@RunWith(AndroidJUnit4::class)
class DatabaseTests {
    @get:Rule
    val helper: MigrationTestHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        SoundAuraDatabase::class.java)

    @Test @Throws(IOException::class)
    fun all_migrations() {
        val dbName = "migration test database"
        val oldestDb = helper.createDatabase(dbName, 1)
        oldestDb.close()

        val newestDb = Room.databaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            SoundAuraDatabase::class.java, dbName
        ).also(SoundAuraDatabase::addAllMigrations).build()
        newestDb.openHelper.writableDatabase.close()
    }
}