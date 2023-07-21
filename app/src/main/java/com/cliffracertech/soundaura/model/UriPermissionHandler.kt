/* This file is part of SoundAura, which is released under
 * the terms of the Apache License 2.0. See license.md in
 * the project's root directory to see the full license. */
package com.cliffracertech.soundaura.model

import android.content.Context
import android.net.Uri
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class UriPermissionHandler @Inject constructor(
    @ApplicationContext
    private val context: Context,
//    private val playlistDao: PlaylistDao,
) {
    private val contentResolver = context.contentResolver
    private val persistedPermissionAllowance =
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) 128 else 512

    /**
     * Take persistable permissions for each [Uri] in [uris], if space permits.
     * If the size of [uris] is greater than the remaining permission count, the
     * permissions will not be taken if [insertPartial] is false. Otherwise, As
     * any uri permissions as space permits will be taken.
     *
     * @return The list of uris that the app has persisted permissions for.
     *     If the remaining permission count is less than the size of [uris]
     *     and [insertPartial] is true, then the partial list of [Uri]s whose
     *     persistable permissions were taken will be returned. If there is
     *     not enough space and [insertPartial] is false, then an empty list
     *     will be returned.
     */
    fun takeUriPermissions(
        uris: List<Uri>,
        insertPartial: Boolean = true
    ): List<Uri> {
        val permissionsCount = context.contentResolver.persistedUriPermissions.size
        val remainingSpace = persistedPermissionAllowance - permissionsCount
        val hasEnoughSpace = remainingSpace >= uris.size

        return when {
            hasEnoughSpace -> uris
            insertPartial ->  uris.subList(0, remainingSpace - 1)
            else ->           emptyList()
        }.onEach { contentResolver.takePersistableUriPermission(it, 0) }
    }

    /** Release any persisted permissions for the [Uri]s in [uris]. */
    fun releasePermissions(uris: List<Uri>) {
        for (uri in uris)
            contentResolver.releasePersistableUriPermission(uri, 0)
    }
}