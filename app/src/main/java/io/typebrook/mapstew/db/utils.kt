package io.typebrook.mapstew.db

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import de.westnordost.osmapi.OsmConnection
import de.westnordost.osmapi.common.SingleElementHandler
import de.westnordost.osmapi.notes.Note
import de.westnordost.osmapi.notes.NotesParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream


val Fragment.db get() = AppDatabase.getDatabase(requireContext())

fun Fragment.uploadSurveys() {
    val osm = OsmConnection(
        "https://master.apis.dev.openstreetmap.org/api/0.6/",
        "whatever", null
    )
    lifecycleScope.launch(Dispatchers.IO) {
        val surveys = db.surveyDao().listReadyToUpload()
        surveys.forEach { survey ->
            val call =
                "notes?lat=${survey.lat}&lon=${survey.lon}&text=${survey.content}${survey.photoUri}"
            val noteHandler = SingleElementHandler<Note>()
            osm.makeRequest(call, "POST", false, null, NotesParser(noteHandler))

            val note = noteHandler.get()
            db.surveyDao().insert(survey.copy(osmNoteId = note.id))
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
