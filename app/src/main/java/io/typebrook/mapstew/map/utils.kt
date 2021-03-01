package io.typebrook.mapstew.map

import com.mapbox.geojson.Feature
import com.mapbox.geojson.FeatureCollection
import com.mapbox.geojson.LineString
import com.mapbox.geojson.Point
import com.mapbox.mapboxsdk.geometry.LatLngBounds
import com.mapbox.mapboxsdk.style.expressions.Expression.get
import com.mapbox.mapboxsdk.style.layers.LineLayer
import com.mapbox.mapboxsdk.style.layers.Property
import com.mapbox.mapboxsdk.style.layers.PropertyFactory.*
import com.mapbox.mapboxsdk.style.layers.SymbolLayer
import com.mapbox.mapboxsdk.style.sources.CustomGeometrySource
import com.mapbox.mapboxsdk.style.sources.GeometryTileProvider
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.geometry.TaipowerCRS.Companion.BOTTOM_BOUNDARY
import io.typebrook.mapstew.geometry.TaipowerCRS.Companion.LEFT_BOUNDARY
import io.typebrook.mapstew.geometry.TaipowerCRS.Companion.RIGHT_BOUNDARY
import io.typebrook.mapstew.geometry.TaipowerCRS.Companion.SECTION_HEIGHT
import io.typebrook.mapstew.geometry.TaipowerCRS.Companion.SECTION_WIDTH
import io.typebrook.mapstew.geometry.TaipowerCRS.Companion.TOP_BOUNDARY
import kotlin.math.ceil
import kotlin.math.floor

// region grid

const val ID_GRID_SOURCE = "grid"
const val ID_GRID_LINE_LAYER = "grid-line"
const val ID_GRID_SYMBOL_LAYER = "grid-symbol"

fun gridSource(crsWrapper: CRSWrapper) =
    CustomGeometrySource(ID_GRID_SOURCE, gridLineProvider(crsWrapper))

val GridLineLayer
    get() = LineLayer(ID_GRID_LINE_LAYER, ID_GRID_SOURCE)
        .withProperties(lineWidth(0.6F))

fun gridSymbolLayer(crsWrapper: CRSWrapper) =
    SymbolLayer(ID_GRID_SYMBOL_LAYER, ID_GRID_SOURCE).withProperties(
        textField(get("name")),
        textFont(arrayOf("Noto Sans Regular")),
        textSize(10F),
        textHaloColor("white"),
        textHaloWidth(2F),
        symbolPlacement(
            if (crsWrapper is TaipowerCRS)
                Property.SYMBOL_PLACEMENT_POINT else
                Property.SYMBOL_PLACEMENT_LINE
        ),
        symbolSpacing(150F)
    )

