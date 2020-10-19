package com.example.sample.map

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import com.example.sample.ui.main.MapViewModel
import com.example.sample.ui.main.zoom
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
        updateCameraPosition(CameraUpdateFactory.setZoom(model.center.value.zoom))

        setMapChangeListener(object : MapChangeListener {
            override fun onViewComplete() {}
            override fun onRegionWillChange(animated: Boolean) {}
            fun updateModel() {
                model.center.value = cameraPosition.run { Triple(longitude, latitude, zoom) }
            }

            override fun onRegionIsChanging() {
                updateModel()
            }

            override fun onRegionDidChange(animated: Boolean) {
                updateModel()
            }
        })

        model.target.observe(viewLifecycleOwner) { camera ->
            flyToCameraPosition(
                CameraPosition().apply {
                    longitude = camera.first
                    latitude = camera.second
                    zoom = camera.zoom
                }, 400, null
            )
        }

        Unit
    } ?: Unit
}
