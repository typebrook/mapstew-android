package io.typebrook.mapstew.network

import android.app.Notification
import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LiveData
import androidx.work.*
import io.typebrook.mapstew.notification.createChannel
import okhttp3.ResponseBody
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ExecutionException

// TODO i18n for non logger text
private const val NOTIFICATION_CHANNEL_ID = "DOWNLOAD"

// ref: https://developer.android.com/topic/libraries/architecture/workmanager/advanced/long-running
class DownloadWorker(private val context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    private val path: String = inputData.getString(KEY_PATH) ?: throw InputNotEnoughException
    private val urlTemplate: String = inputData.getString(KEY_URL_TEMPLATE) ?: throw InputNotEnoughException
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
        Timber.d("Fetching remote file")
        val response = service.downloadFileWithFixedUrl(path)

        if (!response.isSuccessful) {
            Timber.d("Server contact failed")
            Result.failure()
        }
        val body = response.body() ?: return Result.failure()

        Timber.d("Server contacted and has file")
        val writtenToDisk: Boolean = writeResponseBodyToStorage(body, unfinishedFileName)
        Timber.d("File saved? $writtenToDisk")
        if (!writtenToDisk) return Result.failure()

        val dbFile = context.getDatabasePath(fileName)
        context.getDatabasePath(unfinishedFileName).renameTo(dbFile)
        val urlTemplateInserted = insertUrlTemplate(dbFile, urlTemplate)
        if (!urlTemplateInserted) {
            Timber.d("Fail to insert url template")
            return Result.failure()
        } else {
            Timber.d("Success to insert url template")
        }

        return Result.success()
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
        Timber.d("Write to ${file.absolutePath}")

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
                    val progressDetails = "%.1f".format(progress / 1024 / 1024) +
                            "MB/${fileSize / 1024 / 1024}MB  $progressString"
                    val foregroundInfo =
                        ForegroundInfo(notificationId, createNotification(progressDetails))
                    setForegroundAsync(foregroundInfo).get()
                    setProgressAsync(workDataOf(DATA_KEY_PROGRESS to progressString))
                    Timber.d("Write stream body to storage: $progressString $progress/$fileSize")
                }
            }
        }
        true
    } catch (e: IOException) {
        Timber.d("Fail to write response body to $fileName")
        false
    } catch (exception: ExecutionException) {
        Timber.d("The following error message should caused by the cancellation of CoroutineWorker: ${exception.localizedMessage}")
        false
    }

    fun insertUrlTemplate(dbFile: File, urlTemplate: String): Boolean = try {
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE).use { db ->
            val record = ContentValues().apply {
                put("name", KEY_URL_TEMPLATE)
                put("value", urlTemplate)
            }
            db.insertWithOnConflict("metadata", null, record, SQLiteDatabase.CONFLICT_REPLACE)
        }
        true
    } catch (e: SQLiteException) {
        false
    }

    companion object {

        const val KEY_PATH = "path"
        const val KEY_URL_TEMPLATE = "url_template"
        const val DATA_KEY_PROGRESS = "PROGRESS"

        fun enqueue(
            context: Context,
            path: String,
            urlTemplate: String,
            tag: String
        ): LiveData<WorkInfo> {
            val workManager = WorkManager.getInstance(context)
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val workRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
                .addTag(tag)
                .setConstraints(constraints)
                .setInputData(
                    workDataOf(
                        KEY_PATH to path,
                        KEY_URL_TEMPLATE to urlTemplate
                    )
                )
                .build()
            workManager.enqueueUniqueWork(
                path,
                ExistingWorkPolicy.KEEP,
                workRequest
            )
            return workManager.getWorkInfoByIdLiveData(workRequest.id)
        }

        object InputNotEnoughException : Exception()
    }
}