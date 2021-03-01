package io.typebrook.mapstew.db

import android.content.Context
import androidx.fragment.app.Fragment
import java.io.File
import java.io.FileOutputStream

val Fragment.db get() = AppDatabase.getDatabase(requireContext())

fun Context.copyAssetToDatabase(asset: String): String = assets.open(asset).use { inputStream ->
    val path = getDatabasePath(asset).path
    val outputFile = File(path)
    FileOutputStream(outputFile).use { outputStream ->
        inputStream.copyTo(outputStream)
        outputStream.flush()
    }
    return path
}