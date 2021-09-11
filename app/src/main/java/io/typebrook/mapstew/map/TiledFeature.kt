package io.typebrook.mapstew.map

import android.content.Context
import io.typebrook.mapstew.R
import io.typebrook.mapstew.geometry.XYPair

data class TiledFeature(
    val relatedLngLat: XYPair? = null,
    val osmId: String? = null,
    val name: String? = null,
) {
    companion object {
        fun TiledFeature.displayName(context: Context) =
            name ?: context.getString(R.string.placeholder_path)
    }
}
