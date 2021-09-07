package io.typebrook.mapstew.survey

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.FragmentSimpleBottomSheetBinding
import io.typebrook.mapstew.db.Survey
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.main.MapViewModel
import kotlinx.android.synthetic.main.fragment_simple_bottom_sheet.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*

class SimpleSurveyFragment : Fragment() {

    private val binding by lazy { FragmentSimpleBottomSheetBinding.inflate(layoutInflater) }
    private val model by activityViewModels<MapViewModel>()
    private val imageModel: AttachPhotoFragment.Companion.ImageModel by viewModels()

    private var survey: Survey? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        if (savedInstanceState == null) {
            childFragmentManager.commit {
                replace(R.id.take_photo, AttachPhotoFragment(), null)
            }
        }
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {

        model.focusedFeatureId.observe(viewLifecycleOwner) { id ->
            survey = null
            content.text?.clear()
            details.text = id
            id ?: return@observe

            val lnglat = model.focusLngLat.value ?: return@observe
            lifecycleScope.launch setWithSurvey@{
                val key = try {
                    id.toLong()
                } catch (e: NumberFormatException) {
                    -1
                }

                survey = withContext(Dispatchers.IO) {
                    db.surveyDao().getFromKey(key).firstOrNull() ?: Survey(
                        lon = lnglat.first,
                        lat = lnglat.second,
                        content = binding.content.text.toString(),
                        photoPaths = listOf()
                    ).also { db.surveyDao().insert(it) }
                }.also { survey ->
                    with(content) {
                        setText(survey.content)
                        setSelection(survey.content.length)
                    }
                    if (survey.osmNoteId != null) {
                        details.text = "Note: ${survey.osmNoteId}"
                    }
                }

                imageModel.imagePaths.value = survey?.photoPaths ?: listOf()
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

        imageModel.imagePaths.observe(viewLifecycleOwner) {
            val newSurvey = survey?.copy(photoPaths = it) ?: return@observe
            lifecycleScope.launch(Dispatchers.IO) {
                db.surveyDao().insert(newSurvey)
            }
        }
    }
}
