package com.example.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.mapzen.tangram.CameraPosition
import com.mapzen.tangram.MapController
import com.mapzen.tangram.MapView.MapReadyCallback

class TangramFragment : Fragment(), MapReadyCallback {

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
    } ?: Unit
}