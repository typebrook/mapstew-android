package io.typebrook.mapstew.geometry

internal class RescueCRS : TaipowerCRS(displayName = "搜索分區") {

    val peaks = mutableMapOf<String, String>()

    override fun mask(coord: XYPair, zoom: Int?): String? {
        val origin = super.mask(coord, null) ?: return null

        val code = origin.substring(0..4)
                .replaceRange(2..2, "X")
                .replaceRange(4..4, "X")
        val peak = peaks[code] ?: return origin
        return "$peak-" +
                "${origin.subSequence(2..2)}${origin.substring(4..4)}區-" +
                "${origin.subSequence(6..7)}格" +
                "${origin.subSequence(8..9)}號"
    }

    // FIXME Use hint to guide user types proper value
    override fun reverseMask(rawMask: String): XYPair {
        return super.reverseMask(rawMask)
    }
}