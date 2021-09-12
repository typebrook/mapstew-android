package io.typebrook.mapstew.db

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

/**
 * If a Survey references an OSM feature, the id is in form: way/[feature id]
 * If it is not, the id is in form: survey/[ISO8601 datetime string]
 */

@Entity
data class Survey(
    @PrimaryKey
    val dateCreated: Date = Date(),
    val relatedFeatureId: String? = null,
    val osmNoteId: Long? = null,
    val lon: Double,
    val lat: Double,
    val content: String? = null,
    val photoPaths: List<String> = emptyList(),
    val audioUri: Uri? = null,

    // If epoch time is 0, this means this Survey is newly created and makes no sense
    // This kind of Survey should only lives when user is editing a new Survey
    // It should not be upload or reserved in db for a long time
    val dateModified: Date = Date(0)
)