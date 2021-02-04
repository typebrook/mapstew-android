package io.typebrook.mapstew.map

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.RectF
import android.view.Gravity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.LocationComponentOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.maps.SupportMapFragment
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import io.typebrook.mapstew.R
import io.typebrook.mapstew.geometry.CRSWrapper
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.main.zoom
import io.typebrook.mapstew.network.GithubService
import io.typebrook.mapstew.offline.MBTilesServer
import io.typebrook.mapstew.offline.MBTilesSource
import io.typebrook.mapstew.offline.MBTilesSourceException
import io.typebrook.mapstew.preference.prefShowHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader

class MapboxFragment : SupportMapFragment() {

    private val model by activityViewModels<MapViewModel>()
    private val styleFile by lazy {
        File(requireContext().filesDir.absolutePath + File.separator + "rudymap.json")
    }
    private val styleBuilder by lazy {
        val uri = if (styleFile.exists())
            "file://${styleFile.path}" else
            getString(R.string.uri_style_rudymap)
        SafeMutableLiveData(Style.Builder().fromUri(uri))
    }

    private val selectedFeatureSource by lazy { GeoJsonSource("foo") }
    private var selectedFeatures: List<Feature> = emptyList()

    override fun onAttach(context: Context) {
        super.onAttach(context)

        Mapbox.getInstance(requireContext(), null)
        Mapbox.setConnected(true)

        // fetch latest style file
        lifecycleScope.launchWhenCreated {
            // TODO A proper way to check if upstream style file is updated
            if (styleFile.exists()) return@launchWhenCreated

            val response = withContext(Dispatchers.IO) {
                GithubService.mapstewService().getMapboxStyle()
            }
            if (!response.isSuccessful) return@launchWhenCreated

            withContext(Dispatchers.IO) {
                response.body()?.byteStream()?.use { input ->
                    FileOutputStream(styleFile).use { output ->
                        input.copyTo(output)
                    }
                }
            }
            styleBuilder.value = Style.Builder().fromUri("file://${styleFile.path}")
        }

        model.mbTilesList.observe(this) { list ->
            list.forEach { mbtiles ->
                // Create MBTilesSource
                val file = context.getDatabasePath(mbtiles)
                val sourceId = mbtiles.substringBefore(".mbtiles")
                try {
                    MBTilesSource(file, sourceId).apply { activate() }
                } catch (e: MBTilesSourceException.CouldNotReadFileException) {
                    // TODO Deal with error if fail to read MBTiles
                }
            }

            try {
                with(JsonParser.parseReader(FileReader(styleFile)).asJsonObject) {

                    // Override sources with local MBTiles
                    getAsJsonObject("sources")?.run {
                        MBTilesServer.sources.forEach {
                            val source = it.value
                            with(getAsJsonObject(source.id)) {
                                add("tiles", JsonArray().apply { add(source.url) })
                                remove("url")
                            }
                        }
                    }
                    // Override glyphs and sprite with asset
                    addProperty("glyphs", "asset://fonts/KlokanTech%20{fontstack}/{range}.pbf")

                    styleBuilder.value = Style.Builder().fromJson(toString())
                }
            } catch (e: Exception) {
                // Exception may happens when FileNotFound, JsonParseFail, IllegalState
                Timber.d("Fail to update style with local MBTiles: ${e.localizedMessage ?: "No message"}")
            }
        }
    }

