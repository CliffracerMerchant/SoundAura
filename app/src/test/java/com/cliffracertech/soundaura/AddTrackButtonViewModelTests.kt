package com.cliffracertech.soundaura

import android.content.Context
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
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class AddTrackButtonViewModelTests {
    private lateinit var instance: AddTrackButtonViewModel
    private lateinit var trackDao: TrackDao
    private lateinit var messageHandler: MessageHandler

    @Before fun init() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, SoundAuraDatabase::class.java).build()
        trackDao = db.trackDao()
        messageHandler = MessageHandler()
        instance = AddTrackButtonViewModel(trackDao, messageHandler)
    }

    @Test fun initialState() {
        assertThat(instance.expanded).isFalse()
        assertThat(instance.showingAddLocalFileDialog).isFalse()
        assertThat(instance.showingDownloadFileDialog).isFalse()
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

    @Test fun addLocalFileButtonClick() {
        instance.onClick()
        assertThat(instance.expanded).isTrue()
        assertThat(instance.showingAddLocalFileDialog).isFalse()
        instance.onAddLocalFileButtonClick()
        assertThat(instance.expanded).isFalse()
        assertThat(instance.showingAddLocalFileDialog).isTrue()
    }

    @Test fun addLocalFileDialogDismiss() {
        addLocalFileButtonClick()
        instance.onAddLocalFileDialogDismiss()
        assertThat(instance.showingAddLocalFileDialog).isFalse()
    }

    @Test fun addLocalFileDialogConfirm() = runBlocking {
        addLocalFileButtonClick()
        val testTrack = Track("uriString", "name")
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onAddLocalFileDialogConfirm(testTrack)
        assertThat(instance.showingAddLocalFileDialog).isFalse()
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
        var tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).isEmpty()

        instance.onAddLocalFileDialogConfirm(testTrack)
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)

        instance.onAddLocalFileDialogConfirm(testTrack)
        tracks = trackDao.getAllTracks(Track.Sort.OrderAdded, null).first()
        assertThat(tracks).containsExactly(testTrack)

        assertThat(latestMessage?.stringResource?.stringResId)
            .isEqualTo(R.string.track_already_exists_error_message)
    }
}