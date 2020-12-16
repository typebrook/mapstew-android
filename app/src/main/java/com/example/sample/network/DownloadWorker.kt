package com.example.sample.network

import android.app.Notification
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.work.*
import com.example.sample.notification.createChannel
import okhttp3.ResponseBody
import retrofit2.Retrofit
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutionException

const val KEY_PATH = "path"

// TODO i18n for non logger text
private const val NOTIFICATION_CHANNEL_ID = "DOWNLOAD"

// ref: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/long-running
class DownloadWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val path: String = inputData.getString(KEY_PATH) ?: throw NoPathSpecifiedException
    private val fileName get() = path.substringAfterLast("/")
    private val unfinishedFileName get() = "$fileName.tmp"
    private val notificationId get() = path.hashCode()

    init {
        // Create a Notification channel if necessary
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.createChannel(NOTIFICATION_CHANNEL_ID, "Download Offline Maps")
        }
    }

    override suspend fun doWork(): Result {

        val foregroundInfo =
            ForegroundInfo(notificationId, createNotification(progress = "Starting Download"))
        setForeground(foregroundInfo)

        val service = GithubService.basicService()
        Log.d(javaClass.name, "Fetching remote file")
        val response = service.downloadFileWithFixedUrl(path)

        if (!response.isSuccessful) {
            Log.d(javaClass.name, "Server contact failed")
            Result.failure()
        }
        val body = response.body() ?: return Result.failure()

        Log.d(javaClass.name, "Server contacted and has file")
        val writtenToDisk: Boolean = writeResponseBodyToStorage(body, unfinishedFileName)
        Log.d(javaClass.name, "File saved? $writtenToDisk")

        return if (writtenToDisk) {
            context.getDatabasePath(unfinishedFileName).renameTo(context.getDatabasePath(fileName))
            Result.success()
        } else {
            Result.failure()
        }
    }

    private fun createNotification(progress: String): Notification =
        with(NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)) {
            val title = "Downloading $fileName"
            setContentTitle(title)
            setTicker(title)
            setContentText(progress)
            setSmallIcon(android.R.drawable.sym_def_app_icon)
            setOngoing(true)
            setOnlyAlertOnce(true)

            // This PendingIntent can be used to cancel the worker
            val cancel = "Cancel"
            val intent = WorkManager.getInstance(context).createCancelPendingIntent(id)
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
                        ForegroundInfo(notificationId, createNotification(progressString))
                    setForegroundAsync(foregroundInfo).get()
                    setProgressAsync(workDataOf(DATA_KEY_PROGRESS to progressString))
                    Log.d(javaClass.name, "Write stream body to storage: $progressString")
                }
            }
        }
        true
    } catch (e: IOException) {
        Log.d(javaClass.name, "Fail to write response body to $fileName")
        false
    } catch (exception: ExecutionException) {
        Log.d(
            javaClass.name,
            "The following error message should caused by the cancellation of CoroutineWorker",
            exception
        )
        false
    }

    companion object {

        const val DATA_KEY_PROGRESS = "PROGRESS"

        fun enqueue(context: Context, path: String): LiveData<WorkInfo> {
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
            return workManager.getWorkInfoByIdLiveData(workRequest.id)
        }

        object NoPathSpecifiedException : Exception()
    }
}