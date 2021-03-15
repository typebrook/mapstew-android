package io.typebrook.mapstew.map

import android.content.Context
import io.typebrook.mapstew.R

data class TiledFeature(
    val osmId: String,
    val name: String? = null
) {
    companion object {
        fun TiledFeature.displayName(context: Context) =
            name ?: context.getString(R.string.placeholder_path)
    }
}
