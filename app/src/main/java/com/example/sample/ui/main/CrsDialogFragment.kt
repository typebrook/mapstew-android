package com.example.sample.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.observe
import com.example.sample.R
import com.example.sample.geometry.*
import com.example.sample.geometry.CoordExpression.DegMin
import com.example.sample.geometry.CoordExpression.Degree
import com.example.sample.geometry.CoordRefSys.Companion.TWD67
import com.example.sample.geometry.CoordRefSys.Companion.TWD97
import com.example.sample.geometry.CoordRefSys.Companion.WGS84
import kotlinx.android.synthetic.main.dialog_crs.view.*
import kotlinx.android.synthetic.main.input_deg_min.view.*
import kotlinx.android.synthetic.main.input_degree.view.*

class CrsDialogFragment : DialogFragment() {

    private val mapModel by activityViewModels<MapViewModel>()
    private val viewGroup by lazy { layoutInflater.run { inflate(R.layout.dialog_crs, null) } }
    private lateinit var xyInput: XYInput

    private val crs get() = mapModel.crsState.value.crs
    private val coord get() = mapModel.coordinate.value

    private val crsOptions = listOf(WGS84, TWD97, TWD67)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = requireActivity().run {

        initViewGroup()
        with(AlertDialog.Builder(this)) {
            setView(viewGroup)
            setTitle("foo")
            setPositiveButton("GOTO") { _, _ ->
                mapModel.target.value = xyInput.xy.convert(crs, WGS84)
            }
            create()
        }
    }

    // Initialize custom view for dialog
    private fun initViewGroup() = with(viewGroup) {

        // Initialize spinner for coordinate reference system
        with(crs_options) {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                crsOptions.map { it.displayName }
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {}
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, i: Int, p3: Long) {
                    with(mapModel.crsState) {
                        value = value.copy(crs = crsOptions[i])
                    }
                }
            }
        }

        // Initialize spinner for expression
        with(expr_options) {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                selectableExpressions
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {}
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, i: Int, p3: Long) {
                    with(mapModel.crsState) {
                        value = value.copy(expression = CoordExpression.values()[i])
                    }
                }
            }
        }

        // Change views by crsState
        mapModel.crsState.observe(this@CrsDialogFragment) { state ->
            xyInput = when (state.expression) {
                Degree -> degreeInput
                DegMin -> degMinInput
                else -> degreeInput
            }
            input_container.removeAllViews()
            input_container.addView(xyInput.view)

            if (state.crs.isLongLat) {
                expr_options.visibility = View.VISIBLE
                expr_options.setSelection(state.expression.ordinal)
            } else {
                expr_options.visibility = View.GONE
            }
        }
    }

    // region XYInput
    interface XYInput {
        val view: View
        val xy: XYPair
    }

    private val degreeInput: XYInput
        get() = object : XYInput {
            override val view: View = with(layoutInflater) {
                inflate(R.layout.input_degree, null, false)
            }.apply {
                val xy = coord.convert(WGS84, crs)
                longitude.hint = xy.first.scaleTo(6).toString()
                latitude.hint = xy.second.scaleTo(6).toString()
            }
            override val xy: XYPair
                get() {
                    val x = view.longitude.text.toString().toDoubleOrNull() ?: coord.first
                    val y = view.latitude.text.toString().toDoubleOrNull() ?: coord.second
                    return x to y
                }
        }

    private val degMinInput: XYInput
        get() = object : XYInput {
            override val view: View = with(layoutInflater) {
                inflate(R.layout.input_deg_min, null, false)
            }.apply {
                val xy = coord.convert(WGS84, crs)
                xy.first.let(degree2DM).run {
                    longitude_deg.hint = first.toString()
                    longitude_min.hint = second.toString()
                }
                xy.second.let(degree2DM).run {
                    latitude_deg.hint = first.toString()
                    latitude_min.hint = second.toString()
                }
            }
            override val xy: XYPair
                get() {
                    val x = 0.0
                    val y = 0.0
                    return x to y
                }
        }
// endregion
}
