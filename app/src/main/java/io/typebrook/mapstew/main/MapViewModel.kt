package io.typebrook.mapstew.main

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Transformations
import androidx.lifecycle.viewModelScope
import io.typebrook.mapstew.db.AppDatabase
import io.typebrook.mapstew.db.Survey
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.*

typealias Camera = Triple<Double, Double, Float>

val Camera.wgs84LongLat: XYPair get() = first to second
val Camera.zoom: Float get() = third

class MapViewModel(application: Application) : AndroidViewModel(application) {

    val db = AppDatabase.getDatabase(application)

    // Camera of center of map
    val center = object : SafeMutableLiveData<Camera>(
            Triple(121.585674, 25.023167, 12F)
    ) {
        override val predicate = { value: Camera ->
            with(value) { first to second }.isLongLatPair()
        }
    }

    // Camera of target, camera will move to here
    val target = object : SafeMutableLiveData<Camera>(center.value) {
        override val predicate = { value: Camera ->
            with(value) { first to second }.isLongLatPair()
        }
    }

    val crsState = object : SafeMutableLiveData<CrsState>(CrsState()) {
        override val transformer = { newState: CrsState ->
            when {
                newState.crsWrapper.isLongLat && !value.crsWrapper.isLongLat -> {
                    newState.copy(expression = CoordExpression.DMS)
                }
                !newState.crsWrapper.isLongLat -> {
                    val expression = if (newState.crsWrapper is CoordMask)
                        CoordExpression.SINGLE else
                        CoordExpression.XY
                    newState.copy(expression = expression)
                }
                else -> newState
            }
        }
    }

    // List of MBTiles inside internal storage
    val mbTilesList = object : SafeMutableLiveData<List<String>>(emptyList()) {
        override val transformer = { newState: List<String> -> newState.distinct() }
    }

    // Details of features rendered on map
    val details = SafeMutableLiveData<String?>(null)

    val focusSurveyId = object : SafeMutableLiveData<Date?>(null) {
        override val transformer = { newState: Date? ->
            
            newState
        }
    }
    val focusSurvey = SafeMutableLiveData<Survey?>(null)

    val hideButtons = SafeMutableLiveData(false)
    val locateUser = object : SafeMutableLiveData<Boolean>(false) {
        override val predicate = { _: Boolean -> true }
    }
    val displayGrid = SafeMutableLiveData(false)
    val displayLayers = SafeMutableLiveData(false)
    val displayBottomSheet = SafeMutableLiveData(false)

    data class CrsState(
            val crsWrapper: CRSWrapper = CRSWrapper.WGS84,
            val expression: CoordExpression = CoordExpression.DMS
    )

    companion object {
        const val ID_RAW_SURVEY = "survey"
    }
}
