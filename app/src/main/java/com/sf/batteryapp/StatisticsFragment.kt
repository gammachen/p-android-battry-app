package com.sf.batteryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class StatisticsFragment : Fragment() {

    private lateinit var recyclerView: RecyclerView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_statistics, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        recyclerView = view.findViewById(R.id.recyclerView)
        setupRecyclerView()
    }

    private fun setupRecyclerView() {
        val cards = listOf(
            StatisticsCardAdapter.StatisticsCard(
                title = getString(R.string.battery_life),
                value = "0%",
                description = "",
                subValue = "0/4040 mAh"
            ),
            StatisticsCardAdapter.StatisticsCard(
                title = getString(R.string.battery_usage),
                value = getString(R.string.discharging),
                description = getString(R.string.record_description),
                subValue = "89%"
            ),
            StatisticsCardAdapter.StatisticsCard(
                title = getString(R.string.chart_comparison),
                value = "",
                description = getString(R.string.chart_description)
            ),
            StatisticsCardAdapter.StatisticsCard(
                title = getString(R.string.history),
                value = "",
                description = getString(R.string.history_description)
            )
        )

        val adapter = StatisticsCardAdapter(cards) {
            // 处理卡片点击事件
        }

        recyclerView.apply {
            layoutManager = GridLayoutManager(context, 2)
            this.adapter = adapter
        }
    }
}
