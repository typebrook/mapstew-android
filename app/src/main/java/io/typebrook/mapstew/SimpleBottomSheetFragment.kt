package io.typebrook.mapstew

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.gson.internal.bind.util.ISO8601Utils
import io.typebrook.mapstew.databinding.FragmentSimpleBottomSheetBinding
import io.typebrook.mapstew.db.Note
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.storage.getPickImageIntent
import io.typebrook.mapstew.storage.newImageUri
import kotlinx.android.synthetic.main.fragment_simple_bottom_sheet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.Exception
import java.util.*

/** Abstract base class for (quest) bottom sheets
 *
 * Note: The AbstractBottomSheetFragment currently assumes that it will be inflated with the views
that are in fragment_quest_answer by any subclass!*/
class SimpleBottomSheetFragment : Fragment() {

    private val binding by lazy { FragmentSimpleBottomSheetBinding.inflate(layoutInflater) }
    private val model by activityViewModels<MapViewModel>()

    private var photoUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {

        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet)

        bottomSheetBehavior.addBottomSheetCallback(object :
            BottomSheetBehavior.BottomSheetCallback() {
            override fun onStateChanged(bottomSheet: View, newState: Int) {
                if (newState != BottomSheetBehavior.STATE_HIDDEN) {
                    shadow.visibility = View.VISIBLE
                } else {
                    shadow.visibility = View.GONE
                    model.displayBottomSheet.value = false
                }
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })

        model.displayBottomSheet.observe(viewLifecycleOwner) { display ->
            bottomSheetBehavior.state = if (display)
                BottomSheetBehavior.STATE_EXPANDED else
                BottomSheetBehavior.STATE_HIDDEN
        }

        model.focusedFeatureId.observe(viewLifecycleOwner) { id ->
            details.text = id
            content.text.clear()
            id ?: return@observe

            lifecycleScope.launch {
                val note: Note = withContext(Dispatchers.IO) {
                    db.noteDao().getFromId(id).firstOrNull()
                } ?: return@launch
                content.setText(note.content)
                try {
                    photoUri = note.photoUri
                    image.setImageURI(note.photoUri)
                } catch (e: Exception) {
                    
                }
            }
        }

        photo.setOnClickListener {
//            val takePictureIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val uri = newImageUri("${ISO8601Utils.format(Date())}.png")
            photoUri = uri

            try {
                startActivityForResult(getPickImageIntent(uri), REQUEST_IMAGE_CAPTURE)
            } catch (e: ActivityNotFoundException) {
                // display error state to the user
            }
        }

        // FIXME Just a quick workround
        details.setOnLongClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                val id = model.focusedFeatureId.value ?: return@launch
                val note: Note = db.noteDao().getFromId(id).firstOrNull() ?: return@launch
                db.noteDao().delete(note)
                withContext(Dispatchers.Main) {
                    model.displayBottomSheet.postValue(false)
                }
            }
            true
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            val id = model.focusedFeatureId.value ?: return
            val uri = photoUri ?: return
            photoUri = null

            val note = Note(
                id = id,
                lon = model.center.value.first,
                lat = model.center.value.second,
                content = binding.content.text.toString(),
                photoUri = uri
            )
            lifecycleScope.launch(Dispatchers.IO) {
                db.noteDao().insert(note)
            }

            // exit bottom sheet
            binding.content.text.clear()
            model.displayBottomSheet.value = false
        }
    }

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 0
    }
}