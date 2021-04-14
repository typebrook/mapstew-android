package io.typebrook.mapstew

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import io.typebrook.mapstew.offline.downloadableMaps
import io.typebrook.mapstew.offline.importMBTilesIntoMbgl
import java.io.File
import java.io.FileOutputStream


class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        context ?: return
        val action = intent?.action ?: return
        if (action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val downloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
        if (downloadId == -1L) return

        val downloadManager =
            context.applicationContext.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

        // Get information about downloaded file
        val c: Cursor = downloadManager.query(DownloadManager.Query())
        if (!c.moveToFirst()) return
        val columnIndex: Int = c.getColumnIndex(DownloadManager.COLUMN_STATUS)
        if (DownloadManager.STATUS_SUCCESSFUL != c.getInt(columnIndex)) return
        val remoteUri: String = c.getString(c.getColumnIndex(DownloadManager.COLUMN_URI))
        val map = downloadableMaps.firstOrNull { it.path == remoteUri } ?: return

        val externalFileUri = downloadManager.getUriForDownloadedFile(downloadId) ?: return
        val file = File(context.filesDir, map.path.substringAfterLast('/'))
        context.contentResolver.openInputStream(externalFileUri).use { input ->
            FileOutputStream(file).use { output -> input?.copyTo(output) }
        }

        context.importMBTilesIntoMbgl(file, map.urlTemplate)
    }
}