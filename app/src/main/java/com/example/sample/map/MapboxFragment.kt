package com.example.sample.map

import android.content.Context
import android.util.Log
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

            // FIXME This is just a simple feature query test
            model.coordinate.value
                .run { LatLng(second, first) }
                .run(mapboxMap.projection::toScreenLocation)
                .let { queryRenderedFeatures(it) }
                .forEach {
                    Log.d("jojojo", it.getStringProperty("name:latin") ?: "null")
                }
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
