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
import com.example.sample.databinding.DialogCrsBinding
import com.example.sample.databinding.InputDegMinBinding
import com.example.sample.databinding.InputDegreeBinding
import com.example.sample.databinding.InputDmsBinding
import com.example.sample.geometry.*
import com.example.sample.geometry.CoordRefSys.Companion.TWD67
import com.example.sample.geometry.CoordRefSys.Companion.TWD97
import com.example.sample.geometry.CoordRefSys.Companion.WGS84

class CrsDialogFragment : DialogFragment() {

    private val mapModel by activityViewModels<MapViewModel>()
    private val viewGroup by lazy { DialogCrsBinding.inflate(layoutInflater) }
    private lateinit var xyInput: XYInput

    private val crs get() = mapModel.crsState.value.crs
    private val coord get() = mapModel.coordinate.value

    private val crsList = listOf(WGS84, TWD97, TWD67)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = requireActivity().run {

        initViewGroup()
        with(AlertDialog.Builder(this)) {
            setView(viewGroup.root)
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
        with(crsOptions) {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                crsList.map { it.displayName }
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {}
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, i: Int, p3: Long) {
                    with(mapModel.crsState) {
                        value = value.copy(crs = crsList[i])
                    }
                }
            }
        }

        // Initialize spinner for expression
        with(exprOptions) {
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
                CoordExpression.Degree -> degreeInput
                CoordExpression.DegMin -> degMinInput
                CoordExpression.DMS -> dmsInput
                else -> degreeInput
            }
            inputContainer.removeAllViews()
            inputContainer.addView(xyInput.view)

            if (state.crs.isLongLat) {
                exprOptions.visibility = View.VISIBLE
                exprOptions.setSelection(state.expression.ordinal)
            } else {
                exprOptions.visibility = View.GONE
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
            val binding = InputDegreeBinding.inflate(layoutInflater)
            override val view: View = with(binding) {
                val xy = coord.convert(WGS84, crs)
                longitude.hint = xy.first.scaleTo(6).toString()
                latitude.hint = xy.second.scaleTo(6).toString()
                root
            }
            override val xy: XYPair
                get() = with(binding) {
                    val x = longitude.text.toString().toDoubleOrNull() ?: coord.first
                    val y = latitude.text.toString().toDoubleOrNull() ?: coord.second
                    return x to y
                }
        }

    private val degMinInput: XYInput
        get() = object : XYInput {
            val binding = InputDegMinBinding.inflate(layoutInflater)
            override val view: View = with(binding) {
                val xy = coord.convert(WGS84, crs)
                xy.first.let(degree2DM).run {
                    longitudeDeg.hint = first.toString()
                    longitudeMin.hint = second.toString()
                }
                xy.second.let(degree2DM).run {
                    latitudeDeg.hint = first.toString()
                    latitudeMin.hint = second.toString()
                }
                root
            }
            override val xy: XYPair
                get() {
                    val x = 0.0
                    val y = 0.0
                    return x to y
                }
        }

    private val dmsInput: XYInput
        get() = object : XYInput {
            override val view: View = InputDmsBinding.inflate(layoutInflater).run {
                val xy = coord.convert(WGS84, crs)
                xy.first.let(degree2DMS).run {
                    longitudeDeg.hint = first.toString()
                    longitudeMin.hint = second.toString()
                    longitudeSec.hint = third.toString()
                }
                xy.second.let(degree2DMS).run {
                    latitudeDeg.hint = first.toString()
                    latitudeMin.hint = second.toString()
                    latitudeSec.hint = third.toString()
                }
                root
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
