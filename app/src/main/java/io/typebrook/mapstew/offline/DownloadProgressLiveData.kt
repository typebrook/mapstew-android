package io.typebrook.mapstew.offline

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import androidx.lifecycle.LiveData
import kotlinx.coroutines.*
import kotlin.coroutines.CoroutineContext

// Modified from https://gist.github.com/FhdAlotaibi/678eb1f4fa94475daf74ac491874fc0e
// Many thanks! FhdAlotaibi

data class DownloadItem(
    val bytesDownloadedSoFar: Long = -1,
    val totalSizeBytes: Long = -1,
    val status: Int,
    val uri: String
)

class DownloadProgressLiveData(private val activity: Activity) :
    LiveData<List<DownloadItem>>(),
    CoroutineScope {

    private val downloadManager by lazy {
        activity.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
    }

    private val job = Job()

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.IO + job

    override fun onActive() {
        super.onActive()
        launch {
            while (isActive) {
                val query = DownloadManager.Query()
                val cursor = downloadManager.query(query)
                val list = mutableListOf<DownloadItem>()

                while (cursor.moveToNext()) {
                    when (val status = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS))) {
                        DownloadManager.STATUS_PENDING,
                        DownloadManager.STATUS_RUNNING,
                        DownloadManager.STATUS_PAUSED -> with(cursor) {
                            getInt(getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            val uri = getString(getColumnIndex(DownloadManager.COLUMN_URI))
                            val bytesDownloadedSoFar =
                                getInt(getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
                            val totalSizeBytes =
                                getInt(getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
                            list.add(
                                DownloadItem(
                                    bytesDownloadedSoFar.toLong(),
                                    totalSizeBytes.toLong(),
                                    status,
                                    uri
                                )
                            )
                        }
                    }
                }
                postValue(list)
                delay(300)
            }
        }
    }

    override fun onInactive() {
        super.onInactive()
        job.cancel()
    }
}