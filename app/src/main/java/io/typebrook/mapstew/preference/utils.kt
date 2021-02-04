package io.typebrook.mapstew.preference

import android.content.Context
import androidx.preference.PreferenceManager
import io.typebrook.mapstew.R

fun Context.prefShowHint(): Boolean = PreferenceManager.getDefaultSharedPreferences(this)
    .getBoolean(getString(R.string.pref_feature_details), false)
