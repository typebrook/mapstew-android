package io.typebrook.mapstew.storage

import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Parcelable
import android.provider.MediaStore
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import com.google.gson.internal.bind.util.ISO8601Utils
import io.typebrook.mapstew.BuildConfig
import io.typebrook.mapstew.R
import java.io.File
import java.util.*

fun Fragment.newImageUri(fileName: String?): Uri = with(requireContext()) {
    val folder = File("${getExternalFilesDir(Environment.DIRECTORY_DCIM)}").apply {
        mkdirs()
    }

    val file = File(folder, fileName ?: "${ISO8601Utils.format(Date())}.png")
    if (file.exists()) file.delete()

    file.createNewFile()
    return FileProvider.getUriForFile(
        this,
        BuildConfig.APPLICATION_ID + getString(R.string.file_provider_name),
        file
    )
}

fun Fragment.getPickImageIntent(uri: Uri): Intent? {
    val pickIntent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)

    val takePhotoIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    takePhotoIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri)

    val intentList = arrayListOf(pickIntent, takePhotoIntent)

    val chooserIntent = Intent.createChooser(
        intentList.removeAt(intentList.size - 1),
        getString(R.string.select_capture_image)
    )
    chooserIntent.putExtra(
        Intent.EXTRA_INITIAL_INTENTS,
        intentList.toTypedArray<Parcelable>()
    )

    return chooserIntent
}