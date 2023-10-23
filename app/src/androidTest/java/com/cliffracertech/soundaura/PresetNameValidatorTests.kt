/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.cliffracertech.soundaura.model.Validator
import com.cliffracertech.soundaura.model.database.PresetDao
import com.cliffracertech.soundaura.model.database.SoundAuraDatabase
import com.cliffracertech.soundaura.model.database.newPresetNameValidator
import com.cliffracertech.soundaura.model.database.presetRenameValidator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PresetNameValidatorTests {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val coroutineScope = TestCoroutineScope()

    private lateinit var instance: Validator<String>
    private lateinit var db: SoundAuraDatabase
    private lateinit var presetDao: PresetDao

    private val existingName = "preset 1"
    private val existingName2 = "preset 2"
    private val newPresetName = "new preset"

    @Before fun init() {
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        presetDao = db.presetDao()
        runBlocking {
            val map = LinkedHashMap<Uri, String>()
            val names = listOf("track 1")
            map["test rui".toUri()] = names.first()
            db.playlistDao().insertSingleTrackPlaylists(map)
            db.playlistDao().toggleIsActive(names.first())
            presetDao.savePreset(existingName)
            presetDao.savePreset(existingName2)
        }
    }

    @After fun clean_up() {
        db.close()
        coroutineScope.cancel()
    }

    private fun initNewNameValidator() {
        instance = newPresetNameValidator(presetDao, coroutineScope)
    }

    @Test fun new_name_validator_begins_blank_without_error() = runTest{
        initNewNameValidator()
        assertThat(instance.value).isEqualTo("")
        waitUntil { instance.message != null } // should time out
        assertThat(instance.message).isNull()
    }

    @Test fun new_name_validator_shows_error_for_existing_name() = runTest {
        initNewNameValidator()
        instance.value = existingName
        waitUntil { instance.message != null }
        assertThat(instance.message).isInstanceOf(Validator.Message.Error::class)
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.name_dialog_duplicate_name_error_message)
    }

    @Test fun new_name_validator_shows_error_for_blank_name_after_change() = runTest {
        initNewNameValidator()
        instance.value = newPresetName
        instance.value = ""
        waitUntil { instance.message != null }
        assertThat(instance.message).isInstanceOf(Validator.Message.Error::class)
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.name_dialog_blank_name_error_message)
    }

    @Test fun new_name_validator_fails_validation_and_shows_error_for_blank_name() = runTest {
        initNewNameValidator()
        val result = instance.validate()
        waitUntil { instance.message != null }
        assertThat(result).isNull()
        assertThat(instance.message).isInstanceOf(Validator.Message.Error::class)
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.name_dialog_blank_name_error_message)
    }

    @Test fun new_name_validator_fails_validation_for_existing_name() = runTest {
        initNewNameValidator()
        instance.value = existingName
        val result = instance.validate()
        waitUntil {
            instance.message?.stringResource?.stringResId != R.string.name_dialog_duplicate_name_error_message
        } // should time out
        assertThat(result).isNull()
        assertThat(instance.message).isInstanceOf(Validator.Message.Error::class)
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.name_dialog_duplicate_name_error_message)
    }

    @Test fun new_name_validator_success() = runTest {
        initNewNameValidator()
        instance.value = newPresetName
        waitUntil { instance.message != null } // should time out
        val result = instance.validate()
        assertThat(result).isEqualTo(newPresetName)
    }

    private fun initRenameValidator() {
        instance = presetRenameValidator(presetDao, coroutineScope, existingName)
    }

    @Test fun rename_validator_begins_with_existing_name_without_error() = runTest {
        initRenameValidator()
        assertThat(instance.value).isEqualTo(existingName)
        waitUntil { instance.message != null } // should time out
        assertThat(instance.message).isNull()
    }

    @Test fun rename_validator_shows_error_for_existing_name() = runTest {
        initRenameValidator()
        instance.value = existingName2
        waitUntil { instance.message != null }
        assertThat(instance.message).isInstanceOf(Validator.Message.Error::class)
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.name_dialog_duplicate_name_error_message)
    }

    @Test fun rename_validator_fails_validation_for_blank_name() = runTest {
        initRenameValidator()
        instance.value = ""
        waitUntil { instance.message != null }
        assertThat(instance.message?.stringResource?.stringResId)
            .isEqualTo(R.string.name_dialog_blank_name_error_message)
        assertThat(instance.validate()).isNull()
    }

    @Test fun rename_validator_success() = runTest {
        initRenameValidator()
        instance.value = newPresetName
        waitUntil { instance.message != null } // should time out
        assertThat(instance.validate()).isEqualTo(newPresetName)
    }
}