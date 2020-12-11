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
import androidx.fragment.app.activityViewModels
import androidx.work.WorkInfo
import com.example.sample.R
import com.example.sample.databinding.OfflineMapItemBinding
import com.example.sample.main.MapViewModel
import com.example.sample.network.DownloadWorker
import com.example.sample.network.DownloadWorker.Companion.DATA_KEY_PROGRESS


// TODO i18n for texts
class OfflineFragment : DialogFragment() {

    val mapModel by activityViewModels<MapViewModel>()

    inner class OfflineMap(val displayName: String, val path: String)

    val maps by lazy {
        listOf(
            OfflineMap(
                displayName = "Contours",
                path = "typebrook/contours/releases/download/2020/contours.mbtiles"
            ),
            OfflineMap(
                displayName = "Mapstew",
                path = "typebrook/mapstew/releases/download/cache-2020.12.11/mapstew.mbtiles"
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
                    val localMBTiles = map.path.substringAfterLast("/")

                    setOnClickListener {
                        isClickable = false

                        val liveData = DownloadWorker.enqueue(requireContext(), map.path)
                        liveData.observe(this@OfflineFragment) { info ->
                            when (info.state) {
                                WorkInfo.State.SUCCEEDED ->
                                    with(mapModel.mbTilesList) { value = value + localMBTiles }
                                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                    text = "Download"
                                    isCheckable = true
                                }
                                else -> {
                                    val progress = info.progress.getString(DATA_KEY_PROGRESS)
                                    text = "%s%s%s".format(
                                        "下載中",
                                        if (progress != null) "\n" else "",
                                        progress ?: ""
                                    )
                                }
                            }
                        }
                    }

                    mapModel.mbTilesList.observe(this@OfflineFragment) { list ->
                        if (localMBTiles in list) {
                            text = getString(R.string.in_use)
                            isClickable = false
                            setBackgroundColor(resources.getColor(android.R.color.darker_gray))
                        } else {
                            text = "Download"
                            isCheckable = true
                        }
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