    @SuppressLint("RtlHardcoded")
    override fun onMapReady(mapboxMap: MapboxMap) = with(mapboxMap) {

        uiSettings.compassGravity = Gravity.LEFT
        uiSettings.setCompassMargins(24, 180, 0, 0)

        styleBuilder.observe(this@MapboxFragment) { builder ->
            setStyle(builder) { style -> onStyleLoaded(this, style) }
        }

        cameraPosition = CameraPosition.Builder()
                .zoom(model.center.value.zoom.toDouble())
                .build()

        addOnCameraMoveListener {
            model.center.value = cameraPosition.run {
                Triple(target.longitude, target.latitude, zoom.toFloat())
            }
            model.focusedFeatureId.value = null
        }

        addOnMapClickListener {
            model.focusPoint.value = null
            model.displayBottomSheet.value = false
            true
        }

        model.focusPoint.observe(this@MapboxFragment.viewLifecycleOwner) { point ->
            markers.forEach(::removeMarker)
            if (point == null) return@observe

            addMarker(MarkerOptions().position(projection.fromScreenLocation(point)))

            val bbox = RectF(point.x - 20, point.y + 20, point.x + 20, point.y - 20)
            selectedFeatures = queryRenderedFeatures(bbox)

            model.displayBottomSheet.value = false
            model.selectedFeatures.value = selectedFeatures.mapNotNull {
                val osmId = it.id() ?: return@mapNotNull null
                TiledFeature(osmId = osmId, name = it.getStringProperty("name"))
            }

            if (requireContext().prefShowHint()) {
                val detailText = selectedFeatures
                        .map { it.id() + it.properties()?.toString() }
                        .let { if (it.isEmpty()) null else it }
                        ?.joinToString("\n\n")
                model.details.value = detailText
            } else {
                model.details.value = null
            }
        }

        model.target.observe(viewLifecycleOwner) { camera ->
            animateCamera {
                CameraPosition.Builder()
                        .target(LatLng(camera.second, camera.first))
                        .zoom(camera.zoom.toDouble())
                        .build()
            }
        }

        model.focusedFeatureId.observe(viewLifecycleOwner) { id ->
            id ?: return@observe
            val features = selectedFeatures.filter { it.id() == id }
            selectedFeatureSource.setGeoJson(FeatureCollection.fromFeatures(features))
        }
    }

    private fun onStyleLoaded(mapboxMap: MapboxMap, style: Style) {
        model.locateUser.observe(this@MapboxFragment) locate@{ enable ->
            if (enable) mapboxMap.enableLocationComponent(style)
        }

        model.displayLayers.observe(viewLifecycleOwner) { display ->
            if (display) {
                style.showLayerSelectionDialog()
                model.displayLayers.value = false
            }
        }

        mapboxMap.addOnMapLongClickListener { latLng ->
            model.focusPoint.value = mapboxMap.projection.toScreenLocation(latLng)
            true
        }

        MediatorLiveData<Pair<Boolean, CRSWrapper>>().apply {
            addSource(model.displayGrid) {
                value = model.displayGrid.value to model.crsState.value.crsWrapper
            }
            addSource(model.crsState) {
                value = model.displayGrid.value to model.crsState.value.crsWrapper
            }
        }.observe(viewLifecycleOwner) { (display, crsWrapper) ->
            with(style) {
                removeLayer(ID_GRID_LINE_LAYER)
                removeLayer(ID_GRID_SYMBOL_LAYER)
                removeSource(ID_GRID_SOURCE)

                if (!display) return@observe

                addLayer(GridLineLayer)
                addLayer(gridSymbolLayer(crsWrapper))
                addSource(gridSource(crsWrapper))
            }
        }

        style.addSource(selectedFeatureSource)
        val highlightedFeature = LineLayer("bar", selectedFeatureSource.id)
        style.addLayer(highlightedFeature)
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

    // TODO show sub-layers under each item
    private fun Style.showLayerSelectionDialog() = with(AlertDialog.Builder(context)) {

        val layersFromStyle = layers.filterNot {
            it.id == ID_GRID_LINE_LAYER || it.id == ID_GRID_SYMBOL_LAYER
        }

        setTitle("Layers")
        setPositiveButton("OK", null)
        setNeutralButton("Toggle") { _, _ ->
            layersFromStyle.forEach { layer ->
                val visibility =
                        if (layer.visibility.value == Property.VISIBLE) Property.NONE else Property.VISIBLE
                layer.setProperties(
                        PropertyFactory.visibility(visibility)
                )
            }
        }

        // List of id prefix, like 'road', 'water'
        val layerGroupList = layersFromStyle.sortedBy { it.id }
                .map { it.id.substringBefore('_') }
                .distinct()
                .toTypedArray()
        val checkedList = layerGroupList.map { idPrefix ->
            layersFromStyle.first { it.id.startsWith(idPrefix) }?.visibility?.value == Property.VISIBLE
        }.toBooleanArray()

        // Here we only let user select groups of layers (each item in a group has same id prefix)
        setMultiChoiceItems(layerGroupList, checkedList) { _, which, isChecked ->
            val prefix = layerGroupList[which]

            // Enable/Disable a layer group
            layersFromStyle.filter { it.id.startsWith(prefix) }.forEach { layer ->
                layer.setProperties(
                        PropertyFactory.visibility(
                                if (isChecked) Property.VISIBLE else Property.NONE
                        )
                )
            }
        }
        create().show()
    }
}