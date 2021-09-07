package io.typebrook.mapstew.survey

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import androidx.core.view.isGone
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.ViewModel
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.typebrook.mapstew.ApplicationConstants.ATTACH_PHOTO_MAXHEIGHT
import io.typebrook.mapstew.ApplicationConstants.ATTACH_PHOTO_MAXWIDTH
import io.typebrook.mapstew.ApplicationConstants.ATTACH_PHOTO_QUALITY
import io.typebrook.mapstew.BuildConfig
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.FragmentAttachPhotoBinding
import io.typebrook.mapstew.ktx.toast
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import io.typebrook.mapstew.storage.getPickImageIntent
import io.typebrook.mapstew.view.decodeScaledBitmapAndNormalize
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class AttachPhotoFragment : Fragment() {

    private val binding by lazy { FragmentAttachPhotoBinding.inflate(layoutInflater) }

    private var currentImagePath: String? = null
    private lateinit var noteImageAdapter: SurveyImageAdapter
    private val imageModel: ImageModel by viewModels({ requireParentFragment() })

    private val takePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // If imageUri is not null, then image comes from other FileProvider
            // We should copy it into currentImagePath
            it.data?.data?.let { imageUri ->
                try {
                    requireContext().contentResolver.openInputStream(imageUri)
                        ?.copyTo(FileOutputStream(currentImagePath!!))
                } catch (e: IOException) {
                    Timber.e("Unable to create file for photo $e")
                    context?.toast(R.string.quest_leave_new_note_create_image_error)
                }
            }
            onTakePhotoResult(it.resultCode == RESULT_OK)
        }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = binding.root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {
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

        noteImageAdapter = SurveyImageAdapter(paths, requireContext())
        photoListView.layoutManager = LinearLayoutManager(
            context,
            LinearLayoutManager.HORIZONTAL,
            false
        )
        photoListView.adapter = noteImageAdapter
        noteImageAdapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {
            fun assignList() { imageModel.imagePaths.value = noteImageAdapter.list.toList() }
            override fun onItemRangeChanged(start: Int, count: Int) { assignList() }
            override fun onItemRangeInserted(start: Int, count: Int) { assignList() }
            override fun onItemRangeRemoved(start: Int, count: Int) { assignList() }
            override fun onItemRangeMoved(from: Int, to: Int, count: Int) { assignList() }
        })

        imageModel.imagePaths.observe(viewLifecycleOwner) {
            val showHint = it.isEmpty()
            photoListView.isGone = showHint
            photosAreUsefulExplanation.isGone = !showHint

            if (it.size != noteImageAdapter.list.size) {
                noteImageAdapter.list = it.toMutableList()
                noteImageAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putStringArrayList(PHOTO_PATHS, ArrayList(noteImageAdapter.list))
        outState.putString(CURRENT_PHOTO_PATH, currentImagePath)
    }

    private fun takePhoto() {
        try {
            val photoFile = createImageFile()
            val photoUri = if (Build.VERSION.SDK_INT > 21) {
                // Use FileProvider for getting the content:// URI, see:
                // https://developer.android.com/training/camera/photobasics.html#TaskPath
                FileProvider.getUriForFile(
                    requireContext(),
                    BuildConfig.APPLICATION_ID + getString(R.string.file_provider_name),
                    photoFile
                )
            } else {
                photoFile.toUri()
            }
            currentImagePath = photoFile.path
            takePhotoLauncher.launch(getPickImageIntent(photoUri))
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

    private fun onTakePhotoResult(saved: Boolean) {
        val file = File(currentImagePath!!)
        if (saved && file.exists() && file.totalSpace > 0) {
            try {
                val path = currentImagePath!!
                val bitmap = decodeScaledBitmapAndNormalize(
                    path,
                    ATTACH_PHOTO_MAXWIDTH,
                    ATTACH_PHOTO_MAXHEIGHT
                ) ?: throw IOException()
                val out = FileOutputStream(path)
                bitmap.compress(Bitmap.CompressFormat.JPEG, ATTACH_PHOTO_QUALITY, out)

                with(noteImageAdapter) {
                    list.add(path)
                    notifyItemInserted(list.size - 1)
                }
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
        if (!file.createNewFile()) throw IOException("Photo file with exactly the same name already exists")
        return file
    }

    fun deleteImages() {
        for (path in noteImageAdapter.list) {
            val file = File(path)
            if (file.exists()) {
                file.delete()
            }
        }
    }

    companion object {
        class ImageModel : ViewModel() {
            val imagePaths = SafeMutableLiveData(listOf<String>())
        }

        private const val PHOTO_PATHS = "photo_paths"
        private const val CURRENT_PHOTO_PATH = "current_photo_path"
    }
}
