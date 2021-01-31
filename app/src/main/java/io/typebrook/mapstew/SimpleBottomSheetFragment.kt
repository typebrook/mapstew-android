package io.typebrook.mapstew

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Spinner
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.typebrook.mapstew.databinding.FragmentSimpleBottomSheetBinding
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.map.TiledFeature

/** Abstract base class for (quest) bottom sheets
 *
 * Note: The AbstractBottomSheetFragment currently assumes that it will be inflated with the views
that are in fragment_quest_answer by any subclass!*/
class SimpleBottomSheetFragment : Fragment() {

    private val binding by lazy { FragmentSimpleBottomSheetBinding.inflate(layoutInflater) }
    private val model by activityViewModels<MapViewModel>()

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
                shadow.visibility = if (newState != BottomSheetBehavior.STATE_HIDDEN)
                    View.VISIBLE else
                    View.GONE
            }

            override fun onSlide(bottomSheet: View, slideOffset: Float) {
            }
        })

        model.displayBottomSheet.observe(viewLifecycleOwner) { display ->
            bottomSheetBehavior.state = if (display)
                BottomSheetBehavior.STATE_EXPANDED else
                BottomSheetBehavior.STATE_HIDDEN
        }

        model.selectedFeatures.observe(viewLifecycleOwner) { features ->
            featureSelector.setFeatures(features)
        }
    }

    private fun Spinner.setFeatures(features: List<TiledFeature>) {
        adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            features.map { it.name ?: it.osmId }
        )

        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(p0: AdapterView<*>?, v: View?, p1: Int, id: Long) {
                model.focusedFeatureId.value = if (model.displayBottomSheet.value)
                    features[p1].osmId else
                    null
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {
                TODO("Not yet implemented")
            }
        }
    }
}

