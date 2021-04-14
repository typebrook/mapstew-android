package io.typebrook.mapstew.map

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.activityViewModels
import androidx.work.WorkManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.OfflineMapItemBinding
import io.typebrook.mapstew.main.MapViewModel
import io.typebrook.mapstew.offline.OfflineMap
import io.typebrook.mapstew.offline.getLocalMBTiles
import io.typebrook.mapstew.offline.downloadableMaps
import io.typebrook.mapstew.offline.importMBTilesIntoMbgl
import kotlinx.android.synthetic.main.offline_map_item.view.*
import java.io.File


// TODO i18n for texts
class OfflineFragment : DialogFragment() {

    val mapModel by activityViewModels<MapViewModel>()

    private val adapter by lazy {
        object : ArrayAdapter<OfflineMap>(requireContext(), 0, downloadableMaps) {
            private val workManager by lazy { WorkManager.getInstance(requireContext()) }

            @SuppressLint("ViewHolder")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val map = getItem(position)!!
                val view = convertView ?: OfflineMapItemBinding.inflate(layoutInflater).root

                view.name.text = map.displayName

                with(view.button) {
                    setOnClickListener {
                        isClickable = false

                        val localMBTiles = File(requireContext().filesDir, map.fileName)
                        if (localMBTiles.exists())  {
                            requireContext().importMBTilesIntoMbgl(localMBTiles, map.urlTemplate)
                        }
                        else {
                            download(map)
                        }
                    }

                    mapModel.mbTilesList.observe(this@OfflineFragment) { list ->
                        if (map.fileName in list) {
                            text = getString(R.string.in_use)
                            isClickable = false
                            setBackgroundColor(resources.getColor(android.R.color.darker_gray))
                        } else {
                            text = "Download"
                            isCheckable = true
                        }
                    }
                }

                return view
            }

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

    private fun download(map: OfflineMap): Long {
        val request = DownloadManager.Request(Uri.parse(map.path)).apply {
            val fileName = map.path.substringAfterLast('/')
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            setTitle(fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        val manager = requireActivity().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}