// Generate grid lines by current coordinate reference system
fun gridLineProvider(crsWrapper: CRSWrapper) = object : GeometryTileProvider {
    override fun getFeaturesForBounds(bounds: LatLngBounds?, zoom: Int): FeatureCollection {
        bounds ?: return FeatureCollection.fromFeatures(emptyList())

        val features: MutableList<Feature> = ArrayList()
        val yValues: MutableList<Double> = ArrayList()
        val xValues: MutableList<Double> = ArrayList()

        val spacingAdapter = when {
            crsWrapper.isLongLat -> LatLngSpacingAdapter()
            crsWrapper is TaipowerCRS -> TaipowerSpacingAdapter
            else -> MeterSpacingAdapter()
        }
        if (!spacingAdapter.isValid(zoom)) return FeatureCollection.fromFeatures(emptyList())

        val startXY = (bounds.lonWest to bounds.latSouth).convert(CRSWrapper.WGS84, crsWrapper)
        val endXY = (bounds.lonEast to bounds.latNorth).convert(CRSWrapper.WGS84, crsWrapper)

        var y = spacingAdapter.firstY(startXY.y, zoom)
        while (y != null && y < endXY.second) {
            yValues.add(y)
            Feature.fromGeometry(
                LineString.fromLngLats(
                    arrayListOf(
                        (startXY.x to y).convert(crsWrapper, CRSWrapper.WGS84)
                            .let { Point.fromLngLat(it.x, it.y) },
                        (endXY.x to y).convert(crsWrapper, CRSWrapper.WGS84)
                            .let { Point.fromLngLat(it.x, it.y) },
                    )
                )
            ).apply {
                val text = when {
                    crsWrapper.isLongLat -> xy2DMSString(0.0 to y!!).second
                        .split(' ')
                        .filterNot { it.startsWith("00") }
                        .last()
                    crsWrapper is TaipowerCRS -> return@apply
                    crsWrapper.isMeter -> xy2IntString(0.0 to y!!).second
                    else -> return@apply
                }
                addStringProperty("name", text)
            }.let(features::add)
            y = spacingAdapter.nextY(y, zoom)
        }

        var x = spacingAdapter.firstX(startXY.x, zoom)
        while (x != null && x < endXY.x) {
            xValues.add(x)
            Feature.fromGeometry(
                LineString.fromLngLats(
                    arrayListOf(
                        (x to startXY.y).convert(crsWrapper, CRSWrapper.WGS84)
                            .let { Point.fromLngLat(it.x, it.y) },
                        (x to endXY.y).convert(crsWrapper, CRSWrapper.WGS84)
                            .let { Point.fromLngLat(it.x, it.y) },
                    )
                )
            ).apply {
                val text = when {
                    crsWrapper.isLongLat -> xy2DMSString(x!! to 0.0).first
                        .split(' ')
                        .filterNot { it.startsWith("00") }
                        .last()
                    crsWrapper is TaipowerCRS -> ""
                    else -> xy2IntString(x!! to 0.0).first
                }
                addStringProperty("name", text)
            }.let(features::add)
            x = spacingAdapter.nextX(x, zoom)
        }

        if (crsWrapper is TaipowerCRS && spacingAdapter is TaipowerSpacingAdapter) {
            yValues.forEach { crossY ->
                xValues.forEach eachX@{ crossX ->
                    val spacingY = spacingAdapter.ySpacing(zoom) ?: return@eachX
                    val spacingX = spacingAdapter.xSpacing(zoom) ?: return@eachX
                    val xy = (crossX + spacingX / 2) to (crossY + spacingY / 2)
                    val lonLat = xy.convert(crsWrapper, CRSWrapper.WGS84)

                    Feature.fromGeometry(
                        Point.fromLngLat(lonLat.x, lonLat.y)
                    ).apply {
                        val text = crsWrapper.mask(xy)?.run {
                            when (zoom) {
                                in 0..5 -> null
                                in 6..9 -> substring(0, 1)
                                in 10..11 -> substring(0, 5).toCharArray().let {
                                    it[2] = 'X'
                                    it[4] = 'X'
                                    String(it)
                                }
                                in 12..14 -> substring(0, 5)
                                in 15..17 -> substring(0, 8)
                                else -> this
                            }
                        } ?: return@eachX

                        addStringProperty("name", text)
                    }.let(features::add)
                }
            }
        }

        return FeatureCollection.fromFeatures(features)
    }
}

// Adapt spacing for each grid line
sealed class SpacingAdapter {
    abstract fun isValid(zoom: Int): Boolean
    abstract fun firstX(x: Double, zoom: Int): Double?
    abstract fun nextX(x: Double, zoom: Int): Double?
    abstract fun firstY(y: Double, zoom: Int): Double?
    abstract fun nextY(y: Double, zoom: Int): Double?
}

class LatLngSpacingAdapter : SpacingAdapter() {
    private val smallestSpacing: Double = 0.0002777778
    private val gridSpacing = mapOf(
        0 to 90.0,          // 90°
        1 to 40.0,          // 40°
        2 to 20.0,          // 20°
        3 to 10.0,          // 10°
        4 to 5.0,           // 5°
        5 to 2.0,           // 2°
        6 to 1.0,           // 1°
        7 to 0.5,           // 0.5°
        8 to 0.3333333,     // 20'
        9 to 0.1666667,     // 10'
        10 to 0.08333333,   // 5'
        11 to 0.03333333,   // 2'
        12 to 0.01666667,   // 1'
        13 to 0.008333333,  // 0.5'
        14 to 0.005555556,  // 20''
        15 to 0.002777778,  // 10''
        16 to 0.001388889,  // 5''
        17 to 0.0005555556, // 2''
        18 to smallestSpacing  // 1''
    )

