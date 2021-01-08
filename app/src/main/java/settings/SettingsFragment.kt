package settings

import android.os.Bundle
import androidx.preference.PreferenceFragmentCompat
import io.typebrook.mapstew.R

// TODO add real settings for options
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }
}
