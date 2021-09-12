package io.typebrook.mapstew.map

import android.graphics.PointF
import android.view.Gravity
import android.view.WindowManager
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.PopupWindow
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import io.typebrook.mapstew.R
import io.typebrook.mapstew.db.Survey
import io.typebrook.mapstew.db.db
import io.typebrook.mapstew.geometry.XYPair
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.map.TiledFeature.Companion.displayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

interface MapViewModelHolder {
    val model: MapViewModel

    fun Fragment.showPopupWindow(
        location: XYPair,
        anchorPoint: PointF,
        selectableFeatures: List<TiledFeature>
    ) = PopupWindow(requireContext()).apply {
        contentView = ListView(requireContext()).apply {
            adapter = ArrayAdapter<String>(context, android.R.layout.simple_list_item_1).apply {
                val items = listOf(
                    context.getString(R.string.map_btn_create_survey)
                ) + selectableFeatures.map {
                    it.displayName(context)
                }
                addAll(items)
            }
            setOnItemClickListener { _, _, position, _ ->
                val featureId = if (position != 0)
                    selectableFeatures[position - 1].osmId else
                    MapViewModel.ID_RAW_SURVEY
                val newSurvey = Survey(
                    relatedFeatureId = featureId,
                    lon = location.first,
                    lat = location.second
                )
                lifecycleScope.launch(Dispatchers.IO) {
                    db.surveyDao().insert(newSurvey)
                }
                dismiss()
            }
        }
        height = WindowManager.LayoutParams.WRAP_CONTENT
        width = 500
        elevation = 40f
        isOutsideTouchable = true
        setBackgroundDrawable(
            ContextCompat.getDrawable(
                requireContext(),
                R.drawable.shape_bottom_sheet
            )
        )
        showAtLocation(view, Gravity.NO_GRAVITY, anchorPoint.x.toInt(), anchorPoint.y.toInt())
        setOnDismissListener {
            model.focusedFeature.value = null
        }
    }
}