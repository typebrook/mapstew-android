package com.example.sample.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.sample.R
import com.example.sample.geometry.scaleDownTo
import kotlinx.android.synthetic.main.dialog_crs.view.*

class CrsDialogFragment : DialogFragment() {

    private val mapModel by activityViewModels<MapViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = requireActivity().run {
        val viewGroup = layoutInflater.inflate(
            R.layout.dialog_crs,
            requireActivity().findViewById(R.id.root_crs)
        ).apply {
            longitude.hint = mapModel.coordinate.value.first.scaleDownTo(6).toString()
            latitude.hint = mapModel.coordinate.value.second.scaleDownTo(6).toString()
        }

        AlertDialog.Builder(this).run {
            setView(viewGroup)
            setTitle("foo")
            setPositiveButton("GOTO") { _, _ ->
                val x = viewGroup.longitude.text.toString().toDoubleOrNull()
                    ?: mapModel.coordinate.value.first
                val y = viewGroup.latitude.text.toString().toDoubleOrNull()
                    ?: mapModel.coordinate.value.second
                mapModel.target.value = x to y
            }
            create()
        }
    }
}