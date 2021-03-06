package io.typebrook.mapstew.main

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.Spinner
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.*
import io.typebrook.mapstew.geometry.*
import io.typebrook.mapstew.geometry.CRSWrapper.Companion.EPSG_3857
import io.typebrook.mapstew.geometry.CRSWrapper.Companion.TWD67
import io.typebrook.mapstew.geometry.CRSWrapper.Companion.TWD97
import io.typebrook.mapstew.geometry.CRSWrapper.Companion.WGS84
import io.typebrook.mapstew.ui.AngleFilter
import io.typebrook.mapstew.ui.LetterDigitFilter
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.lang.Character.isDigit
import kotlin.math.absoluteValue

class CoordInputDialogFragment : DialogFragment() {

    private val mapModel by activityViewModels<MapViewModel>()
    private val viewGroup by lazy { DialogCrsBinding.inflate(layoutInflater) }
    private lateinit var coordInput: CoordInput

    private val crs get() = mapModel.crsState.value.crsWrapper
    private val coord get() = mapModel.center.value.wgs84LongLat.convert(WGS84, crs)

    private val validCrsList by lazy {
        listOf(
                WGS84,
                TWD97,
                TWD67,
                TaipowerCRS(),
                RescueCRS().apply {
                    resources.getStringArray(R.array.peaks).associate {
                        it.substringBefore(",") to it.substringAfter(",")
                    }.let {
                        peaks.putAll(it)
                    }
                },
                EPSG_3857
        )
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = requireActivity().run {

        initViewGroup()
        with(MaterialAlertDialogBuilder(this, R.style.AlertDialogTheme)) {
            setView(viewGroup.root)
            setTitle(R.string.dialog_coord_input_desc)
            setPositiveButton("GOTO") { _, _ ->
                coordInput.wgs84LongLat?.run {
                    mapModel.target.value = Triple(first, second, mapModel.center.value.zoom)
                }
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
                        value = value.copy(crsWrapper = validCrsList[i])
                    }
                }
            }
        }

