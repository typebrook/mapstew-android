package io.typebrook.mapstew.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteException
import timber.log.Timber
import java.io.File
import java.util.*
import java.util.zip.DeflaterInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.pow

const val EXTENSION_MBTILES = ".mbtiles"

fun Context.getLocalMBTiles() = databaseList().filter { it.endsWith(EXTENSION_MBTILES) }

data class OfflineMap(
    val displayName: String,
    val path: String,
    val urlTemplate: String
) {
    val fileName get() = path.substringAfterLast('/')
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

fun Context.importMBTilesIntoMbgl(mbtiles: File, urlTemplate: String) {
    val db: SQLiteDatabase = try {
        val dbFile = File(filesDir.absolutePath + File.separator + "mbgl-offline.db")
        if (!dbFile.exists()) throw Exception()
        Timber.d("jojojo success to load mbgl")
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
    } catch (e: Exception) {
        Timber.d("jojojo fail to load")
        return
    }

    val mbtilesDb: SQLiteDatabase = try {
        Timber.d("jojojo success to load ${mbtiles.name}")
        SQLiteDatabase.openOrCreateDatabase(mbtiles, null)
    } catch (e: Exception) {
        Timber.d("jojojo fail to load ${e.message}")
        return
    }

    db.use {
        it.beginTransactionNonExclusive()

        mbtilesDb.query(
            "tiles", null, null, null, null, null, null
        ).use { cursor ->
            cursor.moveToFirst()

            var index = 1
            while (cursor.moveToNext() && index < 5000) {
                val zIndex = cursor.getColumnIndex("zoom_level")
                val xIndex = cursor.getColumnIndex("tile_column")
                val yIndex = cursor.getColumnIndex("tile_row")
                val dataIndex = cursor.getColumnIndex("tile_data")

                val record = ContentValues().apply {
                    put("url_template", urlTemplate)
                    put("pixel_ratio", 1)
                    val z = cursor.getInt(zIndex)
                    val x = cursor.getInt(xIndex)
                    val yInTMS = cursor.getInt(yIndex)
                    val yInXYZ = (2.0.pow(z)).toInt() - 1 - yInTMS
                    put("z", z)
                    put("x", x)
                    put("y", yInXYZ)
                    // FIXME quick workaround
                    val isVector = with(urlTemplate) { endsWith("pbf") || endsWith("mvt") }
                    put("compressed", if (isVector) 1 else 0)
                    put("accessed", Date().time / 1000)
                    put("modified", Date().time / 1000)
                    put("expires", Date().time / 1000 + 8640000)
                    val rawData = cursor.getBlob(dataIndex)
                    val data = if (isVector) {
                        DeflaterInputStream(rawData.inputStream()).use { input ->
                            input.readBytes()
                        }
                    } else {
                        rawData
                    }
                    put("data", data)
                }
                Timber.d("jojojo record $record")
                try {
                    val newId = db.insertOrThrow(
                        "tiles",
                        null,
                        record
                    )
                    Timber.d("jojoj insert $newId")
                } catch (e: SQLiteException) {
                    e.printStackTrace()
                }
//                val newId = db.insertWithOnConflict(
//                    "tiles",
//                    null,
//                    record,
//                    SQLiteDatabase.CONFLICT_REPLACE
//                )
                index++
            }
            cursor.close()
            db.isDbLockedByCurrentThread
        }
        it.setTransactionSuccessful()
        it.endTransaction()

        File(mbtilesDb.path).renameTo(File(mbtilesDb.path + ".bak"))
        Timber.d("Rename ${mbtilesDb.path} into $${mbtilesDb.path}.bak")
    }
}
