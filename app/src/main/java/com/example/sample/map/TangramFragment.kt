package com.example.sample.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import com.example.sample.ui.main.MapViewModel
import com.mapzen.tangram.*
import com.mapzen.tangram.MapView.MapReadyCallback

class TangramFragment : Fragment(), MapReadyCallback {

    private val model by activityViewModels<MapViewModel>()

    private val mapView by lazy {
        MapView(requireContext()).apply { getMapAsync(this@TangramFragment) }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = mapView

    override fun onMapReady(mapController: MapController?) = mapController?.run {

        loadSceneFileAsync("basic.yaml", null)
        updateCameraPosition(CameraUpdateFactory.setZoom(12F))

        setMapChangeListener(object : MapChangeListener {
            override fun onViewComplete() {}
            override fun onRegionWillChange(animated: Boolean) {}
            fun updateModel() {
                model.coordinate.value = cameraPosition.run { longitude to latitude }
            }

            override fun onRegionIsChanging() {
                updateModel()
            }

            override fun onRegionDidChange(animated: Boolean) {
                updateModel()
            }
        })

        model.target.observe(viewLifecycleOwner) { xy ->
            flyToCameraPosition(
                CameraPosition().apply {
                    longitude = xy.first
                    latitude = xy.second
                    zoom = this@run.cameraPosition.zoom
                }, 400, null
            )
        }

        Unit
    } ?: Unit
}
