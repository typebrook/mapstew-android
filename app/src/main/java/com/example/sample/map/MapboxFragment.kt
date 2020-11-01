package com.example.sample.map

import android.annotation.SuppressLint
import android.app.AlertDialog
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
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory

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

            addOnMapLongClickListener {
                style.showLayerSelectionDialog()
                true
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

    private fun Style.showLayerSelectionDialog() = with(AlertDialog.Builder(context)) {
        setTitle("Layers")

        val layers = layers.sortedBy { it.id }
        val idList = layers.map { it.id }.toTypedArray()
        val checkedList = layers.map {
            it.visibility.value == Property.VISIBLE
        }.toBooleanArray()
        setMultiChoiceItems(idList, checkedList) { _, which, isChecked ->
            val layerId = idList[which]
            getLayer(layerId)?.setProperties(
                PropertyFactory.visibility(
                    if (isChecked) Property.VISIBLE else Property.NONE
                )
            )
        }
        setPositiveButton("OK", null)
        create().show()
    }
}
