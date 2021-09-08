package io.typebrook.mapstew.db

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.osmapi.OsmConnection
import de.westnordost.osmapi.common.SingleElementHandler
import de.westnordost.osmapi.notes.Note
import de.westnordost.osmapi.notes.NotesParser
import io.typebrook.mapstew.network.ContentUriRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import java.io.File
import java.io.FileOutputStream


val Fragment.db get() = AppDatabase.getDatabase(requireContext())

fun Fragment.uploadSurveys() {
    val osm = OsmConnection(
        "https://openstreetmap.org/api/0.6/",
        "whatever", null
    )
    lifecycleScope.launch(Dispatchers.IO) {
        val surveys = db.surveyDao().listReadyToUpload()
        surveys.forEach { survey ->
            // Upload images
            val client = OkHttpClient.Builder().build()
//            val fileBody = ContentUriRequestBody(requireContext().contentResolver, survey.photoUri)
            val requestBody: RequestBody = MultipartBody.Builder()
                .setType(MultipartBody.FORM)
//                .addFormDataPart("file", survey.photoUri.lastPathSegment, fileBody)
                .build()
            val request = Request.Builder()
                .url("https://0x0.st")
                .post(requestBody)
                .build()
            val photoUrl = try {
                client.newCall(request).execute().body?.string() ?: return@forEach
            } catch (e: Exception) {
                return@forEach
            }

            val call = "notes?lat=${survey.lat}&lon=${survey.lon}&text=${survey.content}%0A$photoUrl"
            val noteHandler = SingleElementHandler<Note>()
            osm.makeRequest(call, "POST", false, null, NotesParser(noteHandler))

            val note = noteHandler.get()
            db.surveyDao().update(survey.copy(osmNoteId = note.id))
        }
    }
}

fun Context.copyAssetToDatabase(asset: String): String = assets.open(asset).use { inputStream ->
    val path = getDatabasePath(asset).path
    val outputFile = File(path)
    FileOutputStream(outputFile).use { outputStream ->
        inputStream.copyTo(outputStream)
        outputStream.flush()
    }
    return path
}
