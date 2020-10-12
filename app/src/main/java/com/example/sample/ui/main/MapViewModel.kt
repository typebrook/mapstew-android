package com.example.sample.ui.main

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.sample.geometry.XYPair
import java.math.RoundingMode

class MapViewModel : ViewModel() {

    // coordinate of center of map
    private val _coordinate = MutableLiveData(121.585674 to 25.023167)
    val coordinate: LiveData<XYPair> get() = _coordinate
    fun setCoordinate(xy: XYPair) {
        _coordinate.value = xy.first.scaleTo6() to xy.second.scaleTo6()
    }

    // coordinate of target, camera will move to here
    private val _target = MutableLiveData<XYPair>(null)
    val target: LiveData<XYPair> get() = _target
    fun setTarget(xy: XYPair) {
        _target.value = xy.first.scaleTo6() to xy.second.scaleTo6()
    }

    private fun Double.scaleTo6() = toBigDecimal().setScale(6, RoundingMode.HALF_UP).toDouble()
}
