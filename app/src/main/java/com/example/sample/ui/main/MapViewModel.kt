package com.example.sample.ui.main

import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel

class MapViewModel : ViewModel() {
    val coordinate = MutableLiveData(121.585674 to 25.023167)
    val zoom = MutableLiveData(12.0F)
}
