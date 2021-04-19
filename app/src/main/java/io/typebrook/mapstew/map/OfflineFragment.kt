package io.typebrook.mapstew.map

import android.annotation.SuppressLint
import android.app.Dialog
import android.app.DownloadManager
import android.content.Context
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.fragment.app.DialogFragment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.typebrook.mapstew.R
import io.typebrook.mapstew.databinding.OfflineMapItemBinding
import io.typebrook.mapstew.offline.DownloadProgressLiveData
import io.typebrook.mapstew.offline.OfflineMap
import io.typebrook.mapstew.offline.downloadableMaps
import kotlinx.android.synthetic.main.offline_map_item.view.*


// TODO i18n for texts
class OfflineFragment : DialogFragment() {

    private val downloadProgress: DownloadProgressLiveData by lazy {
        DownloadProgressLiveData(requireActivity())
    }

    enum class Status { DOWNLOADABLE, DOWNLOADING, DOWNLOADED }

    val items = downloadableMaps.map { it to Status.DOWNLOADABLE }

    private val adapter by lazy {
        object : ArrayAdapter<Pair<OfflineMap, Status>>(requireContext(), 0, items) {

            @SuppressLint("ViewHolder")
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val item = getItem(position)!!
                val view = convertView ?: OfflineMapItemBinding.inflate(layoutInflater).root

                view.name.text = item.first.displayName

                with(view.button) {

                    setOnClickListener {
                        isClickable = false
                        download(item.first)
                    }

                    val localMBTiles = item.first.localFile(requireContext())
                    val status = if (localMBTiles.exists())
                        Status.DOWNLOADED else
                        item.second

                    when (status) {
                        Status.DOWNLOADABLE -> {
                            text = "Download"
                            isClickable = true
                            setBackgroundColor(
                                MaterialColors.getColor(context, R.attr.colorAccent, Color.GRAY)
                            )
                        }
                        Status.DOWNLOADING -> {
                            text = "下載中"
                            isClickable = false
                            setBackgroundColor(
                                MaterialColors.getColor(context, R.attr.colorPrimary, Color.GRAY)
                            )
                        }
                        Status.DOWNLOADED -> {
                            text = getString(R.string.in_use)
                            isClickable = false
                            setBackgroundColor(resources.getColor(android.R.color.darker_gray))
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
            // Override dismiss dialog, do nothing instead
            listView.onItemClickListener = null

            downloadProgress.observe(this@OfflineFragment) { progresses ->
                val newItems: List<Pair<OfflineMap, Status>> = items.map { item ->
                    item.first to when {
                        item.first.path in progresses.map { it.uri } -> Status.DOWNLOADING
                        item.first.localFile(requireContext()).exists() -> Status.DOWNLOADED
                        else -> Status.DOWNLOADABLE
                    }
                }
                if (newItems == items) return@observe
                with(adapter) {
                    clear()
                    addAll(newItems)
                    notifyDataSetChanged()
                }
            }
        }

    private fun download(map: OfflineMap): Long {
        val request = DownloadManager.Request(Uri.parse(map.path)).apply {
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, map.fileName)
            setTitle(map.fileName)
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
        }
        val manager =
            requireActivity().getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        return manager.enqueue(request)
    }
}