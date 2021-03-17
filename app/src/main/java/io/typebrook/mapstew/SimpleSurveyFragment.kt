package io.typebrook.mapstew

import android.app.Activity.RESULT_OK
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore.ACTION_IMAGE_CAPTURE
import android.provider.MediaStore.EXTRA_OUTPUT
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import com.google.gson.internal.bind.util.ISO8601Utils
import io.typebrook.mapstew.databinding.FragmentSimpleBottomSheetBinding
import io.typebrook.mapstew.db.Survey
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.main.MapViewModel.Companion.ID_RAW_SURVEY
import io.typebrook.mapstew.storage.getPickImageIntent
import io.typebrook.mapstew.storage.newImageUri
import kotlinx.android.synthetic.main.fragment_simple_bottom_sheet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SimpleSurveyFragment : Fragment() {

    private val binding by lazy { FragmentSimpleBottomSheetBinding.inflate(layoutInflater) }
    private val model by activityViewModels<MapViewModel>()

    private var survey: Survey? = null
    private var photoUri: Uri? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {

        model.focusedFeatureId.observe(viewLifecycleOwner) { id ->
            survey = null
            content.text.clear()
            image.setImageURI(null)
            details.text = id
            id ?: return@observe

            lifecycleScope.launch setWithSurvey@{
                val key = try {
                    id.toLong()
                } catch (e: NumberFormatException) {
                    // If it is a new survey, take a photo directly
                    if (model.displayBottomSheet.value) {
                        val uri = newImageUri("${ISO8601Utils.format(Date())}.png")
                        photoUri = uri
                        startActivityForResult(
                            Intent(ACTION_IMAGE_CAPTURE).putExtra(EXTRA_OUTPUT, uri),
                            REQUEST_IMAGE_CAPTURE
                        )
                        Toast.makeText(
                            requireContext(),
                            getString(R.string.hint_take_photo),
                            Toast.LENGTH_LONG
                        ).show()
                    }
                    return@setWithSurvey
                }

                survey = withContext(Dispatchers.IO) {
                    db.surveyDao().getFromKey(key).firstOrNull()
                }?.also { it ->
                    content.setText(it.content)
                    photoUri = it.photoUri
                    image.setImageURI(it.photoUri)
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

        // FIXME Just a quick workaround
        details.setOnLongClickListener {
            lifecycleScope.launch(Dispatchers.IO) {
                survey?.let { db.surveyDao().delete(it) }
                withContext(Dispatchers.Main) {
                    model.displayBottomSheet.postValue(false)
                }
            }
            true
        }

        with(content) {
            addTextChangedListener {
                val newSurvey = survey?.copy(content = it.toString()) ?: return@addTextChangedListener

                lifecycleScope.launch(Dispatchers.IO) {
                    db.surveyDao().insert(newSurvey)
                }
            }
        }

        Unit
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {

            val id = model.focusedFeatureId.value ?: return
            val uri = photoUri ?: return
            photoUri = null

            binding.image.setImageURI(uri)

            val survey = Survey(
                relatedFeatureId = id.takeIf { it != ID_RAW_SURVEY },
                lon = model.center.value.first,
                lat = model.center.value.second,
                content = binding.content.text.toString(),
                photoUri = uri
            )
            lifecycleScope.launch(Dispatchers.IO) {
                db.surveyDao().insert(survey)
            }
        }
    }

    companion object {
        const val REQUEST_IMAGE_CAPTURE = 0
    }
}