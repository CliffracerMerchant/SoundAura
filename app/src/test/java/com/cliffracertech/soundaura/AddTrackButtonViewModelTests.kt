package com.cliffracertech.soundaura

import android.content.Context
import android.net.Uri
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.TestCoroutineDispatcher
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.IOException

@Config(sdk=[30])
@RunWith(RobolectricTestRunner::class)
class AddTrackButtonViewModelTests {
    private lateinit var instance: AddTrackButtonViewModel
    private lateinit var db: SoundAuraDatabase
    private lateinit var trackDao: TrackDao
    private lateinit var messageHandler: MessageHandler

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        trackDao = db.trackDao()
        messageHandler = MessageHandler()
        instance = AddTrackButtonViewModel(context, trackDao, messageHandler)
    }

    @After @Throws(IOException::class)
    fun closeDb() = db.close()

    @Test fun initialState() {
        assertThat(instance.expanded).isFalse()
        assertThat(instance.showingDownloadFileDialog).isFalse()
        assertThat(instance.showingAddLocalFilesDialog).isFalse()

    }

    @Test fun clickTogglesExpandedState() {
        assertThat(instance.expanded).isFalse()
        instance.onClick()
        assertThat(instance.expanded).isTrue()
        instance.onClick()
        assertThat(instance.expanded).isFalse()
    }

    @Test fun globalClickOutsideBoundsCollapses() {
        instance.onClick()
        assertThat(instance.expanded).isTrue()
        instance.onBoundsChange(Rect(150f, 200f, 250f, 300f))
        instance.onGlobalClick(Offset(200f, 250f))
        assertThat(instance.expanded).isTrue()
        instance.onGlobalClick(Offset(0f, 0f))
        assertThat(instance.expanded).isFalse()
    }

    @Test fun downloadFileButtonClick() {
        instance.onClick()
        assertThat(instance.expanded).isTrue()
        assertThat(instance.showingDownloadFileDialog).isFalse()
        instance.onDownloadFileButtonClick()
        assertThat(instance.expanded).isFalse()
        assertThat(instance.showingDownloadFileDialog).isTrue()
    }

    @Test fun downloadFileDialogDismiss() {
        downloadFileButtonClick()
        instance.onDownloadFileDialogDismiss()
        assertThat(instance.showingDownloadFileDialog).isFalse()
    }

    @Test fun downloadFileDialogConfirm() = runBlocking {
        downloadFileButtonClick()
        val testTrack = Track("uriString", "name")
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onDownloadFileDialogConfirm(testTrack)
        assertThat(instance.showingDownloadFileDialog).isFalse()
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)
        Unit
    }

    @Test fun addLocalFilesButtonClick() {
        instance.onClick()
        assertThat(instance.expanded).isTrue()
        assertThat(instance.showingAddLocalFilesDialog).isFalse()
        instance.onAddLocalFilesButtonClick()
        assertThat(instance.expanded).isFalse()
        assertThat(instance.showingAddLocalFilesDialog).isTrue()
    }

    @Test fun addLocalFileDialogDismiss() {
        addLocalFilesButtonClick()
        instance.onAddLocalFilesDialogDismiss()
        assertThat(instance.showingAddLocalFilesDialog).isFalse()
    }

    @Test fun addLocalFileDialogConfirm() = runBlocking {
        addLocalFilesButtonClick()
        val testTrack = Track("uriString", "name")
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onAddLocalFilesDialogConfirm(
            trackUris = listOf(Uri.parse(testTrack.uriString)),
            trackNames = listOf(testTrack.name))
        assertThat(instance.showingAddLocalFilesDialog).isFalse()
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)
        Unit
    }

    @Test fun addingRepeatFileFailsWithMessage() = runBlocking {
        var latestMessage: MessageHandler.Message? = null
        // The Dispatchers.setMain call is necessary to ensure that the
        // view model's jobs that it launches in its own viewModelScope
        // are dispatched immediately.
        Dispatchers.setMain(TestCoroutineDispatcher())
        launch { latestMessage = messageHandler.messages.first() }

        val testTrack = Track("uriString", "name")
        val trackUris = listOf(Uri.parse(testTrack.uriString))
        val trackNames = listOf(testTrack.name)
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onAddLocalFilesDialogConfirm(trackUris, trackNames)
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)

        instance.onAddLocalFilesDialogConfirm(trackUris, trackNames)
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)

        assertThat(latestMessage?.stringResource?.stringResId)
            .isEqualTo(R.string.track_already_exists_error_message)
    }
}