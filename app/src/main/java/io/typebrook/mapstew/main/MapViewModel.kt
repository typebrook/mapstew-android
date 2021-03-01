package io.typebrook.mapstew.main

import android.graphics.PointF
import androidx.lifecycle.ViewModel
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import io.typebrook.mapstew.map.TiledFeature

typealias Camera = Triple<Double, Double, Float>

val Camera.wgs84LongLat: XYPair get() = first to second
val Camera.zoom: Float get() = third

class MapViewModel : ViewModel() {

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
    val focusPoint = SafeMutableLiveData<PointF?>(null)
    val selectableFeatures = object : SafeMutableLiveData<List<TiledFeature>>(emptyList()) {
        override val predicate = { _: List<TiledFeature> -> true }
    }
    val details = SafeMutableLiveData<String?>(null)

    val focusedFeatureId = object : SafeMutableLiveData<String?>(null) {
        override val predicate = { newValue: String? ->
            if (newValue == null || !newValue.startsWith(ID_NOTE)) focusPoint.value = null
            value != newValue
        }
    }

    val hideButtons = object : SafeMutableLiveData<Boolean>(false) {
        override val predicate = { _: Boolean ->
            focusPoint.value == null && focusedFeatureId.value == null && !displayBottomSheet.value
        }
    }
    val locateUser = SafeMutableLiveData(false)
    val displayGrid = SafeMutableLiveData(false)
    val displayLayers = SafeMutableLiveData(false)
    val displayBottomSheet = object : SafeMutableLiveData<Boolean>(false) {
        override val predicate = { value: Boolean ->
            if (!value) focusedFeatureId.value = null
            true
        }
    }

    data class CrsState(
            val crsWrapper: CRSWrapper = CRSWrapper.WGS84,
            val expression: CoordExpression = CoordExpression.DMS
    )

    companion object {
        const val ID_NOTE = "note"
    }
}
