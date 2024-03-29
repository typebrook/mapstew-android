package io.typebrook.mapstew.main

import android.content.res.Resources
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.commit
import androidx.lifecycle.MediatorLiveData
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import io.typebrook.mapstew.R
import io.typebrook.mapstew.survey.SimpleSurveyFragment
import io.typebrook.mapstew.databinding.FragmentMainBinding
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.db.uploadSurveys
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.map.MaplibreFragment
import io.typebrook.mapstew.map.OfflineFragment
import io.typebrook.mapstew.offline.getLocalMBTiles
import kotlin.math.roundToInt


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
                replace(R.id.map_container, MaplibreFragment(), null)
//                replace(R.id.bottom_sheet_content, SimpleSurveyFragment(), null)
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
            mapModel.target.value = mapModel.center.value.run {
                copy(third = (zoom + 0.5).roundToInt().toFloat())
            }
        }

        zoomOutButton.setOnClickListener {
            mapModel.target.value = mapModel.center.value.run {
                copy(third = (zoom - 0.51).roundToInt().toFloat())
            }
        }

        locatingButton.setOnClickListener {
            AndPermission.with(requireContext())
                    .runtime()
                    .permission(Permission.ACCESS_FINE_LOCATION)
                    .onGranted { mapModel.locateUser.value = true }
                    .start()
        }

        menuButton.setOnClickListener {
            with(PopupMenu(requireContext(), it)) {
                inflate(R.menu.main)
                setOnMenuItemClickListener { item ->
                    when (item.itemId) {
                        R.id.action_offline_maps -> OfflineFragment().show(
                            childFragmentManager,
                            null
                        )
                        R.id.action_upload_surveys -> uploadSurveys()
                        R.id.action_introduction -> Toast.makeText(
                            requireContext(),
                            "NOTHING",
                            Toast.LENGTH_SHORT
                        ).show()
                        R.id.action_settings -> findNavController().navigate(
                            MainFragmentDirections.actionMainFragmentToSettingsFragment()
                        )
                    }
                    true
                }
                show()
            }
        }

        layersButton.setOnClickListener {
            mapModel.displayLayers.value = true
        }

        mapModel.details.observe(viewLifecycleOwner) { text ->
            featuresDetails.visibility = if (text != null) View.VISIBLE else View.GONE
            featuresDetails.text = text
        }

        val screenHeight = Resources.getSystem().displayMetrics.heightPixels
        bottomSheet.layoutParams.height = (screenHeight * 0.6).toInt()

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

        mapModel.focusSurvey.observe(viewLifecycleOwner) { survey ->
            mapModel.displayBottomSheet.value = survey != null
            childFragmentManager.commit {
                replace(R.id.bottom_sheet_content, SimpleSurveyFragment(), null)
            }
        }
    }
}
