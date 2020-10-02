package com.example.sample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.add
import androidx.fragment.app.commit
import com.example.sample.MapboxFragment
import com.example.sample.TangramFragment
import com.example.sample.R
import kotlinx.android.synthetic.main.main_fragment.*

class MainFragment : Fragment() {

    private val mapModel by activityViewModels<MapViewModel>()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {

        requireActivity().supportFragmentManager.commit {
            add<MapboxFragment>(R.id.map_container, null)
//            add<TangramFragment>(R.id.map_container, null)
        }

        mapModel.coordinate.observe(viewLifecycleOwner) {
            coordinates.text = "%.6f %.6f".format(it.first, it.second)
        }

        return inflater.inflate(R.layout.main_fragment, container, false)
    }
}
