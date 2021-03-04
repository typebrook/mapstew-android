package io.typebrook.mapstew.db

import androidx.lifecycle.LiveData
import androidx.room.*
import java.util.*

@Dao
interface SurveyDao {
    @Query("SELECT * FROM survey")
    fun getAll(): LiveData<List<Survey>>

    @Query("SELECT * FROM survey where osmNoteId is NULL")
    fun listReadyToUpload(): List<Survey>

    @Query("SELECT * FROM survey where dateCreated = :timeStamp")
    fun getFromKey(timeStamp: Long): List<Survey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(survey: Survey)

    @Delete
    fun delete(survey: Survey)
}
