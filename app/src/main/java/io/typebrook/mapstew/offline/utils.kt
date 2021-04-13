package io.typebrook.mapstew.offline

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.typebrook.mapstew.network.DownloadWorker.Companion.KEY_URL_TEMPLATE
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.math.pow

const val EXTENSION_MBTILES = ".mbtiles"

fun Context.getLocalMBTiles() = databaseList().filter { it.endsWith(EXTENSION_MBTILES) }

fun Context.importMBTilesIntoMbgl(mbtiles: String) {
    val db: SQLiteDatabase = try {
        val dbFile = File(filesDir.absolutePath + File.separator + "mbgl-offline.db")
        if (!dbFile.exists()) throw Exception()
        Timber.d("jojojo success to load")
        SQLiteDatabase.openDatabase(dbFile.path, null, SQLiteDatabase.OPEN_READWRITE)
    } catch (e: Exception) {
        Timber.d("jojojo fail to load")
        return
    }

    val mbtiles: SQLiteDatabase = try {
        val dbFile = getDatabasePath(mbtiles)
        if (!dbFile.exists()) throw Exception(dbFile.name)
        Timber.d("jojojo success to load ${dbFile.name}")
        SQLiteDatabase.openOrCreateDatabase(dbFile, null)
    } catch (e: Exception) {
        Timber.d("jojojo fail to load ${e.message}")
        return
    }

    db.use {
        it.beginTransactionNonExclusive()

        val urlTemplate = mbtiles.query("metadata",
            arrayOf("name", "value"),
            "name = ?",
            arrayOf(KEY_URL_TEMPLATE),
            null, null, null, null).use { cursor ->
            cursor.moveToFirst()
            cursor.getString(1)
        }
        Timber.d("jojojo url_template: $urlTemplate")
        mbtiles.query(
            "tiles", null, null, null, null, null, null ).use { cursor ->
            cursor.moveToFirst()
            while (!cursor.isLast) {
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
                    put("compressed", if (urlTemplate.endsWith("png")) 0 else 1)
                    put("accessed", Date().time/1000)
                    put("modified", Date().time/1000)
                    put("expires", Date().time/1000 + 8640000)
                    put("data", cursor.getBlob(dataIndex))
                }
                Timber.d("jojojo record $record")
                val newId = db.insertWithOnConflict("tiles", null, record, SQLiteDatabase.CONFLICT_REPLACE)
                Timber.d("jojoj insert ${newId}")

                cursor.moveToNext()
            }
        }
        it.setTransactionSuccessful()
        it.endTransaction()

        File(mbtiles.path).renameTo(File(mbtiles.path + ".bak"))
        Timber.d("Rename ${mbtiles.path} into $${mbtiles.path}.bak")
    }
}