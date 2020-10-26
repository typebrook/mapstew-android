package com.example.sample.map

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import com.example.sample.R
import com.example.sample.main.MapViewModel
import com.example.sample.main.zoom
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.maps.SupportMapFragment

class MapboxFragment : SupportMapFragment() {

    private val model by activityViewModels<MapViewModel>()
    private val defaultStyle by lazy { getString(R.string.uri_style_rudymap) }

    override fun onAttach(context: Context) {
        super.onAttach(context)
        Mapbox.getInstance(requireContext(), null)
    }

    override fun onMapReady(mapboxMap: MapboxMap) = with(mapboxMap) {

        setStyle(defaultStyle) { style ->
            model.locateUser.observe(viewLifecycleOwner) locate@{ enable ->
                if (enable) enableLocationComponent(style)
            }
        }
        cameraPosition = CameraPosition.Builder()
            .zoom(model.center.value.zoom.toDouble())
            .build()

        addOnCameraMoveListener {
            model.center.value = cameraPosition.run {
                Triple(target.longitude, target.latitude, zoom.toFloat())
            }
        }

        addOnCameraIdleListener {
            // FIXME This is just a simple feature query for debug
            model.center.value
                .run { LatLng(second, first) }
                .run(mapboxMap.projection::toScreenLocation)
                .run { RectF(x - 20, y + 20, x + 20, y - 20) }
                .let { queryRenderedFeatures(it) }
                .mapNotNull { it.id() + it.properties()?.toString() }
                .let { if (it.isEmpty()) null else it }
                ?.joinToString("\n\n")
                .let(model.details::setValue)
        }

        model.target.observe(viewLifecycleOwner) { camera ->
            animateCamera {
                CameraPosition.Builder()
                    .target(LatLng(camera.second, camera.first))
                    .zoom(camera.zoom.toDouble())
                    .build()
            }
        }

        Unit
    }

    // FIXME handle permission properly
    @SuppressLint("MissingPermission")
    fun MapboxMap.enableLocationComponent(style: Style) {
        val locationComponentOptions = LocationComponentOptions.builder(requireContext())
            .accuracyAlpha(0.5F)
            .build()
        val locationComponentActivationOptions = LocationComponentActivationOptions
            .builder(requireContext(), style)
            .locationComponentOptions(locationComponentOptions)
            .build()
        with(locationComponent) {
            activateLocationComponent(locationComponentActivationOptions)
            cameraMode = CameraMode.TRACKING
            isLocationComponentEnabled = true
        }
    }
}
