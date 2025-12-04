package com.batteryapp

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.batteryapp.data.BatteryRepository
import com.batteryapp.model.AppBatteryUsage

class AppUsageListFragment : Fragment() {

    private lateinit var rvAppUsage: RecyclerView
    private lateinit var adapter: AppBatteryUsageAdapter
    private var appUsageList: List<AppBatteryUsage> = emptyList()
    private lateinit var rankType: BatteryRepository.BatteryUsageRankType

    companion object {
        fun newInstance(usageList: List<AppBatteryUsage>, rankType: BatteryRepository.BatteryUsageRankType): AppUsageListFragment {
            val fragment = AppUsageListFragment()
            fragment.appUsageList = usageList
            fragment.rankType = rankType
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
        // 计算总使用量，根据不同的排序类型使用不同的字段
        val totalUsage = when (rankType) {
            BatteryRepository.BatteryUsageRankType.TOTAL_USAGE -> appUsageList.sumOf { it.totalUsage }
            BatteryRepository.BatteryUsageRankType.BACKGROUND_USAGE -> appUsageList.sumOf { it.backgroundUsage }
            BatteryRepository.BatteryUsageRankType.WAKELOCK_TIME -> appUsageList.sumOf { it.wakelockTime.toDouble() }
            BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION -> appUsageList.sumOf { it.totalUsage }
        }
        adapter = AppBatteryUsageAdapter(appUsageList, totalUsage, rankType)
        rvAppUsage.layoutManager = LinearLayoutManager(requireContext())
        rvAppUsage.adapter = adapter
    }

    fun updateData(newData: List<AppBatteryUsage>, newRankType: BatteryRepository.BatteryUsageRankType) {
        appUsageList = newData
        rankType = newRankType
        // 计算总使用量，根据不同的排序类型使用不同的字段
        val totalUsage = when (rankType) {
            BatteryRepository.BatteryUsageRankType.TOTAL_USAGE -> appUsageList.sumOf { it.totalUsage }
            BatteryRepository.BatteryUsageRankType.BACKGROUND_USAGE -> appUsageList.sumOf { it.backgroundUsage }
            BatteryRepository.BatteryUsageRankType.WAKELOCK_TIME -> appUsageList.sumOf { it.wakelockTime.toDouble() }
            BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION -> appUsageList.sumOf { it.totalUsage }
        }
        adapter = AppBatteryUsageAdapter(appUsageList, totalUsage, rankType)
        rvAppUsage.adapter = adapter
    }

    fun updateData(newData: List<AppBatteryUsage>) {
        appUsageList = newData
        // 计算总使用量，根据不同的排序类型使用不同的字段
        val totalUsage = when (rankType) {
            BatteryRepository.BatteryUsageRankType.TOTAL_USAGE -> appUsageList.sumOf { it.totalUsage }
            BatteryRepository.BatteryUsageRankType.BACKGROUND_USAGE -> appUsageList.sumOf { it.backgroundUsage }
            BatteryRepository.BatteryUsageRankType.WAKELOCK_TIME -> appUsageList.sumOf { it.wakelockTime.toDouble() }
            BatteryRepository.BatteryUsageRankType.ESTIMATED_CONSUMPTION -> appUsageList.sumOf { it.totalUsage }
        }
        adapter = AppBatteryUsageAdapter(appUsageList, totalUsage, rankType)
        rvAppUsage.adapter = adapter
    }
}