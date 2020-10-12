package com.example.sample

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.example.sample.ui.main.MapViewModel
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.maps.MapView
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.OnMapReadyCallback

class MapboxFragment : Fragment(), OnMapReadyCallback {

    private val model by activityViewModels<MapViewModel>()

    private val mapView by lazy {
        Mapbox.getInstance(requireContext(), null)
        MapView(requireContext()).apply { getMapAsync(this@MapboxFragment) }
    }
    private val defaultStyle by lazy { requireContext().getString(R.string.uri_style_rudymap) }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View = mapView

    override fun onMapReady(mapboxMap: MapboxMap) = with(mapboxMap) {
        setStyle(defaultStyle)
        cameraPosition = CameraPosition.Builder()
            .target(LatLng(25.023167, 121.585674))
            .zoom(12.0)
            .build()

        model.coordinate.value?.let {
            cameraPosition = CameraPosition.Builder()
                .target(LatLng(it.second, it.first))
                .zoom(12.0)
                .build()
        }

        addOnCameraMoveListener {
            model.coordinate.postValue(cameraPosition.target.run { longitude.dec() to latitude })
            model.zoom.postValue(cameraPosition.zoom.toFloat())
        }

        model.target.observe(viewLifecycleOwner) { xy ->
            xy ?: return@observe

            val target = LatLng(xy.second, xy.first)
            animateCamera {
                CameraPosition.Builder()
                    .target(target)
                    .build()
            }
        }
    }
}