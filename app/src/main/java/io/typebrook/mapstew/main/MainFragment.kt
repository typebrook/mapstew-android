package io.typebrook.mapstew.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.appcompat.widget.ListPopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.MediatorLiveData
import androidx.navigation.fragment.findNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import io.typebrook.mapstew.R
import io.typebrook.mapstew.SimpleBottomSheetFragment
import io.typebrook.mapstew.databinding.MainFragmentBinding
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.map.MapboxFragment
import io.typebrook.mapstew.map.OfflineFragment
import io.typebrook.mapstew.offline.getLocalMBTiles
import kotlinx.android.synthetic.main.main_fragment.*


class MainFragment : Fragment() {

    private val mapModel by activityViewModels<MapViewModel>()
    private val binding by lazy { MainFragmentBinding.inflate(layoutInflater) }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        mapModel.mbTilesList.value = requireContext().getLocalMBTiles()
        mapModel.target.value = mapModel.center.value

        if (savedInstanceState == null) {
            requireActivity().supportFragmentManager.commit {
                replace(R.id.map_container, MapboxFragment(), null)
                replace(R.id.bottom_sheet_container, SimpleBottomSheetFragment(), null)
//              add<TangramFragment>(R.id.map_container, null)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {

        // Update text of coordinate by center of map and current coordinate referency system
        MediatorLiveData<Int>().apply {
            addSource(mapModel.center) { value = value ?: 0 + 1 }
            addSource(mapModel.crsState) { value = value ?: 0 + 1 }
        }.observe(viewLifecycleOwner) {
            val xy = mapModel.center.value.wgs84LongLat
                    .convert(CRSWrapper.WGS84, mapModel.crsState.value.crsWrapper)
            val crsState = mapModel.crsState.value
            coordinates.text = when (crsState.expression) {
                CoordExpression.Degree -> xy2DegreeString(xy).run { "$first $second" }
                CoordExpression.DegMin -> xy2DegMinString(xy).run { "$first $second" }
                CoordExpression.DMS -> xy2DMSString(xy).run { "$first $second" }
                CoordExpression.XY -> xy2IntString(xy).run { "$first $second" }
                CoordExpression.SINGLE -> if (crsState.crsWrapper is MaskedCRS) {
                    crsState.crsWrapper.mask(xy) ?: getString(R.string.out_of_boundary)
                } else {
                    String()
                }
            }
        }

        with(featuresDetails) {
            mapModel.selectedFeatures.observe(viewLifecycleOwner) { features ->
            }
        }

        coordinates.setOnClickListener {
            CoordInputDialogFragment().show(childFragmentManager, null)
        }

        zoomInButton.setOnClickListener {
            mapModel.target.value = mapModel.center.value.run { copy(third = zoom + 1) }
        }

        zoomOutButton.setOnClickListener {
            mapModel.target.value = mapModel.center.value.run { copy(third = zoom - 1) }
        }

        locatingButton.setOnClickListener {
            AndPermission.with(requireContext())
                    .runtime()
                    .permission(Permission.ACCESS_FINE_LOCATION)
                    .onGranted { mapModel.locateUser.value = true }
                    .start()
        }

        menuButton.setOnClickListener {
            with(MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)) {
                setItems(R.array.menu_items) { _, which ->
                    when (which) {
                        0 -> OfflineFragment().show(childFragmentManager, null)
                        1 -> Toast.makeText(requireContext(), "NOTHING", Toast.LENGTH_SHORT).show()
                        2 -> findNavController().navigate(
                                MainFragmentDirections.actionMainFragmentToSettingsFragment()
                        )
                    }
                }
                create()
            }.show()
        }

        layersButton.setOnClickListener {
            mapModel.displayLayers.value = true
        }

        // If feature query is finished, show the popup window to let user do selection
        mapModel.selectedFeatures.observe(viewLifecycleOwner) { features ->
            with(featuresDetails) {
                text = mapModel.details.value ?: when (val number = features.size) {
                    0 -> String()
                    1 -> features.first().name
                    else -> getString(R.string.number_features).format(number)
                }

                visibility = if (text.isNotBlank())
                    View.VISIBLE else
                    View.GONE
            }

            val point = mapModel.focusPoint.value
            if (point == null || features.isEmpty()) return@observe

            // TODO consider the case that there is only one feature
            PopupWindow(requireContext()).apply {
                contentView = ListView(requireContext()).apply {
                    adapter = ArrayAdapter<String>(requireContext(), android.R.layout.simple_list_item_1).apply {
                        val items = listOf(getString(R.string.map_btn_create_note)) + features.map {
                            it.name ?: String()
                        }
                        addAll(items)
                    }
                    setOnItemClickListener { _, _, position, id ->
                        if (position != 0) {
                            mapModel.displayBottomSheet.value = true
                            mapModel.focusedFeatureId.value = features[position - 1].osmId
                        }
                        dismiss()
                    }
                }
                height = WindowManager.LayoutParams.WRAP_CONTENT
                width = 500
                isOutsideTouchable = true
                setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.shape_bottom_sheet))
                showAsDropDown(contextMenuView.apply {
                    translationX = point.x
                    translationY = point.y
                })
                setOnDismissListener { mapModel.focusPoint.value = null }
            }
        }
    }
}
