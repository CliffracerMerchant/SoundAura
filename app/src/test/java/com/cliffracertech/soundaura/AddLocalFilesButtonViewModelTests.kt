package com.cliffracertech.soundaura

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@Config(sdk=[30])
@RunWith(RobolectricTestRunner::class)
class AddLocalFilesButtonViewModelTests {
    private val coroutineDispatcher = StandardTestDispatcher()
    private val coroutineScope = TestScope(coroutineDispatcher + Job())
    private lateinit var instance: AddLocalFilesButtonViewModel
    private lateinit var db: SoundAuraDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var messageHandler: MessageHandler

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        trackDao = db.trackDao()
        messageHandler = MessageHandler()
        instance = AddLocalFilesButtonViewModel(context, trackDao, messageHandler, coroutineScope)
        Dispatchers.setMain(coroutineDispatcher)
    }

    @After @Throws(IOException::class)
    fun cleanup() {
        Dispatchers.resetMain()
        coroutineScope.cancel()
        db.close()
    }

    @Test fun initialState() {
        assertThat(instance.showingDialog).isFalse()
    }

    @Test fun clickShowsDialog() {
        assertThat(instance.showingDialog).isFalse()
        instance.onClick()
        assertThat(instance.showingDialog).isTrue()
    }

    @Test fun dialogDismiss() {
        clickShowsDialog()
        instance.onDialogDismiss()
        assertThat(instance.showingDialog).isFalse()
    }

    @Test fun dialogConfirm() = runTest {
        clickShowsDialog()
        val testTrack = Track("uriString", "name")
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onDialogConfirm(
            trackUris = listOf(Uri.parse(testTrack.uriString)),
            trackNames = listOf(testTrack.name))
        assertThat(instance.showingDialog).isFalse()
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)
    }

    @Test fun addingRepeatFileFailsWithMessage() = runTest {
        var latestMessage: MessageHandler.Message? = null
        launch { latestMessage = messageHandler.messages.first() }

        val testTrack = Track("uriString", "name")
        val trackUris = listOf(Uri.parse(testTrack.uriString))
        val trackNames = listOf(testTrack.name)
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onDialogConfirm(trackUris, trackNames)
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)

        instance.onDialogConfirm(trackUris, trackNames)
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)

        assertThat(latestMessage?.stringResource?.stringResId)
            .isEqualTo(R.string.track_already_exists_error_message)
    }
}