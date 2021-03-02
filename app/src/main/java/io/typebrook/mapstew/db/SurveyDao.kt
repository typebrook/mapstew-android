package io.typebrook.mapstew.db

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface SurveyDao {
    @Query("SELECT * FROM survey")
    fun getAll(): LiveData<List<Survey>>

    @Query("SELECT * FROM survey where dateCreated = :id")
    fun getFromId(id: String): List<Survey>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(survey: Survey)

    @Delete
    fun delete(survey: Survey)
}
