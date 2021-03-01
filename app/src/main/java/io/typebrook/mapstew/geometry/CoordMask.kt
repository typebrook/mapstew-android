package io.typebrook.mapstew.geometry

interface CoordMask {
    fun mask(coord: XYPair, zoom: Int?): String?
    fun reverseMask(rawMask: String): XYPair

    companion object {
        object CannotHandleException : Exception()
    }
}
