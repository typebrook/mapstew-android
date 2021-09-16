package io.typebrook.mapstew.db

import androidx.lifecycle.LiveData
import androidx.room.*
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import java.util.*

@Dao
abstract class SurveyDao {
    @Query("SELECT * FROM survey")
    abstract fun getAll(): LiveData<List<Survey>>

    @Query("SELECT * FROM survey WHERE dateModified = 0 LIMIT 1")
    abstract fun getNewlyCreated(): LiveData<Survey?>

    @Query("SELECT * FROM survey where osmNoteId is NULL")
    abstract fun listReadyToUpload(): List<Survey>

    @Query("SELECT * FROM survey WHERE dateCreated = :timeStamp")
    abstract fun getFromKey(timeStamp: Long): LiveData<Survey?>

    @Update(entity = Survey::class)
    abstract fun updateInner(survey: Survey)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    abstract fun insert(survey: Survey)

    @Delete
    abstract fun delete(survey: Survey)

    fun update(survey: Survey) = updateInner(
        survey.copy(dateModified = Date())
    )
}
