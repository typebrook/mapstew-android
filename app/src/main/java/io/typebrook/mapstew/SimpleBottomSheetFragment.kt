package io.typebrook.mapstew


import android.R
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.bottomsheet.BottomSheetBehavior
import io.typebrook.mapstew.databinding.FragmentSimpleBottomSheetBinding
import io.typebrook.mapstew.main.MapViewModel

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
            title.text = features.size.toString()

            with(featureSelector) {
                adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.simple_dropdown_item_1line,
                    features.map { it.name ?: it.osmId }
                )
            }
        }
    }
}

