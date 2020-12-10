package com.example.sample.map

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import com.example.sample.R
import com.example.sample.databinding.OfflineMapItemBinding
import com.example.sample.network.DownloadWorker
import com.google.android.material.button.MaterialButton


class OfflineFragment : DialogFragment() {

    inner class OfflineMap(val displayName: String, val path: String) {
        val isValid = requireContext().getDatabasePath(path.substringAfterLast("/")).exists()
    }

    val maps by lazy {
        listOf(
            OfflineMap(
                displayName = "Contours",
                path = "typebrook/contours/releases/download/2020/contours.mbtiles"
            ),
            OfflineMap(
                displayName = "Rudymap",
                path = "typebrook/mapstew/releases/download/daily-taiwan-pbf/taiwan-daily.osm.pbf"
            )
        )
    }

    private val adapter by lazy {
        object : BaseAdapter() {
            @SuppressLint("ViewHolder")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
                val map = getItem(position)
                val layoutInflater =
                    requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
                val binding = OfflineMapItemBinding.inflate(layoutInflater)

                binding.name.text = map.displayName

                with(binding.button) {
                    text = if (map.isValid) getString(R.string.in_use) else "Download"
                    setOnClickListener { DownloadWorker.enqueue(requireContext(), map.path) }
                    if (map.isValid) {
                        isClickable = false
                        setBackgroundColor(resources.getColor(android.R.color.darker_gray))
                    }
                }
                return binding.root
            }

            override fun getCount(): Int = maps.size
            override fun getItem(position: Int): OfflineMap = maps[position]
            override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()
        }
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog =
        with(AlertDialog.Builder(requireContext())) {
            setAdapter(adapter) { _, _ -> }
            create()
        }.apply {
            // Override dismiss dialog, do nothing instead
            listView.onItemClickListener = null
        }
}