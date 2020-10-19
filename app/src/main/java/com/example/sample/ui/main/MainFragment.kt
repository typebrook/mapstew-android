package com.example.sample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.observe
import com.example.sample.R
import com.example.sample.databinding.MainFragmentBinding
import com.example.sample.geometry.*
import com.example.sample.map.MapboxFragment

class MainFragment : Fragment() {

    private val mapModel by activityViewModels<MapViewModel>()
    private val binding by lazy { MainFragmentBinding.inflate(layoutInflater) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

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
            val xy = camera.wgs84LongLat.convert(CoordRefSys.WGS84, mapModel.crsState.value.crs)
            coordinates.text = when (mapModel.crsState.value.expression) {
                CoordExpression.Degree -> xy2DegreeString(xy)
                CoordExpression.DegMin -> xy2DegMinString(xy)
                CoordExpression.DMS -> xy2DMSString(xy)
                else -> xy2IntString(xy)
            }.run { "$first $second" }
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
    }
}
