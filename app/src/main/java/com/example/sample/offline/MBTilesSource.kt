package com.example.sample.offline

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import com.mapbox.mapboxsdk.style.sources.RasterSource
import com.mapbox.mapboxsdk.style.sources.Source
import com.mapbox.mapboxsdk.style.sources.TileSet
import com.mapbox.mapboxsdk.style.sources.VectorSource
import java.io.File
import java.io.FileOutputStream
import kotlin.properties.Delegates

/*
 *  Mapbox Source backend by localhost tile server
 */

sealed class MBTilesSourceException : Exception() {
    class CouldNotReadFileException : MBTilesSourceException()
    class UnsupportedFormatException : MBTilesSourceException()
}

class MBTilesSource(file: File, sourceId: String? = null) {

    val id = sourceId ?: file.path.substringAfterLast("/").substringBefore(".")
    val url get() = "http://localhost:${MBTilesServer.port}/$id/{z}/{x}/{y}.$format"
    private val db: SQLiteDatabase = try {
        if (!file.exists()) throw MBTilesSourceException.CouldNotReadFileException()
        SQLiteDatabase.openOrCreateDatabase(file, null)
    } catch (e: RuntimeException) {
        file.delete()
        throw MBTilesSourceException.CouldNotReadFileException()
    }
    val instance: Source by lazy {
        if (isVector) VectorSource(id, TileSet(null, url))
        else RasterSource(id, TileSet(null, url))
    }

    var isVector by Delegates.notNull<Boolean>()
    lateinit var format: String
//    var tileSize: Int? = null
//    var layersJson: String? = ""
//    var attributions: String? = ""
//    var minZoom: Float? = null
//    var maxZoom: Float? = null
//    var bounds: LatLngBounds? = null

    init {
        try {
            format = db.query(
                "metadata", null, "name = ?",
                arrayOf("format"), null, null, null
            ).use { cursor ->
                cursor.moveToFirst()
                val index = cursor.getColumnIndex("value")
                cursor.getString(index)
            }

            isVector = when (format) {
                in validVectorFormats -> true
                in validRasterFormats -> false
                else -> throw MBTilesSourceException.UnsupportedFormatException()
            }
        } catch (e: Exception) {
            file.delete()
            print(e.localizedMessage)
            throw MBTilesSourceException.CouldNotReadFileException()
        }
    }

    fun getTile(z: Int, x: Int, y: Int): ByteArray? {
        return db.query(
            "tiles", null, "zoom_level = ? AND tile_column = ? AND tile_row = ?",
            arrayOf("$z", "$x", "$y"), null, null, null
        ).use { cursor ->
            if (cursor.count == 0) return null

            cursor.moveToFirst()
            val index = cursor.getColumnIndex("tile_data")
            cursor.getBlob(index)
        }
    }

    fun activate() = with(MBTilesServer) {
        sources[id] = this@MBTilesSource
        if (!isRunning) start()
    }

    fun deactivate() = with(MBTilesServer) {
        sources.remove(id)
        if (isRunning && sources.isEmpty()) stop()
    }

    companion object {
        val validRasterFormats = listOf("jpg", "png")
        val validVectorFormats = listOf("pbf", "mvt")

        fun readAsset(context: Context, asset: String): String =
            context.assets.open(asset).use { inputStream ->
                val path = context.getDatabasePath(asset).path
                val outputFile = File(path)
                FileOutputStream(outputFile).use { outputStream ->
                    inputStream.copyTo(outputStream)
                    outputStream.flush()
                }
                return path
            }
    }
}