package io.typebrook.mapstew.main

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.gson.internal.bind.util.ISO8601Utils
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import io.typebrook.mapstew.R
import io.typebrook.mapstew.SimpleBottomSheetFragment
import io.typebrook.mapstew.databinding.FragmentMainBinding
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.main.MapViewModel.Companion.ID_NOTE
import io.typebrook.mapstew.map.MapboxFragment
import io.typebrook.mapstew.map.OfflineFragment
import io.typebrook.mapstew.offline.getLocalMBTiles
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*


class MainFragment : Fragment() {

    private val mapModel by activityViewModels<MapViewModel>()
    private val binding by lazy { FragmentMainBinding.inflate(layoutInflater) }

    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        mapModel.mbTilesList.value = requireContext().getLocalMBTiles()
        mapModel.target.value = mapModel.center.value

        if (savedInstanceState == null) {
            requireActivity().supportFragmentManager.commit {
                replace(R.id.map_container, MapboxFragment(), null)
                replace(R.id.bottom_sheet, SimpleBottomSheetFragment(), null)
//              add<TangramFragment>(R.id.map_container, null)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {

        // Update text of coordinate by center of map and current coordinate reference system
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
                CoordExpression.SINGLE -> if (crsState.crsWrapper is CoordMask) {
                    crsState.crsWrapper.mask(xy, null) ?: getString(R.string.out_of_boundary)
                } else {
                    String()
                }
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

        mapModel.details.observe(viewLifecycleOwner) { text ->
            featuresDetails.visibility = if (text != null) View.VISIBLE else View.GONE
            featuresDetails.text = text
        }

        // If feature query is finished, show the popup window to let user do selection
        mapModel.selectableFeatures.observe(viewLifecycleOwner) { features ->
            val point = mapModel.focusPoint.value ?: return@observe

            // TODO consider the case that there is only one feature
            PopupWindow(requireContext()).apply {
                contentView = ListView(requireContext()).apply {
                    adapter = ArrayAdapter<String>(
                            requireContext(),
                            android.R.layout.simple_list_item_1
                    ).apply {
                        val items = listOf(getString(R.string.map_btn_create_note)) + features.map {
                            it.name ?: it.osmId.substringAfter('/')
                        }
                        addAll(items)
                    }
                    setOnItemClickListener { _, _, position, _ ->
                        mapModel.displayBottomSheet.value = true
                        mapModel.focusedFeatureId.value = if (position != 0)
                            features[position - 1].osmId else
                            "$ID_NOTE@${ISO8601Utils.format(Date())}"
                        dismiss()
                    }
                }
                height = WindowManager.LayoutParams.WRAP_CONTENT
                width = 500
                elevation = 40f
                isOutsideTouchable = true
                setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.shape_bottom_sheet))
                showAsDropDown(contextMenuView.apply {
                    translationX = point.x
                    translationY = point.y
                })
                setOnDismissListener {
                    lifecycleScope.launch {
                        delay(400)
                        if (mapModel.focusedFeatureId.value == null)
                            mapModel.focusPoint.value = null
                    }
                }
            }
        }

        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        bottomSheet.layoutParams.height = (screenHeight * 0.4).toInt()

        val bottomSheetBehavior = BottomSheetBehavior.from(bottomSheet).apply {
            addBottomSheetCallback(object :
                    BottomSheetBehavior.BottomSheetCallback() {
                override fun onStateChanged(bottomSheet: View, newState: Int) {
                    if (newState != BottomSheetBehavior.STATE_HIDDEN) {
                        shadow.visibility = View.VISIBLE
                    } else {
                        shadow.visibility = View.GONE
                        mapModel.displayBottomSheet.value = false
                    }
                }

                override fun onSlide(bottomSheet: View, slideOffset: Float) {
                }
            })
        }
        mapModel.displayBottomSheet.observe(viewLifecycleOwner) { display ->
            bottomSheetBehavior.state = if (display)
                BottomSheetBehavior.STATE_EXPANDED else
                BottomSheetBehavior.STATE_HIDDEN
        }

        mapModel.hideButtons.observe(viewLifecycleOwner) { hide ->
            listOf(menuButton, layersButton, zoomInButton, zoomOutButton, locatingButton, coordinates).forEach {
                it.isClickable = !hide
                it.animate().setDuration(350)
                        .scaleX(if (hide) 0f else 1f)
                        .scaleY(if (hide) 0f else 1f)
                        .alpha(if (hide) 0f else 1f)
            }
        }
    }
}
