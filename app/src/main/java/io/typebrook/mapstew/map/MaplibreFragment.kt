package io.typebrook.mapstew.map

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.PointF
import android.graphics.RectF
import android.view.Gravity
import android.widget.Toast
import androidx.core.content.res.ResourcesCompat
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.MediatorLiveData
import androidx.lifecycle.lifecycleScope
import com.google.gson.JsonArray
import com.google.gson.JsonObject
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
import com.mapbox.mapboxsdk.maps.*
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory
import com.mapbox.mapboxsdk.style.sources.GeoJsonSource
import com.mapbox.pluginscalebar.ScaleBarOptions
import com.mapbox.pluginscalebar.ScaleBarPlugin
import io.typebrook.mapstew.R
import io.typebrook.mapstew.db.Survey
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.geometry.CRSWrapper
import io.typebrook.mapstew.geometry.XYPair
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.main.MapViewModel.Companion.ID_RAW_SURVEY
import io.typebrook.mapstew.main.zoom
import io.typebrook.mapstew.network.GithubService
import io.typebrook.mapstew.preference.prefShowHint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader

class MaplibreFragment : SupportMapFragment(), MapViewModelHolder {

    private val mapView get() = view as MapView
    override val model by activityViewModels<MapViewModel>()
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
            try {
                with(JsonParser.parseReader(FileReader(styleFile)).asJsonObject) {

                    // Override sources with local MBTiles
                    getAsJsonObject("sources")?.run {
                        list.forEach {
                            val sourceId = it.substringBefore(".")
                            with(getAsJsonObject(sourceId)) {
                                add("tiles", JsonArray().apply {
                                    add("mbtiles://${context.getDatabasePath("$sourceId.mbtiles")}")
                                })
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
            compassGravity = Gravity.RIGHT
            setCompassMargins(0, 530, 20, 0)
            isAttributionEnabled = false
            isLogoEnabled = false
            addScaleBar()
        }

        styleBuilder.observe(this@MaplibreFragment) { builder ->
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

        model.target.observe(viewLifecycleOwner) { camera ->
            animateCamera {
                CameraPosition.Builder()
                        .target(LatLng(camera.second, camera.first))
                        .zoom(camera.zoom.toDouble())
                        .build()
            }
        }

        db.surveyDao().getNewlyCreated().observe(viewLifecycleOwner) { survey ->
            val features = when(val osmId = survey?.relatedFeatureId) {
                null -> emptyList()
                else -> selectedFeatures.filter { it.getStringProperty("id") == osmId }
            }
            val featureCollection = FeatureCollection.fromFeatures(features)
            selectedFeatureSource.setGeoJson(featureCollection)

            val positions = features
                    .mapNotNull { it.geometry() }
                    .mapNotNull { getCameraForGeometry(it) }
                    .takeIf { it.isNotEmpty() }
                    ?: return@observe
            val minZoom = positions.reduce { acc, pos -> if (acc.zoom < pos.zoom) acc else pos }.zoom
            val targetZoom = if (minZoom < 18) minZoom - 1 else 18.0
            val averageLat = positions.fold(0.0) { acc, pos -> acc + pos.target.latitude } / positions.size
            val averageLng = positions.fold(0.0) { acc, pos -> acc + pos.target.longitude } / positions.size
            val mapCenter = getCenterWhenFocusWithBottomSheet(
                LatLng(averageLat, averageLng),
                targetZoom - cameraPosition.zoom
            )
            val cameraUpdate = CameraUpdateFactory.newLatLngZoom(
                mapCenter, if (minZoom < 18) minZoom - 1 else 18.0
            )
            animateCamera(cameraUpdate, 600)
        }
    }

    private fun onStyleLoaded(mapboxMap: MapboxMap, style: Style) {
        style.addImagesByDrawables()

        model.locateUser.observe(this@MaplibreFragment) locate@{ enable ->
            if (enable) mapboxMap.enableLocationComponent(style)
        }

        model.displayLayers.observe(viewLifecycleOwner) { display ->
            if (display) {
                style.showLayerSelectionDialog()
                model.displayLayers.value = false
            }
        }

        mapboxMap.addOnMapLongClickListener { latLng ->
            mapboxMap.handleFocus(latLng)
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
        val highlightedCasing = LineLayer("highlight-outer", selectedFeatureSource.id).withProperties(
                PropertyFactory.lineWidth(13f),
                PropertyFactory.lineColor("red")
        )
        val highlightedLine = LineLayer("highlight-inner", selectedFeatureSource.id).withProperties(
                PropertyFactory.lineWidth(9f),
                PropertyFactory.lineColor("yellow")
        )
        style.addLayer(highlightedCasing)
        style.addLayer(highlightedLine)

        // Add symbols for surveys
        val symbolManager = SymbolManager(mapView, mapboxMap, style)
        symbolManager.addClickListener { symbol ->
            val currentZoom = mapboxMap.cameraPosition.zoom
            val targetZoom = if (currentZoom > 16.0) currentZoom else 16.0
            val targetPoint =
                mapboxMap.getCenterWhenFocusWithBottomSheet(symbol.latLng, targetZoom - currentZoom)
            mapboxMap.animateCamera(
                CameraUpdateFactory.newLatLngZoom(targetPoint, targetZoom),
                object : MapboxMap.CancelableCallback {
                    override fun onFinish() {
                        // FIXME
//                        model.focusedFeatureId.value =
//                            symbol.data?.asJsonObject?.get("key")?.asString
                        model.displayBottomSheet.value = true
                    }

                    override fun onCancel() {}
                }
            )
            true
        }
        db.surveyDao().getAll().observe(viewLifecycleOwner) { surveys: List<Survey> ->
            if (symbolManager.annotations.size() == surveys.size) return@observe

            symbolManager.annotations.clear()
            surveys.map { survey ->
                SymbolOptions()
                    .withLatLng(LatLng(survey.lat, survey.lon))
                    .withIconImage(
                        if (survey.osmNoteId == null) IMAGE_NAME_DEFAULT_MARKER else IMAGE_NAME_UPLOADED_NOTE
                    )
                    .withData(JsonObject().apply{
                        this.addProperty("key", survey.dateCreated.time.toString())
                    })
            }.let (symbolManager::create)
        }
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

    private fun Style.addImagesByDrawables() {
        val imageDefaultMarker =
            ResourcesCompat.getDrawable(resources, R.drawable.mapbox_marker_icon_default, null)
                ?: return
        addImage(IMAGE_NAME_DEFAULT_MARKER, imageDefaultMarker)

        val uploadedMarker = ResourcesCompat.getDrawable(resources, R.drawable.purple_marker, null)
            ?: return
        addImage(IMAGE_NAME_UPLOADED_NOTE, uploadedMarker)
    }

    private fun MapboxMap.addScaleBar() {
        val scaleBarPlugin = ScaleBarPlugin(mapView, this)
        val scaleBarOptions = ScaleBarOptions(requireContext())
            .setMarginTop(180f)
            .setMarginLeft(32f)
            .setTextBarMargin(15f)
            .setTextSize(30F)

        scaleBarPlugin.create(scaleBarOptions)
    }

    // If user choose a point, query features nearby
    private fun MapboxMap.handleFocus(latLng: LatLng) {
        val point = projection.toScreenLocation(latLng)
        // Remove all makers anyway when focus changes
        focusedMarker?.remove()

        // Add a new marker on the point
        val markerOptions = MarkerOptions().position(projection.fromScreenLocation(point))
        focusedMarker = addMarker(markerOptions)

        // Query features with OSM ID nearby feature
        val bbox = RectF(point.x - 30, point.y + 30, point.x + 30, point.y - 30)
        val ids = mutableListOf<String>()
        val selectableFeatures = queryRenderedFeatures(bbox).filter {
            it.getStringProperty("class") == "path"
        }.mapNotNull {
            val osmId = it.getStringProperty("id") ?: return@mapNotNull null
            if (osmId !in ids) ids.add(osmId) else return@mapNotNull null
            TiledFeature(
                osmId = osmId,
                name = it.getStringProperty("name"),
                relatedLngLat = latLng.longitude to latLng.latitude
            )
        }

        // Update ViewModel with feature details if needed
        model.details.value = if (requireContext().prefShowHint())
            selectedFeatures.map { it.id() + " " + it.properties()?.toString() }
                .let { if (it.isEmpty()) null else it }
                ?.joinToString("\n\n") else
            null

        showPopupWindow(
            latLng.longitude to latLng.latitude,
            point,
            selectableFeatures
        )
    }

    companion object {
        const val IMAGE_NAME_DEFAULT_MARKER = "default_marker"
        const val IMAGE_NAME_UPLOADED_NOTE = "uploaded_note"
    }
}