package io.typebrook.mapstew.map

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.work.WorkInfo
import androidx.work.WorkManager
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.OfflineMapItemBinding
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.network.DownloadWorker
import io.typebrook.mapstew.network.DownloadWorker.Companion.DATA_KEY_PROGRESS
import io.typebrook.mapstew.offline.getLocalMBTiles
import com.google.android.material.dialog.MaterialAlertDialogBuilder


// TODO i18n for texts
class OfflineFragment : DialogFragment() {

    val mapModel by activityViewModels<MapViewModel>()
    private val workManager by lazy { WorkManager.getInstance(requireContext()) }

    inner class OfflineMap(val displayName: String, val path: String)

    val maps = listOf(
        OfflineMap(
            displayName = "Contours",
            path = "typebrook/contours/releases/download/2020/contours.mbtiles"
        ),
        OfflineMap(
            displayName = "Mapstew",
            path = "typebrook/mapstew/releases/download/cache-2020.12.11/mapstew.mbtiles"
        ),
        OfflineMap(
            displayName = "Hillshade",
            path = "osmhacktw/terrain-rgb/releases/download/2020/terrain-rgb.mbtiles"
        )
    )

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

                    workManager.getWorkInfosByTagLiveData(localMBTiles)
                        .observe(this@OfflineFragment) { infoList ->
                            val info = infoList.firstOrNull() ?: return@observe

                            when (info.state) {
                                WorkInfo.State.SUCCEEDED -> {
                                    with(mapModel.mbTilesList) {
                                        if (localMBTiles !in value &&
                                            localMBTiles in requireContext().getLocalMBTiles()
                                        ) {
                                            value = requireContext().getLocalMBTiles()
                                        }
                                    }
                                }
                                WorkInfo.State.FAILED, WorkInfo.State.CANCELLED -> {
                                    text = "Download"
                                    isCheckable = true
                                }
                                WorkInfo.State.RUNNING -> {
                                    val progress = infoList[0].progress.getString(DATA_KEY_PROGRESS)
                                    text = "%s%s%s".format(
                                        "下載中",
                                        if (progress != null) "\n" else "",
                                        progress ?: ""
                                    )
                                }
                                else -> {
                                    text = "Calculating"
                                }
                            }
                        }

                    setOnClickListener {
                        isClickable = false
                        DownloadWorker.enqueue(requireContext(), map.path, localMBTiles)
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
        with(MaterialAlertDialogBuilder(requireContext(), R.style.AlertDialogTheme)) {
            setAdapter(adapter) { _, _ -> }
            create()
        }.apply {
            mapModel.mbTilesList.value = requireContext().getLocalMBTiles()
            // Override dismiss dialog, do nothing instead
            listView.onItemClickListener = null
        }
}