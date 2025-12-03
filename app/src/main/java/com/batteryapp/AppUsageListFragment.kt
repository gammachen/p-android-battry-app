package com.batteryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.batteryapp.model.AppBatteryUsage

class AppUsageListFragment : Fragment() {

    private lateinit var rvAppUsage: RecyclerView
    private lateinit var adapter: AppBatteryUsageAdapter
    private var appUsageList: List<AppBatteryUsage> = emptyList()

    companion object {
        fun newInstance(usageList: List<AppBatteryUsage>): AppUsageListFragment {
            val fragment = AppUsageListFragment()
            fragment.appUsageList = usageList
            return fragment
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_app_usage_list, container, false)
        rvAppUsage = view.findViewById(R.id.rv_app_usage)
        setupRecyclerView()
        return view
    }

    private fun setupRecyclerView() {
        // 计算总使用时间
        val totalUsageTime = appUsageList.sumOf { it.totalUsage }
        adapter = AppBatteryUsageAdapter(appUsageList, totalUsageTime)
        rvAppUsage.layoutManager = LinearLayoutManager(requireContext())
        rvAppUsage.adapter = adapter
    }

    fun updateData(newData: List<AppBatteryUsage>) {
        appUsageList = newData
        // 计算总使用时间
        val totalUsageTime = appUsageList.sumOf { it.totalUsage }
        adapter = AppBatteryUsageAdapter(appUsageList, totalUsageTime)
        rvAppUsage.adapter = adapter
    }
}