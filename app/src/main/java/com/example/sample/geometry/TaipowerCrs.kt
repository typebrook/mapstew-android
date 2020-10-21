package com.example.sample.geometry

/**
 * Taipower Coordinate System is a local CRS based on TWD67_TM2
 * For more details, see http://www.sunriver.com.tw/grid_taipower.htm
 */

abstract class MaskedCRS(
    displayName: String,
    type: ParameterType = ParameterType.Code,
    parameter: String
) : CoordRefSys(displayName, type, parameter) {
    abstract fun mask(coord: XYPair): String
    abstract fun reverseMask(rawMask: String): XYPair

    companion object {
        object CannotHandleException : Exception()
    }
}

enum class Section(val xy: Pair<Int, Int>) {
    A(170000 to 2750000),
    B(250000 to 2750000),
    C(330000 to 2750000),
    D(170000 to 2700000),
    E(250000 to 2700000),
    F(330000 to 2700000),
    G(170000 to 2650000),
    H(250000 to 2650000),
    J(90000 to 2600000),
    K(170000 to 2600000),
    L(250000 to 2600000),
    M(90000 to 2550000),
    N(170000 to 2550000),
    O(250000 to 2550000),
    P(90000 to 2500000),
    Q(170000 to 2500000),
    R(250000 to 2500000),
    T(170000 to 2450000),
    U(250000 to 2450000),
    V(170000 to 2400000),
    W(250000 to 2400000)
}

enum class Square {
    A, B, C, D, E, F, G, H
}

object TaipowerCrs : MaskedCRS(
    displayName = "台灣電力座標",
    type = ParameterType.Proj4,
    parameter = "+proj=tmerc +lat_0=0 +lon_0=121 +k=0.9999 +x_0=250000 +y_0=0 +ellps=aust_SA  +towgs84=-750.739,-359.515,-180.510,0.00003863,0.00001721,0.00000197,0.99998180 +units=m +no_defs"
) {

    override fun mask(coord: XYPair): String {
        var (x, y) = coord.x.toInt() to coord.y.toInt()

        val section = Section.values().firstOrNull {
            val (left, bottom) = it.xy
            x in left until left + 80000 && y in bottom until bottom + 50000
        } ?: return "Out of Boundary"
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

    @Throws(Companion.CannotHandleException::class)
    override fun reverseMask(rawMask: String): XYPair = try {
        val mask = rawMask
            .filter { it.isLetterOrDigit() }
            .substring(0, 10)
            .map { if (it.isLetter()) it.toUpperCase() else it }
            .joinToString("")
        val sectionXY = Section.values().firstOrNull { it.name == mask[0].toString() }?.xy
            ?: throw Companion.CannotHandleException
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
        throw Companion.CannotHandleException
    }
}