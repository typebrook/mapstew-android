package io.typebrook.mapstew.geometry

/**
 * This is just a experimental custom Coordinate System based on TaipowerCRS
 * It just map squares in each zoom level to another human readable text, for example:
 * T 76 80    DC  46
 * 方屯山 60區 DC格 46號
 *
 * The prefixing mountain peak is the tallest one in area 8000mx5000m
 */

internal class RescueCRS : TaipowerCRS(displayName = "搜索分區") {

    val peaks = mutableMapOf<String, String>()

    override fun mask(coord: XYPair, zoom: Int?): String? {
        val origin = super.mask(coord, null) ?: return null

        val code = origin.substring(0..4)
                .replaceRange(2..2, "X")
                .replaceRange(4..4, "X")
        val peak = peaks[code] ?: return origin
        val section = "${origin.subSequence(2..2)}${origin.substring(4..4)}區"
        val square = "${origin.subSequence(6..7)}格"
        val vision = "${origin.subSequence(8..9)}號"

        val mask = "$peak-$section-$square-$vision"

        return when (zoom) {
            null -> mask
            in 0..5 -> null
            in 6..9 -> origin.substring(0..0)
            in 10..11 -> peak
            in 12..14 -> "$peak-$section"
            in 15..17 -> square
            in 17..19 -> vision
            else -> mask
        }
    }

    // FIXME Use hint to guide user types proper value
    override fun reverseMask(rawMask: String): XYPair {
        return super.reverseMask(rawMask)
    }
}