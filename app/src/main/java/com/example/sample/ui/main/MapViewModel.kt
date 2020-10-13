package com.example.sample.ui.main

import androidx.lifecycle.ViewModel
import com.example.sample.geometry.XYPair
import com.example.sample.geometry.isLongLatPair
import com.example.sample.livedata.SafeMutableLiveData

class MapViewModel : ViewModel() {

    // coordinate of center of map
    val coordinate = object : SafeMutableLiveData<XYPair>(121.585674 to 25.023167) {
        override val predicate = { xy: XYPair -> xy.isLongLatPair() }
    }

    // coordinate of target, camera will move to here
    val target = object : SafeMutableLiveData<XYPair>(coordinate.value) {
        override val predicate = { xy: XYPair -> xy.isLongLatPair() }
    }
}
