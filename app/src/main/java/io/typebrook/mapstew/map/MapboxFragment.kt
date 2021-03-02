package io.typebrook.mapstew.map

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonParser
import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.Mapbox
import com.mapbox.mapboxsdk.annotations.Marker
import com.mapbox.mapboxsdk.annotations.MarkerOptions
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
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
import io.typebrook.mapstew.db.Survey
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.geometry.CRSWrapper
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.main.MapViewModel.Companion.ID_SURVEY
import io.typebrook.mapstew.main.zoom
import io.typebrook.mapstew.network.GithubService
import io.typebrook.mapstew.offline.MBTilesServer
import io.typebrook.mapstew.offline.MBTilesSource
import io.typebrook.mapstew.offline.MBTilesSourceException
import io.typebrook.mapstew.preference.prefShowHint
import kotlinx.android.synthetic.main.fragment_simple_bottom_sheet.view.*
import kotlinx.android.synthetic.main.input_degree.*
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
    private var focusedMarker: Marker? = null

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

        with(uiSettings) {
            compassGravity = Gravity.LEFT
            setCompassMargins(24, 180, 0, 0)
            isAttributionEnabled = false
            isLogoEnabled = false
        }

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
        }

        addOnMapClickListener {
            with(model) {
                hideButtons.value = !hideButtons.value
                displayBottomSheet.value = false
            }
            true
        }

        // If user choose a point, query features nearby
        model.focusPoint.observe(this@MapboxFragment.viewLifecycleOwner) { point ->
            // Remove all makers anyway when focus changes
            focusedMarker?.remove()

            if (point == null) {
                model.selectableFeatures.value = emptyList()
                model.details.value = null
                return@observe
            }

            // Add a new marker on the point
            focusedMarker = addMarker(MarkerOptions().position(projection.fromScreenLocation(point)))

            // Query features with OSM ID nearby feature
            val bbox = RectF(point.x - 30, point.y + 30, point.x + 30, point.y - 30)
            selectedFeatures = queryRenderedFeatures(bbox)

            // Update ViewModel with selectable unique OSM features
            model.selectableFeatures.value = selectedFeatures.filter {
                it.getStringProperty("class") == "path"
            }.mapNotNull {
                val osmId = it.getStringProperty("id") ?: return@mapNotNull null
                TiledFeature(osmId = osmId, name = it.getStringProperty("name"))
            }.fold(emptyList()) { acc, feature ->
                // Remove features with same OSM ID
                if (feature.osmId in acc.map { it.osmId })
                    acc else
                    acc + feature
            }

            // Update ViewModel with feature details if needed
            model.details.value = if (requireContext().prefShowHint())
                selectedFeatures.map { it.id() + " " + it.properties()?.toString() }
                        .let { if (it.isEmpty()) null else it }
                        ?.joinToString("\n\n") else
                null
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
            val features = when {
                id == null -> emptyList()
                id.startsWith(ID_SURVEY) -> model.focusPoint.value
                    ?.let { it: PointF -> projection.fromScreenLocation(it) }
                    ?.let { it: LatLng -> Point.fromLngLat(it.longitude, it.latitude) }
                    ?.let { it: Point -> listOf(Feature.fromGeometry(it)) }
                    ?: emptyList()
                else -> selectedFeatures.filter { it.getStringProperty("id") == id }
            }
            val featureCollection = FeatureCollection.fromFeatures(features)
            selectedFeatureSource.setGeoJson(featureCollection)

            val positions = features
                    .mapNotNull { it.geometry() }
                    .mapNotNull { getCameraForGeometry(it) }
                    .takeIf { it.isNotEmpty() }
                    ?: return@observe
            val averageLat = positions.fold(0.0) { acc, pos -> acc + pos.target.latitude } / positions.size
            val averageLng = positions.fold(0.0) { acc, pos -> acc + pos.target.longitude } / positions.size
            val minZoom = positions.reduce { acc, pos -> if (acc.zoom < pos.zoom) acc else pos }.zoom
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                    LatLng(averageLat, averageLng),
                    if (minZoom < 18) minZoom - 1 else 18.0
            )
            animateCamera(cameraUpdate, 600)
        }

        db.surveyDao().getAll().observe(viewLifecycleOwner) { surveys: List<Survey> ->
            markers.forEach { it.remove() }
            surveys.forEach { survey ->
                val marker = MarkerOptions()
                    .position(LatLng(survey.lat, survey.lon))
                    .title(survey.id)
                    .snippet(survey.content)
                addMarker(marker)
            }
        }

        setOnMarkerClickListener { marker ->
            selectMarker(marker)
            animateCamera {
                CameraPosition.Builder()
                    .target(marker.position)
                    .build()
            }
            model.focusedFeatureId.value = marker.title
            model.details.value = marker.title
            model.displayBottomSheet.value = true
            true
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
        val highlightedCasing = LineLayer("foo", selectedFeatureSource.id).withProperties(
                PropertyFactory.lineWidth(13f),
                PropertyFactory.lineColor("red")
        )
        val highlightedLine = LineLayer("bar", selectedFeatureSource.id).withProperties(
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineColor("yellow")
        )
        style.addLayer(highlightedCasing)
        style.addLayer(highlightedLine)
    }

    // FIXME handle permission properly
    @SuppressLint("MissingPermission")
    private fun MapboxMap.enableLocationComponent(style: Style) {
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