package com.example.sample.ui.main

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import com.example.sample.R
import kotlinx.android.synthetic.main.dialog_crs.view.*

class CrsDialogFragment : DialogFragment() {

    private val mapModel by activityViewModels<MapViewModel>()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog = requireActivity().run {
        val viewGroup = layoutInflater.inflate(
            R.layout.dialog_crs,
            requireActivity().findViewById(R.id.root_crs)
        ).apply {
            longitude.hint = mapModel.coordinate.value?.first?.toString()
            latitude.hint = mapModel.coordinate.value?.second?.toString()
        }

        AlertDialog.Builder(this).run {
            setView(viewGroup)
            setTitle("foo")
            setPositiveButton("GOTO") { _, _ ->
                mapModel.setTarget(
                    viewGroup.run {
                        longitude.text.toString().toDouble() to latitude.text.toString().toDouble()
                    }
                )
            }
            create()
        }
    }
}