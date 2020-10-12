package com.example.sample.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.sample.geometry.XYPair

class MapViewModel : ViewModel() {
    val coordinate = MutableLiveData(121.585674 to 25.023167)
    val zoom = MutableLiveData(12.0F)

    val target = MutableLiveData<XYPair?>(null)
}
