package com.example.sample.ui.main

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.add
import androidx.fragment.app.commit
import androidx.lifecycle.observe
import com.example.sample.R
import com.example.sample.geometry.xy2DMSString
import com.example.sample.map.MapboxFragment
import com.example.sample.map.TangramFragment
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

        return inflater.inflate(R.layout.main_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        mapModel.coordinate.observe(viewLifecycleOwner) { xy ->
            coordinates.text = xy2DMSString(xy).run { "$first $second" }
        }

        coordinates.setOnClickListener {
            CrsDialogFragment().show(childFragmentManager, null)
        }
    }
}
