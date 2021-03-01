package io.typebrook.mapstew.geometry

/**
 * Taipower Coordinate System is a local CRS based on TWD67_TM2
 * For more details, see http://www.sunriver.com.tw/grid_taipower.htm
 */

internal open class TaipowerCRS(displayName: String = "台灣電力座標") : CoordMask, CRSWrapper(
        displayName = displayName,
        type = ParameterType.Proj4,
        parameter = TWD67.parameter
) {

    // A Section is 80,000m x 50,000m
    // xy is the south-west point of each section
    enum class Section(val xy: Pair<Int, Int>) {
        A(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 7 * SECTION_HEIGHT), // 170000,2750000
        B(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 7 * SECTION_HEIGHT), // 250000,2750000
        C(LEFT_BOUNDARY + 3 * SECTION_WIDTH to BOTTOM_BOUNDARY + 7 * SECTION_HEIGHT), // 330000,2750000
        D(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 6 * SECTION_HEIGHT), // 170000,2700000
        E(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 6 * SECTION_HEIGHT), // 250000,2700000
        F(LEFT_BOUNDARY + 3 * SECTION_WIDTH to BOTTOM_BOUNDARY + 6 * SECTION_HEIGHT), // 330000,2700000
        G(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 5 * SECTION_HEIGHT), // 170000,2650000
        H(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 5 * SECTION_HEIGHT), // 250000,2650000
        J(LEFT_BOUNDARY + 0 * SECTION_WIDTH to BOTTOM_BOUNDARY + 4 * SECTION_HEIGHT), //  90000,2600000
        K(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 4 * SECTION_HEIGHT), // 170000,2600000
        L(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 4 * SECTION_HEIGHT), // 250000,2600000
        M(LEFT_BOUNDARY + 0 * SECTION_WIDTH to BOTTOM_BOUNDARY + 3 * SECTION_HEIGHT), //  90000,2550000
        N(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 3 * SECTION_HEIGHT), // 170000,2550000
        O(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 3 * SECTION_HEIGHT), // 250000,2550000
        P(LEFT_BOUNDARY + 0 * SECTION_WIDTH to BOTTOM_BOUNDARY + 2 * SECTION_HEIGHT), //  90000,2500000
        Q(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 2 * SECTION_HEIGHT), // 170000,2500000
        R(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 2 * SECTION_HEIGHT), // 250000,2500000
        T(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 1 * SECTION_HEIGHT), // 170000,2450000
        U(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 1 * SECTION_HEIGHT), // 250000,2450000
        V(LEFT_BOUNDARY + 1 * SECTION_WIDTH to BOTTOM_BOUNDARY + 0 * SECTION_HEIGHT), // 170000,2400000
        W(LEFT_BOUNDARY + 2 * SECTION_WIDTH to BOTTOM_BOUNDARY + 0 * SECTION_HEIGHT)  // 250000,2400000
    }

    // A Square is 100m x 100m
    enum class Square {
        A, B, C, D, E, F, G, H
    }

    override fun mask(coord: XYPair, zoom: Int?): String? {
        var (x, y) = coord.x.toInt() to coord.y.toInt()

        val section = Section.values().firstOrNull {
            val (left, bottom) = it.xy
            x in left until left + 80000 && y in bottom until bottom + 50000
        } ?: return null
        x -= section.xy.first
        y -= section.xy.second

        val imageXY = x / 800 to y / 500
        x %= 800
        y %= 500

        val squareXY = x / 100 to y / 100
        x %= 100
        y %= 100

        val visionXY = x / 10 to y / 10

        return section.name +
                imageXY.first.toString().padStart(2, '0') +
                imageXY.second.toString().padStart(2, '0') +
                "-" +
                Section.values()[squareXY.first].name + Section.values()[squareXY.second].name +
                visionXY.first + visionXY.second
    }

    @Throws(CoordMask.Companion.CannotHandleException::class)
    override fun reverseMask(rawMask: String): XYPair = try {
        val mask = rawMask
                .filter { it.isLetterOrDigit() }
                .substring(0, 9)
                .map { if (it.isLetter()) it.toUpperCase() else it }
                .joinToString("")
        val sectionXY = Section.values().firstOrNull { it.name == mask[0].toString() }?.xy
                ?: throw CoordMask.Companion.CannotHandleException
        val imageXY = mask.substring(1, 3).toInt() * 800 to mask.substring(3, 5).toInt() * 500
        val squareXY = mask.substring(5, 7).run {
            val xIndex = Square.values().first { it.name == this[0].toString() }.ordinal
            val yIndex = Square.values().first { it.name == this[1].toString() }.ordinal
            xIndex * 100 to yIndex * 100
        }
        val visionXY =
                mask.substring(7, 8).toInt() * 10 + 5 to mask.substring(8, 9).toInt() * 10 + 5

        (sectionXY.first + imageXY.first + squareXY.first + visionXY.first).toDouble() to
                (sectionXY.second + imageXY.second + squareXY.second + visionXY.second).toDouble()
    } catch (e: Exception) {
        throw CoordMask.Companion.CannotHandleException
    }

    companion object {
        const val LEFT_BOUNDARY = 90000
        const val RIGHT_BOUNDARY = 410000
        const val SECTION_WIDTH = 80000
        const val BOTTOM_BOUNDARY = 2400000
        const val TOP_BOUNDARY = 2800000
        const val SECTION_HEIGHT = 50000
    }
}