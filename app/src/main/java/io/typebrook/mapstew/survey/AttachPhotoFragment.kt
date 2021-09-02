package io.typebrook.mapstew.survey

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.typebrook.mapstew.ApplicationConstants.ATTACH_PHOTO_MAXHEIGHT
import io.typebrook.mapstew.ApplicationConstants.ATTACH_PHOTO_MAXWIDTH
import io.typebrook.mapstew.ApplicationConstants.ATTACH_PHOTO_QUALITY
import io.typebrook.mapstew.BuildConfig
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.FragmentAttachPhotoBinding
import io.typebrook.mapstew.ktx.toast
import io.typebrook.mapstew.storage.getPickImageIntent
import io.typebrook.mapstew.view.decodeScaledBitmapAndNormalize
import kotlinx.android.synthetic.main.fragment_attach_photo.*
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AttachPhotoFragment : Fragment() {

    private val binding by lazy { FragmentAttachPhotoBinding.inflate(layoutInflater) }

    val imagePaths: List<String> get() = noteImageAdapter.list
    private val photosListView : RecyclerView by lazy { binding.photoListView }
    private val photosAreUsefulrExplanation : TextView by lazy { binding.photosAreUsefulExplanation }

    private var currentImagePath: String? = null

    private lateinit var noteImageAdapter: SurveyImageAdaper

    private val takePhoto = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        onTakePhotoResult(it.resultCode == RESULT_OK, it.data?.data)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return binding.root
    }

    private fun updateHintVisibility(){
        val isImagePathsEmpty = imagePaths.isEmpty()
        photosListView.isGone = isImagePathsEmpty
        photosAreUsefulrExplanation.isGone = !isImagePathsEmpty
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        takePhotoButton.setOnClickListener { takePhoto() }

        val paths: ArrayList<String>
        if (savedInstanceState != null) {
            paths = savedInstanceState.getStringArrayList(PHOTO_PATHS)!!
            currentImagePath = savedInstanceState.getString(CURRENT_PHOTO_PATH)
        } else {
            paths = ArrayList()
            currentImagePath = null
        }

        noteImageAdapter = SurveyImageAdaper(paths, requireContext())
        photosListView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        photosListView.adapter = noteImageAdapter
        noteImageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            override fun onItemRangeChanged(start: Int, count: Int) { updateHintVisibility() }
            override fun onItemRangeInserted(start: Int, count: Int) { updateHintVisibility() }
            override fun onItemRangeRemoved(start: Int, count: Int) { updateHintVisibility() }
            override fun onItemRangeMoved(from: Int, to: Int, count: Int) { updateHintVisibility() }
        })
        updateHintVisibility()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(PHOTO_PATHS, ArrayList(imagePaths))
        outState.putString(CURRENT_PHOTO_PATH, currentImagePath)
    }

    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            val photoUri = if (Build.VERSION.SDK_INT > 21) {
                // Use FileProvider for getting the content:// URI, see:
                // https://developer.android.com/training/camera/photobasics.html#TaskPath
                FileProvider.getUriForFile(requireContext(), BuildConfig.APPLICATION_ID + getString(R.string.file_provider_name), photoFile)
            } else {
                photoFile.toUri()
            }
            currentImagePath = photoFile.path
            takePhoto.launch(getPickImageIntent(photoUri))
        } catch (e: ActivityNotFoundException) {
            Timber.e("Could not find a camera app $e")
            context?.toast(R.string.no_camera_app)
        } catch (e: IOException) {
            Timber.e("Unable to create file for photo $e")
            context?.toast(R.string.quest_leave_new_note_create_image_error)
        } catch (e: IllegalArgumentException) {
            Timber.e("Unable to create file for photo $e")
            context?.toast(R.string.quest_leave_new_note_create_image_error)
        }
    }

    private fun onTakePhotoResult(saved: Boolean, imageUri: Uri?) {
        // If imageUri is not null, then image comes from other FileProvider
        // We should copy it into currentImagePath
        if (imageUri != null) {
            try {
                requireContext().contentResolver.openInputStream(imageUri)
                    ?.copyTo(FileOutputStream(currentImagePath!!))
            } catch (e: IOException) {
                Timber.e("Unable to create file for photo $e")
                context?.toast(R.string.quest_leave_new_note_create_image_error)
            }
        }
        if (saved) {
            try {
                val path = currentImagePath!!
                val bitmap = decodeScaledBitmapAndNormalize(path, ATTACH_PHOTO_MAXWIDTH, ATTACH_PHOTO_MAXHEIGHT) ?: throw IOException()
                val out = FileOutputStream(path)
                bitmap.compress(Bitmap.CompressFormat.JPEG, ATTACH_PHOTO_QUALITY, out)

                noteImageAdapter.list.add(path)
                noteImageAdapter.notifyItemInserted(imagePaths.size - 1)
            } catch (e: IOException) {
                Timber.e("Unable to rescale the photo $e")
                context?.toast(R.string.quest_leave_new_note_create_image_error)
                removeCurrentImage()
            }
        } else {
            removeCurrentImage()
        }
        currentImagePath = null
    }

    private fun removeCurrentImage() {
        currentImagePath?.let {
            val photoFile = File(it)
            if (photoFile.exists()) {
                photoFile.delete()
            }
        }
    }

    private fun createImageFile(): File {
        val directory = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        val imageFileName = "photo_" + System.currentTimeMillis() + ".jpg"
        val file = File(directory, imageFileName)
        if(!file.createNewFile()) throw IOException("Photo file with exactly the same name already exists")
        return file
    }

    fun deleteImages() {
        for (path in imagePaths) {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    companion object {
        private const val PHOTO_PATHS = "photo_paths"
        private const val CURRENT_PHOTO_PATH = "current_photo_path"
    }
}
