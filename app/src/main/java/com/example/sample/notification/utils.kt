package com.example.sample.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi

@RequiresApi(Build.VERSION_CODES.O)
fun Context.createChannel(name: String, descriptionText: String) {

    val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    // Channel for report
    NotificationChannel(name, name, NotificationManager.IMPORTANCE_DEFAULT).apply {
        description = descriptionText
        enableLights(true)
        enableVibration(true)
    }.let(notificationManager::createNotificationChannel)
}