package com.example.sample.ui.main

import androidx.lifecycle.ViewModel
import com.example.sample.geometry.CoordExpression
import com.example.sample.geometry.CoordRefSys
import com.example.sample.geometry.XYPair
import com.example.sample.geometry.isLongLatPair
import com.example.sample.livedata.SafeMutableLiveData

typealias Camera = Triple<Double, Double, Float>

val Camera.wgs84LongLat: XYPair get() = first to second
val Camera.zoom: Float get() = third

class MapViewModel : ViewModel() {

    // coordinate of center of map
    val center = object : SafeMutableLiveData<Camera>(
        Triple(121.585674, 25.023167, 12F)
    ) {
        override val predicate = { value: Camera ->
            with(value) { first to second }.isLongLatPair()
        }
    }

    // coordinate of target, camera will move to here
    val target = object : SafeMutableLiveData<Camera>(center.value) {
        override val predicate = { value: Camera ->
            with(value) { first to second }.isLongLatPair()
        }
    }

    val crsState = object : SafeMutableLiveData<CrsState>(CrsState()) {
        override val transformer = { newState: CrsState ->
            when {
                newState.crs.isLongLat && !value.crs.isLongLat -> newState.copy(expression = CoordExpression.DMS)
                !newState.crs.isLongLat && value.crs.isLongLat -> newState.copy(expression = CoordExpression.SINGLE)
                else -> newState
            }
        }
    }

    data class CrsState(
        val crs: CoordRefSys = CoordRefSys.WGS84,
        val expression: CoordExpression = CoordExpression.DMS
    )
}
