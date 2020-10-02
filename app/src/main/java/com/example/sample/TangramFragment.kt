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
        flyToCameraPosition(
            CameraPosition().apply {
                longitude = 121.0
                latitude = 24.5
                zoom = 11.0F
            }, null
        )
        setMapChangeListener(object : MapChangeListener {
            override fun onViewComplete() {}
            override fun onRegionWillChange(animated: Boolean) {}
            fun updateModel() {
                model.coordinate.postValue(cameraPosition.run { longitude.dec() to latitude })
                model.zoom.postValue(cameraPosition.zoom)
            }
            override fun onRegionIsChanging() {
                updateModel()
            }
            override fun onRegionDidChange(animated: Boolean) {
                updateModel()
            }
        })
    } ?: Unit
}