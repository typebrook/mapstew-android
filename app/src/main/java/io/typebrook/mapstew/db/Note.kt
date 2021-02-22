package io.typebrook.mapstew.db

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * If a Note references an OSM feature, the id is in form: way/[feature id]
 * If it is not, the id is in form: note/[ISO8601 datetime string]
 */

@Entity
data class Note(
    @PrimaryKey
    val id: String,
    val lon: Double,
    val lat: Double,
    val content: String,
    val photoUri: Uri,
    val audioUri: Uri? = null
)