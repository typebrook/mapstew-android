package com.example.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.sample.ui.main.MapViewModel
import com.mapzen.tangram.CameraPosition
import com.mapzen.tangram.MapChangeListener
import com.mapzen.tangram.MapController
import com.mapzen.tangram.MapView.MapReadyCallback

class TangramFragment : Fragment(), MapReadyCallback {

    private val model by activityViewModels<MapViewModel>()

    private val mapView by lazy {
        com.mapzen.tangram.MapView(requireContext()).apply { getMapAsync(this@TangramFragment) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = mapView

    override fun onMapReady(mapController: MapController?) = mapController?.run {
        loadSceneFileAsync("basic.yaml", null)

        model.coordinate.value?.let {
            flyToCameraPosition(
                CameraPosition().apply {
                    longitude = it.first
                    latitude = it.second
                    zoom = 12.0F
                }, null
            )
        }

        setMapChangeListener(object : MapChangeListener {
            override fun onViewComplete() {}
            override fun onRegionWillChange(animated: Boolean) {}
            fun updateModel() {
                model.setCoordinate(cameraPosition.run { longitude to latitude })
            }

            override fun onRegionIsChanging() {
                updateModel()
            }

            override fun onRegionDidChange(animated: Boolean) {
                updateModel()
            }
        })

        model.target.observe(viewLifecycleOwner) { xy ->
            xy ?: return@observe

            flyToCameraPosition(
                CameraPosition().apply {
                    longitude = xy.first
                    latitude = xy.second
                }, null
            )
        }
    } ?: Unit
}