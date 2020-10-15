package com.example.sample.map

import android.content.Context
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import com.example.sample.R
import com.example.sample.ui.main.MapViewModel
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.SupportMapFragment

class MapboxFragment : SupportMapFragment() {

    private val model by activityViewModels<MapViewModel>()
    private val defaultStyle by lazy { requireContext().getString(R.string.uri_style_rudymap) }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Mapbox.getInstance(requireContext(), null)
    }

    override fun onMapReady(mapboxMap: MapboxMap) = with(mapboxMap) {

        setStyle(defaultStyle)
        cameraPosition = CameraPosition.Builder()
            .zoom(12.0)
            .build()

        addOnCameraMoveListener {
            model.coordinate.value = cameraPosition.target.run { longitude to latitude }
        }

        model.target.observe(viewLifecycleOwner) { xy ->
            animateCamera {
                CameraPosition.Builder()
                    .target(LatLng(xy.second, xy.first))
                    .build()
            }
        }

        Unit
    }
}
