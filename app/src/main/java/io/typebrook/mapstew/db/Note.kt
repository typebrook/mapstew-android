package io.typebrook.mapstew.db

import android.net.Uri
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.*

@Entity
data class Note(
    @PrimaryKey
    val date: Date,
    val osmId: String?,
    val lon: Double,
    val lat: Double,
    val content: String,
    val photo: Uri
)