package io.typebrook.mapstew.geometry

abstract class MaskedCRS(
        displayName: String,
        type: ParameterType = ParameterType.Code,
        parameter: String
) : CRSWrapper(displayName, type, parameter) {
    abstract fun mask(coord: XYPair): String?
    abstract fun reverseMask(rawMask: String): XYPair

    companion object {
        object CannotHandleException : Exception()
    }
}
