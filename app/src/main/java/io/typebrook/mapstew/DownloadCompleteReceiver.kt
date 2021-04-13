package io.typebrook.mapstew

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber

class DownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context?, intent: Intent?) {
        Timber.d("jojojo Received!")
    }
}