package io.typebrook.mapstew.offline

import android.content.Context

const val EXTENSION_MBTILES = ".mbtiles"

fun Context.getLocalMBTiles() = databaseList().filter { it.endsWith(EXTENSION_MBTILES) }