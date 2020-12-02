package com.example.sample.network

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.sample.notification.createChannel
import okhttp3.ResponseBody
import retrofit2.Retrofit
import retrofit2.awaitResponse
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutionException


// TODO i18n for non logger text
private const val NOTIFICATION_CHANNEL_ID = "Download"
private const val NOTIFICATION_ID = 8888

// ref: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/long-running
class DownloadWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    init {
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.createChannel(NOTIFICATION_CHANNEL_ID, "Download Offline Maps")
        }
    }

    override suspend fun doWork(): Result {

        val progress = "Starting Download"
        val foregroundInfo = ForegroundInfo(NOTIFICATION_ID, createNotification(progress))
        setForeground(foregroundInfo)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://github.com/")
            .build()
        val service: GithubService = retrofit.create(GithubService::class.java)
        val call = service.downloadFileWithFixedUrl()
        Log.d(javaClass.name, "Fetching remote file")
        val response = call.awaitResponse()
        val body = response.body()
        if (response.isSuccessful && body != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            Log.d(javaClass.name, "Server contacted and has file")
            val writtenToDisk: Boolean = writeResponseBodyToStorage(body, "foo.pbf")
            Log.d(javaClass.name, "File saved? $writtenToDisk")
        } else {
            Log.d(javaClass.name, "Server contact failed")
        }

        return Result.success()
    }

    private fun createNotification(progress: String): Notification =
        with(NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)) {
            val title = "Downloading Offline Maps"
            setContentTitle(title)
            setTicker(title)
            setContentText(progress)
            setSmallIcon(android.R.drawable.sym_def_app_icon)
            setOngoing(true)
            setOnlyAlertOnce(true)

            // This PendingIntent can be used to cancel the worker
            val cancel = "Cancel"
            val intent = WorkManager.getInstance(context)
                .createCancelPendingIntent(id)
            // Add the cancel action to the notification which can
            // be used to cancel the worker
            addAction(android.R.drawable.ic_delete, cancel, intent)

            build()
        }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun writeResponseBodyToStorage(body: ResponseBody, fileName: String): Boolean = try {

        val uri: Uri = with(ContentValues()) {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, this)
        } ?: throw IOException()

        context.contentResolver.openFileDescriptor(uri, "w")?.use {
            body.byteStream().use { input ->
                FileOutputStream(it.fileDescriptor).use { output ->
                    val data = ByteArray(8192)
                    val fileSize = body.contentLength()
                    var read: Int
                    var progress = 0F

                    while (input.read(data).also { read = it } != -1) {
                        output.write(data, 0, read)

                        progress += read
                        val progressString = (progress / fileSize * 100).toInt().toString() + "%"
                        val foregroundInfo =
                            ForegroundInfo(NOTIFICATION_ID, createNotification(progressString))
                        try {
                            setForegroundAsync(foregroundInfo).get()
                            Log.d(javaClass.name, "Write stream body to storage: $progressString")
                        } catch (exception: ExecutionException) {
                            Log.d(
                                javaClass.name,
                                "The following error message should caused by the cancellation of CoroutineWorker",
                                exception
                            )
                            throw IOException()
                        }
                    }
                }
            }
        } ?: throw IOException()
        true
    } catch (e: IOException) {
        Log.d(javaClass.name, "Fail to write response body to $fileName")
        false
    }

    companion object {

        fun enqueue(context: Context) = with(WorkManager.getInstance(context)) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .build()
            enqueueUniqueWork(NOTIFICATION_CHANNEL_ID, ExistingWorkPolicy.KEEP, workRequest)
        }
    }
}