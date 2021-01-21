package settings

import android.os.Bundle
import android.view.View
import androidx.preference.PreferenceFragmentCompat
import io.typebrook.mapstew.R


// TODO add real settings for options
class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.setPadding(
            view.paddingStart,
            200,
            view.paddingEnd,
            view.paddingBottom
        )
    }
}
