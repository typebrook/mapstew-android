package com.example.sample.ui.main

import android.app.Dialog
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
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
    private lateinit var coordInput: CoordInput

    private val crs get() = mapModel.crsState.value.crs
    private val coord get() = mapModel.coordinate.value

    private val validCrsList = listOf(WGS84, TWD97, TWD67)

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = requireActivity().run {

        initViewGroup()
        with(AlertDialog.Builder(this)) {
            setView(viewGroup.root)
            setTitle("foo")
            setPositiveButton("GOTO") { _, _ ->
                coordInput.wgs84LongLat?.let(mapModel.target::setValue)
            }
            create()
        }
    }

    // Initialize custom view for dialog
    private fun initViewGroup() = with(viewGroup) {

        // Initialize spinner for coordinate reference system
        with(crsSpinner) {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                validCrsList.map { it.displayName }
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {}
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, i: Int, p3: Long) {
                    with(mapModel.crsState) {
                        value = value.copy(crs = validCrsList[i])
                    }
                }
            }
        }

        // Initialize spinner for expression
        with(exprSpinner) {
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
            coordInput = when (state.expression) {
                CoordExpression.Degree -> degreeInput
                CoordExpression.DegMin -> degMinInput
                CoordExpression.DMS -> dmsInput
                else -> degreeInput
            }
            inputContainer.removeAllViews()
            inputContainer.addView(coordInput.view)

            if (state.crs.isLongLat) {
                exprSpinner.visibility = View.VISIBLE
                exprSpinner.setSelection(state.expression.ordinal)
            } else {
                exprSpinner.visibility = View.GONE
            }
        }
    }

    // region XYInput
    interface CoordInput {
        val view: View
        val wgs84LongLat: XYPair?

        // Get degree/minute/second value from EditText
        val EditText.angle: Double
            get() = text.toString().toDoubleOrNull() ?: hint.toString().toDouble()
    }

    private val degreeInput: CoordInput
        get() = object : CoordInput {
            val binding = InputDegreeBinding.inflate(layoutInflater)
            override val view: View = with(binding) {
                val xy = coord.convert(WGS84, crs)
                longitude.hint = xy.first.scaleTo(6).toString()
                latitude.hint = xy.second.scaleTo(6).toString()
                root
            }
            override val wgs84LongLat: XYPair
                get() = with(binding) {
                    val x = longitude.angle
                    val y = latitude.angle
                    return (x to y).convert(crs, WGS84)
                }
        }

    private val degMinInput: CoordInput
        get() = object : CoordInput {
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
            override val wgs84LongLat: XYPair
                get() = with(binding) {
                    val x = dm2Degree(longitudeDeg.angle.toInt() to longitudeMin.angle)
                    val y = dm2Degree(latitudeDeg.angle.toInt() to latitudeMin.angle)
                    return (x to y).convert(crs, WGS84)
                }
        }

    private val dmsInput: CoordInput
        get() = object : CoordInput {
            val binding = InputDmsBinding.inflate(layoutInflater)
            override val view: View = with(binding) {
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
            override val wgs84LongLat: XYPair
                get() = with(binding) {
                    val x = dms2Degree(
                        Triple(
                            longitudeDeg.angle.toInt(),
                            longitudeMin.angle.toInt(),
                            longitudeSec.angle
                        )
                    )
                    val y = dms2Degree(
                        Triple(
                            latitudeDeg.angle.toInt(),
                            latitudeMin.angle.toInt(),
                            latitudeSec.angle
                        )
                    )
                    return (x to y).convert(crs, WGS84)
                }
        }
// endregion
}