        // Initialize spinner for expression
        with(exprSpinner) {
            adapter = ArrayAdapter(
                requireContext(),
                android.R.layout.simple_spinner_dropdown_item,
                resources.getStringArray(R.array.coord_expr)
            )
            onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                override fun onNothingSelected(p0: AdapterView<*>?) {}
                override fun onItemSelected(p0: AdapterView<*>?, p1: View?, i: Int, p3: Long) {
                    with(mapModel.crsState) {
                        value = value.copy(expression = selectableExpressions[i])
                    }
                }
            }
        }

        with(displayGrid) {
            isChecked = mapModel.displayGrid.value
            displayGrid.setOnCheckedChangeListener { _, isChecked ->
                mapModel.displayGrid.value = isChecked
            }
        }

        // Change views by crsState
        mapModel.crsState.observe(this@CoordInputDialogFragment) { state ->
            coordInput = when (state.expression) {
                CoordExpression.Degree -> degreeInput(layoutInflater)
                CoordExpression.DegMin -> degMinInput(layoutInflater)
                CoordExpression.DMS -> dmsInput(layoutInflater)
                CoordExpression.XY -> xyInput(layoutInflater)
                CoordExpression.SINGLE -> singleInput(layoutInflater)
            }
            inputContainer.removeAllViews()
            inputContainer.addView(coordInput.view)

            crsSpinner.setSelection(validCrsList.indexOf(state.crsWrapper))
            if (state.crsWrapper.isLongLat) {
                exprGroup.visibility = View.VISIBLE
                exprSpinner.setSelection(state.expression.ordinal)
            } else {
                exprGroup.visibility = View.GONE
            }
        }
    }

    // region CoordInput
    interface CoordInput {
        val view: View
        val wgs84LongLat: XYPair?

        // Get raw value from EditText
        val EditText.raw: String
            get() = if (text.isNotBlank()) text.toString() else hint.toString()

        // Get vector value from EditText
        val EditText.vector: Double
            get() = text.toString().filter(::isDigit).toDoubleOrNull()
                ?: hint.toString().filter(::isDigit).toDouble()

        // Get degree/minute/second value from EditText
        val EditText.angle: Double
            get() = text.toString().toDoubleOrNull() ?: hint.toString().toDouble()

        // Get degree sign from Spinner
        val Spinner.sign: Int
            get() = if (selectedItemPosition == 0) 1 else -1
    }

    private fun xyInput(layoutInflater: LayoutInflater) = object : CoordInput {
        val binding = InputXyBinding.inflate(layoutInflater)
        override val view: View = with(binding) {
            val xyString = xy2IntString(coord)
            x.hint = xyString.first
            y.hint = xyString.second
            root
        }
        override val wgs84LongLat: XYPair
            get() = with(binding) {
                return (x.vector to y.vector).convert(crs, WGS84)
            }
    }

    private fun singleInput(layoutInflater: LayoutInflater) = object : CoordInput {
        val binding = InputSingleBinding.inflate(layoutInflater)
        val currentCRS = crs
        override val view: View = with(binding) {
            if (currentCRS is CoordMask) {
                singleCoord.hint = currentCRS.mask(coord, null) ?: getString(R.string.out_of_boundary)
            }
            singleCoord.filters = arrayOf(LetterDigitFilter())
            root
        }
        override val wgs84LongLat: XYPair
            get() = with(binding) {
                if (currentCRS is CoordMask) {
                    try {
                        singleCoord.raw.let(currentCRS::reverseMask).convert(crs, WGS84)
                    } catch (e: CoordMask.Companion.CannotHandleException) {
                        coord.convert(crs, WGS84)
                    }
                } else {
                    coord.convert(crs, WGS84)
                }
            }
    }

    private fun degreeInput(layoutInflater: LayoutInflater) = object : CoordInput {
        val binding = InputDegreeBinding.inflate(layoutInflater)
        override val view: View = with(binding) {
            spinnerEw.setSelection(if (coord.first >= 0) 0 else 1)
            spinnerNs.setSelection(if (coord.second >= 0) 0 else 1)
            longitude.hint = coord.first.absoluteValue.scaleTo(6).toString()
            longitude.filters = arrayOf(AngleFilter(180, true))
            latitude.hint = coord.second.absoluteValue.scaleTo(6).toString()
            latitude.filters = arrayOf(AngleFilter(90, true))
            root
        }
        override val wgs84LongLat: XYPair
            get() = with(binding) {
                val x = longitude.angle * spinnerEw.sign
                val y = latitude.angle * spinnerNs.sign
                return (x to y).convert(crs, WGS84)
            }
    }

    private fun degMinInput(layoutInflater: LayoutInflater) = object : CoordInput {
        val binding = InputDegMinBinding.inflate(layoutInflater)
        override val view: View = with(binding) {
            spinnerEw.setSelection(if (coord.first >= 0) 0 else 1)
            spinnerNs.setSelection(if (coord.second >= 0) 0 else 1)
            coord.first.absoluteValue.let(degree2DM).run {
                longitudeDeg.hint = first.toString()
                longitudeDeg.filters = arrayOf(AngleFilter(180, true))
                longitudeMin.hint = second.toString()
                longitudeMin.filters = arrayOf(AngleFilter(60, false))
            }
            coord.second.absoluteValue.let(degree2DM).run {
                latitudeDeg.hint = first.toString()
                latitudeDeg.filters = arrayOf(AngleFilter(90, true))
                latitudeMin.hint = second.toString()
                latitudeMin.filters = arrayOf(AngleFilter(60, false))
            }
            root
        }
        override val wgs84LongLat: XYPair
            get() = with(binding) {
                val x =
                    dm2Degree(longitudeDeg.angle.toInt() to longitudeMin.angle) * spinnerEw.sign
                val y =
                    dm2Degree(latitudeDeg.angle.toInt() to latitudeMin.angle) * spinnerNs.sign
                return (x to y).convert(crs, WGS84)
            }
    }

    private fun dmsInput(layoutInflater: LayoutInflater) = object : CoordInput {
        val binding = InputDmsBinding.inflate(layoutInflater)
        override val view: View = with(binding) {
            spinnerEw.setSelection(if (coord.first >= 0) 0 else 1)
            spinnerNs.setSelection(if (coord.second >= 0) 0 else 1)
            coord.first.absoluteValue.let(degree2DMS).run {
                longitudeDeg.hint = first.toString()
                longitudeDeg.filters = arrayOf(AngleFilter(180, true))
                longitudeMin.hint = second.toString()
                longitudeMin.filters = arrayOf(AngleFilter(60, false))
                longitudeSec.hint = third.toString()
                longitudeSec.filters = arrayOf(AngleFilter(60, false, 4))
            }
            coord.second.absoluteValue.let(degree2DMS).run {
                latitudeDeg.hint = first.toString()
                latitudeDeg.filters = arrayOf(AngleFilter(90, true))
                latitudeMin.hint = second.toString()
                latitudeMin.filters = arrayOf(AngleFilter(60, false))
                latitudeSec.hint = third.toString()
                latitudeSec.filters = arrayOf(AngleFilter(60, false, 4))
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
                ) * spinnerEw.sign
                val y = dms2Degree(
                    Triple(
                        latitudeDeg.angle.toInt(),
                        latitudeMin.angle.toInt(),
                        latitudeSec.angle
                    )
                ) * spinnerNs.sign
                return (x to y).convert(crs, WGS84)
            }
    }
// endregion
}
