package io.typebrook.mapstew.offline

import android.content.Context

const val EXTENSION_MBTILES = ".mbtiles"

fun Context.getLocalMBTiles() = databaseList().filter { it.endsWith(EXTENSION_MBTILES) }

data class OfflineMap(
    val displayName: String,
    val path: String,
    val urlTemplate: String
) {
    val fileName get() = path.substringAfterLast('/')
    fun localFile(context: Context) = context.getDatabasePath(fileName)
}

val downloadableMaps = listOf(
    OfflineMap(
        displayName = "Contours",
        path = "https://github.com/typebrook/contours/releases/download/2020/contours.mbtiles",
        urlTemplate = "https://typebrook.github.io/contours/tiles/{z}/{x}/{y}.pbf"
    ),
    OfflineMap(
        displayName = "Mapstew",
        path = "https://github.com/typebrook/mapstew/releases/download/cache-2020.12.11/mapstew.mbtiles",
        urlTemplate = "https://typebrook.github.io/mapstew/tiles/{z}/{x}/{y}.pbf"
    ),
    OfflineMap(
        displayName = "Hillshade",
        path = "https://github.com/osmhacktw/terrain-rgb/releases/download/2020/terrain-rgb.mbtiles",
        urlTemplate = "https://osmhacktw.github.io/terrain-rgb/tiles/{z}/{x}/{y}.png"
    )
)
