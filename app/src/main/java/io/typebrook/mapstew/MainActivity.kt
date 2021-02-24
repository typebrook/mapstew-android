package io.typebrook.mapstew

import android.os.Bundle
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import io.typebrook.mapstew.main.MapViewModel
import timber.log.Timber

class MainActivity : AppCompatActivity() {

    private val mapModel by viewModels<MapViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        Timber.plant(Timber.DebugTree())
    }

    override fun onBackPressed() {
        // If Bottom Sheet is displayed, close it
        if (mapModel.displayBottomSheet.value) {
            mapModel.displayBottomSheet.value = false
            return
        }

        super.onBackPressed()
    }
}
