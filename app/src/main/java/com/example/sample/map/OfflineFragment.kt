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
import androidx.fragment.app.DialogFragment
import com.example.sample.databinding.OfflineMapItemBinding
import com.example.sample.network.DownloadWorker


class OfflineFragment : DialogFragment() {

    data class OfflineMap(val name: String)

    val maps = listOf(
        OfflineMap("foo"),
        OfflineMap("bar")
    )

    private val adapter = object : BaseAdapter() {

        @SuppressLint("ViewHolder")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val map = getItem(position)
            val layoutInflater =
                requireContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
            val binding = OfflineMapItemBinding.inflate(layoutInflater)

            binding.name.text = map.name
            with(binding.button) {
                text = "Download"
                setOnClickListener { DownloadWorker.enqueue(requireContext()) }

            }
            return binding.root
        }

        override fun getCount(): Int = maps.size
        override fun getItem(position: Int): OfflineMap = maps[position]
        override fun getItemId(position: Int): Long = getItem(position).hashCode().toLong()
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