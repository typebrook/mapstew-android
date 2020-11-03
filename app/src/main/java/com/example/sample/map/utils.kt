package com.example.sample.map

import com.example.sample.geometry.xy2DMSString
import com.mapbox.geojson.*
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.style.expressions.Expression.get
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.CustomGeometrySource
import com.mapbox.mapboxsdk.style.sources.GeometryTileProvider
import kotlin.math.ceil
import kotlin.math.floor

object LineProvider : GeometryTileProvider {
    override fun getFeaturesForBounds(bounds: LatLngBounds?, zoom: Int): FeatureCollection {
        bounds ?: return FeatureCollection.fromFeatures(emptyList())

        val features: MutableList<Feature> = ArrayList()
        val gridSpacing: Double = when {
            zoom >= 18 -> 0.0002777778 // 1''
            zoom >= 17 -> 0.0005555556 // 2''
            zoom >= 16 -> 0.001388889  // 5''
            zoom >= 15 -> 0.002777778  // 10''
            zoom >= 14 -> 0.005555556  // 20''
            zoom >= 13 -> 0.00833333   // 0.5'
            zoom >= 12 -> 0.01666666   // 1'
            zoom >= 11 -> 0.0333333    // 2'
            zoom >= 10 -> 0.0833333    // 5'
            zoom >= 9 -> 0.1666667     // 10'
            zoom >= 8 -> 0.3333333     // 20'
            zoom >= 7 -> 0.5           // 0.5°
            zoom >= 6 -> 1.0           // 1°
            zoom >= 5 -> 2.0           // 2°
            zoom >= 4 -> 5.0           // 5°
            zoom >= 3 -> 10.0          // 10°
            zoom >= 2 -> 20.0          // 20°
            else -> 40.0               // 40°
        }

        var y = ceil(bounds.latNorth / gridSpacing) * gridSpacing
        while (y >= floor(bounds.latSouth / gridSpacing) * gridSpacing) {
            Feature.fromGeometry(
                LineString.fromLngLats(
                    arrayListOf(
                        Point.fromLngLat(bounds.lonWest, y),
                        Point.fromLngLat(bounds.lonEast, y)
                    )
                )
            ).apply {
                val text = xy2DMSString(0.0 to y).second
                    .split(' ')
                    .filterNot { it.startsWith("00") }
                    .last()
                addStringProperty("name", text)
            }.let(features::add)
            y -= gridSpacing
        }

        val gridLines: MutableList<MutableList<Point>> = ArrayList()
        var x = floor(bounds.lonWest / gridSpacing) * gridSpacing
        while (x <= ceil(bounds.lonEast / gridSpacing) * gridSpacing) {
            Feature.fromGeometry(
                LineString.fromLngLats(
                    arrayListOf(
                        Point.fromLngLat(x, bounds.latSouth),
                        Point.fromLngLat(x, bounds.latNorth)
                    )
                )
            ).apply {
                val text = xy2DMSString(x to 0.0).first
                    .split(' ')
                    .filterNot { it.startsWith("00") }
                    .last()
                addStringProperty("name", text)
            }.let(features::add)
            x += gridSpacing
        }
        features.add(Feature.fromGeometry(MultiLineString.fromLngLats(gridLines)))

        return FeatureCollection.fromFeatures(features)
    }
}

object AngleGridSource : CustomGeometrySource("angle-grid", LineProvider)
object AngleGridLayer : LineLayer("angle-grid", AngleGridSource.id)
object AngleGridSymbolLayer :
    SymbolLayer("angle-grid-symbol", AngleGridSource.id) {
    init {
        withProperties(
            textField(get("name")),
            textFont(arrayOf("Noto Sans Regular")),
            textSize(10F),
            textHaloColor("white"),
            textHaloWidth(2F),
            symbolPlacement(Property.SYMBOL_PLACEMENT_LINE),
            symbolSpacing(150F)
        )
    }
}