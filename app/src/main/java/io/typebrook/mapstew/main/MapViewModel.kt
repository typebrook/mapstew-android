package io.typebrook.mapstew.main

import android.graphics.PointF
import android.view.View
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.lifecycleScope
import io.typebrook.mapstew.R
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.livedata.SafeMutableLiveData
import io.typebrook.mapstew.map.TiledFeature
import io.typebrook.mapstew.map.TiledFeature.Companion.displayName
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

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
    val focusLngLat = SafeMutableLiveData<XYPair?>(null)
    val details = SafeMutableLiveData<String?>(null)

    val focusedFeatureId = object : SafeMutableLiveData<String?>(null) {
        override val predicate = { newValue: String? ->
            if (newValue == null) {
                focusLngLat.value = null
            }
            value != newValue
        }
    }
    val focusedFeature = SafeMutableLiveData<TiledFeature?>(null)

    val hideButtons = object : SafeMutableLiveData<Boolean>(false) {
        override val predicate = { _: Boolean ->
            focusedFeatureId.value == null && !displayBottomSheet.value
        }
    }
    val locateUser = object : SafeMutableLiveData<Boolean>(false) {
        override val predicate = { _: Boolean -> true }
    }
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
        const val ID_RAW_SURVEY = "survey"
    }
}
