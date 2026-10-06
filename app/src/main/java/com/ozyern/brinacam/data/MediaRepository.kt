package com.ozyern.brinacam.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import android.util.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** The newest photo or video BrinaCam saved. */
data class SavedMedia(val uri: Uri, val mimeType: String, val thumbnail: Bitmap?)

/** Where captures go (DCIM/BrinaCam via MediaStore) and how they're read back. */
class MediaRepository(private val context: Context) {

    fun newImageValues(): ContentValues = values("IMG_", "image/jpeg")

    fun newVideoValues(): ContentValues = values("VID_", "video/mp4")

    suspend fun thumbnail(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        runCatching { context.contentResolver.loadThumbnail(uri, Size(256, 256), null) }.getOrNull()
    }

    suspend fun latest(): SavedMedia? = withContext(Dispatchers.IO) {
        runCatching {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.MEDIA_TYPE,
            )
            val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)"
            val args = arrayOf(
                "$SAVE_DIR%",
                MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
            )
            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection, selection, args,
                "${MediaStore.MediaColumns.DATE_ADDED} DESC",
            )?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                val id = cursor.getLong(0)
                val isVideo = cursor.getInt(1) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO
                val uri = ContentUris.withAppendedId(
                    if (isVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                SavedMedia(uri, if (isVideo) "video/mp4" else "image/jpeg", null)
            }
        }.getOrNull()
    }?.let { it.copy(thumbnail = thumbnail(it.uri)) }

    private fun values(prefix: String, mime: String) = ContentValues().apply {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        put(MediaStore.MediaColumns.DISPLAY_NAME, prefix + stamp)
        put(MediaStore.MediaColumns.MIME_TYPE, mime)
        put(MediaStore.MediaColumns.RELATIVE_PATH, SAVE_DIR)
    }

    companion object {
        const val SAVE_DIR = "DCIM/BrinaCam"
    }
}