    override fun isValid(zoom: Int): Boolean = true

    override fun firstX(x: Double, zoom: Int): Double {
        val spacing = gridSpacing[zoom] ?: smallestSpacing
        return floor(x / spacing) * spacing
    }

    override fun nextX(x: Double, zoom: Int): Double = x + (gridSpacing[zoom] ?: smallestSpacing)

    override fun firstY(y: Double, zoom: Int): Double {
        val spacing = gridSpacing[zoom] ?: smallestSpacing
        return floor(y / spacing) * spacing
    }

    override fun nextY(y: Double, zoom: Int): Double = if (zoom == 0)
        y + 45 else
        y + (gridSpacing[zoom] ?: smallestSpacing)
}

class MeterSpacingAdapter : SpacingAdapter() {
    private val smallestSpacing: Int = 20
    private val gridSpacing = mapOf(
        7 to 100000,
        8 to 50000,
        9 to 20000,
        10 to 10000,
        11 to 5000,
        12 to 2000,
        13 to 1000,
        14 to 500,
        15 to 200,
        16 to 100,
        17 to 50,
        18 to smallestSpacing
    )

    override fun isValid(zoom: Int): Boolean = zoom >= 7

    override fun firstX(x: Double, zoom: Int): Double {
        val spacing = gridSpacing[zoom] ?: smallestSpacing
        return floor(x / spacing) * spacing
    }

    override fun nextX(x: Double, zoom: Int): Double = x + (gridSpacing[zoom] ?: smallestSpacing)

    override fun firstY(y: Double, zoom: Int): Double {
        val spacing = gridSpacing[zoom] ?: smallestSpacing
        return floor(y / spacing) * spacing
    }

    override fun nextY(y: Double, zoom: Int): Double = if (zoom == 0)
        y + 45 else
        y + (gridSpacing[zoom] ?: smallestSpacing)
}

object TaipowerSpacingAdapter : SpacingAdapter() {

    override fun isValid(zoom: Int): Boolean = zoom >= 6

    fun xSpacing(zoom: Int): Int? = when (zoom) {
        in 0..5 -> null
        in 6..9 -> SECTION_WIDTH
        in 10..11 -> 8000
        in 12..14 -> 800
        in 15..17 -> 100
        else -> 10
    }

    fun ySpacing(zoom: Int): Int? = when (zoom) {
        in 0..5 -> null
        in 6..9 -> SECTION_HEIGHT
        in 10..11 -> 5000
        in 12..14 -> 500
        in 15..17 -> 100
        else -> 10
    }

    override fun firstX(x: Double, zoom: Int): Double? = if (x < LEFT_BOUNDARY)
        LEFT_BOUNDARY.toDouble() else
        xSpacing(zoom)?.let { spacing ->
            ceil((x - LEFT_BOUNDARY) / spacing) * spacing + LEFT_BOUNDARY
        }.takeIf {
            x <= RIGHT_BOUNDARY
        }

    override fun nextX(x: Double, zoom: Int): Double? = xSpacing(zoom)?.let { spacing ->
        x + spacing
    }?.takeIf {
        it <= RIGHT_BOUNDARY
    }

    override fun firstY(y: Double, zoom: Int): Double? = if (y < BOTTOM_BOUNDARY)
        BOTTOM_BOUNDARY.toDouble() else
        ySpacing(zoom)?.let { spacing ->
            ceil((y - BOTTOM_BOUNDARY) / spacing) * spacing + BOTTOM_BOUNDARY
        }.takeIf {
            y <= TOP_BOUNDARY
        }

    override fun nextY(y: Double, zoom: Int): Double? = ySpacing(zoom)?.let { spacing ->
        y + spacing
    }?.takeIf {
        it <= TOP_BOUNDARY
    }
}

// endregion