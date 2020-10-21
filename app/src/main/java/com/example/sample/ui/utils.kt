package com.example.sample.ui

import android.text.InputFilter
import android.text.Spanned

class AngleFilter(
    private val max: Int,
    private val closed: Boolean = false,
    private val charNum: Int? = null
) : InputFilter {
    override fun filter(
        source: CharSequence?, start: Int, end: Int,
        dest: Spanned?, dstart: Int, dend: Int
    ): CharSequence? {
        source ?: return null
        dest ?: return null
        return try {
            // may throws NumberFormatException
            val result = dest.replaceRange(dstart, dend, source).toString()

            if (charNum != null && result.length > charNum) return String()
            when (closed) {
                true -> if (result.toFloat() > max) String() else null
                false -> if (result.toFloat() >= max) String() else null
            }
        } catch (e: NumberFormatException) {
            String()
        }
    }
}