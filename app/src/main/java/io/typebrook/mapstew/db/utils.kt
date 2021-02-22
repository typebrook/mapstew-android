package io.typebrook.mapstew.db

import androidx.fragment.app.Fragment

val Fragment.db get() = AppDatabase.getDatabase(requireContext())