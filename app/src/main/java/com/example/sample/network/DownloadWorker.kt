package com.example.sample.network

import android.app.Notification
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.work.*
import com.example.sample.notification.createChannel
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.lang.Exception
import java.util.concurrent.ExecutionException

const val NOTIFICATION_CHANNEL_ID = "DOWNLOAD"
const val KEY_PATH = "path"

// TODO i18n for non logger text
private const val NOTIFICATION_ID = 8888

// ref: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/long-running
class DownloadWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val path: String = inputData.getString(KEY_PATH) ?: throw NoPathSpecifiedException
    private val fileName get() = path.substringAfterLast("/")

    init {
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.createChannel(NOTIFICATION_CHANNEL_ID, "Download Offline Maps")
        }
    }

    override suspend fun doWork(): Result {

        val foregroundInfo =
            ForegroundInfo(NOTIFICATION_ID, createNotification(progress = "Starting Download"))
        setForeground(foregroundInfo)

        val retrofit = Retrofit.Builder()
            .baseUrl("https://github.com/")
            .build()
        val service: GithubService = retrofit.create(GithubService::class.java)
        Log.d(javaClass.name, "Fetching remote file")
        val response = service.downloadFileWithFixedUrl(path)
        val body = response.body()

        return if (response.isSuccessful && body != null) {
            Log.d(javaClass.name, "Server contacted and has file")
            val writtenToDisk: Boolean = writeResponseBodyToStorage(body, fileName)
            Log.d(javaClass.name, "File saved? $writtenToDisk")
            // TODO check MD5SUM
            Result.success()
        } else {
            Log.d(javaClass.name, "Server contact failed")
            with(File(path)) {
                if (exists()) delete()
            }
            Result.failure()
        }
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

    private fun writeResponseBodyToStorage(body: ResponseBody, fileName: String): Boolean = try {

        val file: File = context.getDatabasePath(fileName)
        Log.d(javaClass.name, "Write to ${file.absolutePath}")

        body.byteStream().use { input ->
            FileOutputStream(file).use { output ->
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
        true
    } catch (e: IOException) {
        Log.d(javaClass.name, "Fail to write response body to $fileName")
        false
    }

    companion object {

        fun enqueue(context: Context, path: String) {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .setConstraints(constraints)
                .setInputData(workDataOf(KEY_PATH to path))
                .build()
            workManager.enqueueUniqueWork(
                path,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
        }

        object NoPathSpecifiedException : Exception()
    }
}