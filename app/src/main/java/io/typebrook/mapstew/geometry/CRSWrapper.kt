package io.typebrook.mapstew.geometry

import org.osgeo.proj4j.*

/**
 * Features which provide coordinate converters for different system
 */

typealias XYPair = Pair<Double, Double>

val XYPair.x get() = first
val XYPair.y get() = second

typealias XYString = Pair<String, String>
typealias CoordConverter = (XYPair) -> (XYPair)
typealias CoordPrinter = (XYPair) -> XYString

val isValidInWGS84: (XYPair) -> Boolean = { (lon, lat) ->
    lat > -90 && lat < 90 && lon > -180 && lon < 180
}

fun XYPair.convert(from: CRSWrapper, to: CRSWrapper) = from.converterFor(to)(this)
fun XYPair.isValid(crs: CRSWrapper) = isValidInWGS84(convert(crs, CRSWrapper.WGS84))

enum class CoordExpression { Degree, DegMin, DMS, SINGLE, XY }

val selectableExpressions = listOf(
    CoordExpression.Degree,
    CoordExpression.DegMin,
    CoordExpression.DMS
)

open class CRSWrapper(
    val displayName: String = "UnNamed",
    private val type: ParameterType = ParameterType.Code,
    val parameter: String
) {
    enum class ParameterType { Code, Proj4 }

    val crs: CoordinateReferenceSystem by lazy {
        @Throws(UnknownAuthorityCodeException::class)
        when (type) {
            ParameterType.Code -> CRSFactory().createFromName(parameter)
            ParameterType.Proj4 -> CRSFactory().createFromParameters(displayName, parameter)
        }
    }

    val isLongLat: Boolean
        get() = crs.parameterString.startsWith("+proj=longlat")

    val isMeter: Boolean
        get() = crs.parameterString.contains("+units=m")

    fun converterFor(crs2: CRSWrapper): CoordConverter {
        if (this == crs2) return { xyPair -> xyPair }

        val trans = CoordinateTransformFactory().createTransform(crs, crs2.crs)
        return { (x, y): XYPair ->
            val p1 = ProjCoordinate(x, y)
            val p2 = ProjCoordinate()
            trans.transform(p1, p2)

            p2.x to p2.y
        }
    }

    override fun equals(other: Any?): Boolean =
        (other is CRSWrapper && hashCode() == other.hashCode())

    override fun hashCode(): Int {
        var result = displayName.hashCode()
        result = 31 * result + type.hashCode()
        result = 31 * result + parameter.hashCode()
        return result
    }

    companion object {

        val WGS84 = CRSWrapper(
            displayName = "WGS84",
            type = ParameterType.Code,
            parameter = "EPSG:4326"
        )
        val TWD97 = CRSWrapper(
            displayName = "TWD97",
            type = ParameterType.Code,
            parameter = "EPSG:3826"
        )
        val TWD67 = CRSWrapper(
            displayName = "TWD67",
            type = ParameterType.Proj4,
            parameter = "+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=aust_SA  +towgs84=-750.739,-359.515,-180.510,0.00003863,0.00001721,0.00000197,0.99998180 +units=m +no_defs"
        )
        val EPSG_3857 = CRSWrapper(
            displayName = "EPSG:3857",
            type = ParameterType.Code,
            parameter = "EPSG:3857"
        )
        val TWD67_latLng = CRSWrapper(
            displayName = "TWD67(經緯度)",
            type = ParameterType.Proj4,
            parameter = "+proj=longlat +ellps=aust_SA  +towgs84=-750.739,-359.515,-180.510,0.00003863,0.00001721,0.00000197,0.99998180 +no_defs"
        )
    }
}