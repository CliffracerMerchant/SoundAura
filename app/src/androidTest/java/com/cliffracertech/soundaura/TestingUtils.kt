/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.test.runner.AndroidJUnitRunner
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.google.common.truth.Subject
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.rules.TemporaryFolder
import org.junit.rules.TestWatcher
import org.junit.runner.Description
import kotlin.reflect.KClass

suspend fun waitUntil(
    timeOut: Long = 1000L,
    condition: suspend () -> Boolean,
) {
    val start = System.currentTimeMillis()
    while (!condition()) {
        if (System.currentTimeMillis() - start >= timeOut) {
            Log.d("SoundAuraTag", "waitUntil timed out after $timeOut milliseconds")
            return
        }
        Thread.sleep(50L)
    }
}

class TestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, name: String?, context: Context?): Application {
        return super.newApplication(cl, HiltTestApplication::class.java.name, context)
    }
}

fun <T: Any> Subject.isInstanceOf(clazz: KClass<T>) = isInstanceOf(clazz.java)

/** Create a [TestScope] instance, accessible via the property [scope], for
 * testing. The [TestScope] instance will use the constructor provided
 * [CoroutineDispatcher] to override the [Dispatcher] object defaults. As long
 * as the code under test specifies all dispatchers by referencing the
 * [Dispatcher] properties (e.g. [Dispatcher.Main], all code under test will be
 * run using the provided [dispatcher]. */
class TestScopeRule(
    val dispatcher: CoroutineDispatcher = UnconfinedTestDispatcher()
): TestWatcher() {
    val scope = TestScope(dispatcher)

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
        Dispatcher.setMain(dispatcher)
        Dispatcher.setImmediate(dispatcher)
        Dispatcher.setIO(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
        Dispatcher.resetMain()
        Dispatcher.resetImmediate()
        Dispatcher.resetIO()
    }
}

/** Provide an in-memory instance of the [RoomDatabase] described by [klass]
 * for testing. Once the test has started, the instance can be accessed via
 * the [db] property. */
class DatabaseTestRule<T: RoomDatabase>(
    private val context: Context,
    private val klass: Class<T>
): TestWatcher() {
    lateinit var db: T

    override fun starting(description: Description) {
        db = Room.inMemoryDatabaseBuilder(context, klass).build()
    }
}

fun SoundAuraDbTestRule(context: Context) = DatabaseTestRule(context, SoundAuraDatabase::class.java)

/** Provide a [DataStore]<[Preferences]> instance for
 * testing, accessible via the property [dataStore]. */
class DataStoreTestRule(private val scope: TestScope): TestWatcher() {
    private val tempFolder = TemporaryFolder.builder().assureDeletion().build()
    lateinit var dataStore: DataStore<Preferences>

    override fun starting(description: Description) {
        tempFolder.create()
        dataStore = PreferenceDataStoreFactory.create(
            scope = scope.backgroundScope,
            produceFile = { tempFolder.newFile("test.preferences_pb") })
    }

    override fun finished(description: Description) {
        runTest { dataStore.edit { it.clear() } }
        tempFolder.delete()
    }
}