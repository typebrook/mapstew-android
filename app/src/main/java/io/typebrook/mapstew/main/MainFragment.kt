package io.typebrook.mapstew.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.navigation.fragment.findNavController
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.MainFragmentBinding
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.map.MapboxFragment
import io.typebrook.mapstew.map.OfflineFragment
import io.typebrook.mapstew.offline.getLocalMBTiles
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yanzhenjie.permission.AndPermission
import com.yanzhenjie.permission.runtime.Permission
import io.typebrook.mapstew.map.TangramFragment


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
                add<MapboxFragment>(R.id.map_container, null)
//              add<TangramFragment>(R.id.map_container, null)
            }
        }

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) = with(binding) {

        mapModel.center.observe(viewLifecycleOwner) { camera ->
            val xy = camera.wgs84LongLat
                .convert(CRSWrapper.WGS84, mapModel.crsState.value.crsWrapper)
            val crsState = mapModel.crsState.value
            coordinates.text = when (crsState.expression) {
                CoordExpression.Degree -> xy2DegreeString(xy).run { "$first $second" }
                CoordExpression.DegMin -> xy2DegMinString(xy).run { "$first $second" }
                CoordExpression.DMS -> xy2DMSString(xy).run { "$first $second" }
                CoordExpression.XY -> xy2IntString(xy).run { "$first $second" }
                CoordExpression.SINGLE -> if (crsState.crsWrapper is MaskedCRS) {
                    crsState.crsWrapper.mask(xy)
                } else {
                    String()
                }
            }
        }

        mapModel.details.observe(viewLifecycleOwner) { details ->
            featuresDetails.text = details ?: String()
            featuresDetails.visibility = if (details == null) View.GONE else View.VISIBLE
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
    }
}
